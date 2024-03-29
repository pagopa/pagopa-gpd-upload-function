package it.gov.pagopa.gpd.upload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import it.gov.pagopa.gpd.upload.client.GPDClient;
import it.gov.pagopa.gpd.upload.entity.UploadMessage;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.RequestGPD;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.model.RetryStep;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;
import it.gov.pagopa.gpd.upload.repository.BlobRepository;
import it.gov.pagopa.gpd.upload.service.QueueService;
import it.gov.pagopa.gpd.upload.service.StatusService;
import it.gov.pagopa.gpd.upload.util.MapUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Following function handles request to GPD and update STATUS and REPORT
 */
public class ServiceFunction {
    private static final Integer MAX_RETRY =
            System.getenv("MAX_RETRY") != null ? Integer.parseInt(System.getenv("MAX_RETRY")) : 1;
    private static final Integer RETRY_DELAY =
            System.getenv("RETRY_DELAY_IN_SECONDS") != null ? Integer.parseInt(System.getenv("RETRY_DELAY_IN_SECONDS")) : 300;

    @FunctionName("PaymentPositionDequeueFunction")
    public void run(
            @QueueTrigger(name = "ValidPositionsTrigger", queueName = "%VALID_POSITIONS_QUEUE%", connection = "GPD_SA_CONNECTION_STRING") String message,
            final ExecutionContext context) {
        Logger logger = context.getLogger();
        String invocationId = context.getInvocationId();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        try {
            UploadMessage msg = objectMapper.readValue(message, UploadMessage.class);
            GPDClient gpdClient = getGPDClient();
            Function<RequestGPD, ResponseGPD> method = gpdClient::createDebtPosition;

            switch(msg.getUploadOperation()) {
                case CREATE:
                    method = gpdClient::createDebtPosition;
                    break;
                case UPDATE:
                    method = gpdClient::updateDebtPosition;
            }

            this.processOperation(context, msg, method);

            // check if upload is completed
            Status status = getStatusService(context).getStatus(invocationId, msg.getOrganizationFiscalCode(), msg.getUploadKey());
            if(status.upload.getCurrent() == status.upload.getTotal()) {
                getStatusService(context).updateStatusEndTime(invocationId, status.fiscalCode, status.id, LocalDateTime.now());
                report(context, logger, msg.getUploadKey(), msg.getBrokerCode(), msg.getOrganizationFiscalCode());
            }

            Runtime.getRuntime().gc();
        } catch (Exception e) {
            logger.log(Level.SEVERE, () -> String.format("[id=%s][ServiceFunction] Processing function exception: %s, caused by: %s", invocationId, e.getMessage(), e.getCause()));
        }
    }

    private void processOperation(ExecutionContext ctx, UploadMessage msg, Function<RequestGPD, ResponseGPD> method) throws AppException {
        // constraint: paymentPositions size less than max bulk item per call -> compliant by design(max queue message = 64KB = ~30 PaymentPosition)
        StatusService statusService = getStatusService(ctx);
        RequestGPD<PaymentPositions> bulkRequestGPD = RequestGPD.<PaymentPositions>builder()
                                        .mode(RequestGPD.Mode.BULK)
                                        .orgFiscalCode(msg.getOrganizationFiscalCode())
                                        .body(msg.getPaymentPositions())
                                        .logger(ctx.getLogger())
                                        .invocationId(ctx.getInvocationId())
                                        .build();

        ResponseGPD response = method.apply(bulkRequestGPD);
        ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][ServiceFunction] Create %s payment positions calling GPD-Core", ctx.getInvocationId(), msg.getPaymentPositions().getPaymentPositions().size()));

        if(!response.is2xxSuccessful()) {
            // if BULK creation wasn't successful, switch to single debt position creation
            Map<String, ResponseGPD> responseByIUPD = processOperationOneByOne(ctx, msg, method);
            ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][ServiceFunction] Call Status update for %s IUPDs", ctx.getInvocationId(), responseByIUPD.keySet().size()));
            statusService.appendResponses(ctx.getInvocationId(), msg.getOrganizationFiscalCode(), msg.getUploadKey(), responseByIUPD);
        } else {
            // if BULK creation was successful
            List<String> IUPDs = msg.getPaymentPositions().getPaymentPositions().stream().map(PaymentPosition::getIupd).collect(Collectors.toList());
            statusService.appendResponse(ctx.getInvocationId(), msg.getOrganizationFiscalCode(), msg.getUploadKey(), IUPDs, response);
        }
    }

    private Map<String, ResponseGPD> processOperationOneByOne(ExecutionContext ctx, UploadMessage msg, Function<RequestGPD, ResponseGPD> method) {
        ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][ServiceFunction] Call GPD-Core one-by-one", ctx.getInvocationId()));
        Map<String, ResponseGPD> responseByIUPD = new HashMap<>();
        RequestGPD<PaymentPosition> requestGPD;
        ResponseGPD response;
        for(PaymentPosition paymentPosition: msg.getPaymentPositions().getPaymentPositions()) {
            requestGPD = RequestGPD.<PaymentPosition>builder()
                                 .mode(RequestGPD.Mode.SINGLE)
                                 .orgFiscalCode(msg.getOrganizationFiscalCode())
                                 .body(paymentPosition)
                                 .logger(ctx.getLogger())
                                 .invocationId(ctx.getInvocationId())
                                 .build();
            response = method.apply(requestGPD);
            responseByIUPD.put(paymentPosition.getIupd(), response);
        }

        // Selecting responses where retry == true
        Map<String, ResponseGPD> retryResponses = responseByIUPD.entrySet().stream()
                                                          .filter(entry -> entry.getValue().getRetryStep().equals(RetryStep.RETRY))
                                                          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (!retryResponses.isEmpty() && msg.getRetryCounter() < MAX_RETRY) {
            // Remove retry-responses from response-map and enqueue retry-responses
            responseByIUPD.entrySet().removeAll(retryResponses.entrySet());
            this.retry(ctx, msg, retryResponses);
        }

        return responseByIUPD;
    }

    private void report(ExecutionContext ctx, Logger logger, String uploadKey, String broker, String fiscalCode) throws AppException, JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.registerModule(new JavaTimeModule());
        Status status = getStatusService(ctx).getStatus(ctx.getInvocationId(), fiscalCode, uploadKey);
        BlobRepository.getInstance(logger).uploadReport(objectMapper.writeValueAsString(MapUtils.convert(status)), broker, fiscalCode, uploadKey + ".json");
    }

    public StatusService getStatusService(ExecutionContext ctx) {
        return StatusService.getInstance(ctx.getLogger());
    }

    public GPDClient getGPDClient() {
        return GPDClient.getInstance();
    }


    public void retry(ExecutionContext ctx, UploadMessage msg, Map<String, ResponseGPD> retryResponses) {
        List<PaymentPosition> retryPositions = msg.getPaymentPositions().getPaymentPositions().stream()
                                                .filter(paymentPosition -> retryResponses.containsKey(paymentPosition.getIupd()))
                                                .collect(Collectors.toList());
        try {
            String message = QueueService.createMessage(msg.getUploadKey(), msg.getOrganizationFiscalCode(), msg.getBrokerCode(), msg.getRetryCounter()+1, retryPositions);
            QueueService.enqueue(ctx.getInvocationId(), ctx.getLogger(), message, RETRY_DELAY);
        } catch (AppException e) {
            ctx.getLogger().log(Level.SEVERE, () -> String.format("[id=%s][ServiceFunction] Processing function exception: %s, caused by: %s", ctx.getInvocationId(), e.getMessage(), e.getCause()));
        }
    }
}
