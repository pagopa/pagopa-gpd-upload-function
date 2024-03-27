package it.gov.pagopa.gpd.upload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.gpd.upload.entity.UploadMessage;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.ModelGPD;
import it.gov.pagopa.gpd.upload.model.RequestGPD;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.model.RetryStep;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class OperationService<T extends ModelGPD<T>> {
    private static final Integer MAX_RETRY =
            System.getenv("MAX_RETRY") != null ? Integer.parseInt(System.getenv("MAX_RETRY")) : 1;
    private static final Integer RETRY_DELAY =
            System.getenv("RETRY_DELAY_IN_SECONDS") != null ? Integer.parseInt(System.getenv("RETRY_DELAY_IN_SECONDS")) : 300;

    private ResponseGPD applyRequest(ExecutionContext ctx, RequestGPD<T> requestGPD, Function<RequestGPD<T>, ResponseGPD> method) {
        ResponseGPD response = method.apply(requestGPD);
        ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][ServiceFunction] Call GPD-Client", ctx.getInvocationId()));
        return  response;
    }

    private RequestGPD<T> generateRequest(RequestGPD.Mode mode, Logger logger, String invocation, String orgFiscalCode, T pps) {
        return RequestGPD.<T>builder()
                           .mode(mode)
                           .orgFiscalCode(orgFiscalCode)
                           .body(pps)
                           .logger(logger)
                           .invocationId(invocation)
                           .build();
    }

    public void processOperation(ExecutionContext ctx, UploadMessage<T> msg, Function<RequestGPD<T>, ResponseGPD> method) throws AppException {
        // constraint: paymentPositions size less than max bulk item per call -> compliant by design(max queue message = 64KB = ~30 PaymentPosition)
        StatusService statusService = StatusService.getInstance(ctx.getLogger());

        RequestGPD<T> requestGPD = generateRequest(RequestGPD.Mode.BULK, ctx.getLogger(), ctx.getInvocationId(), msg.getOrganizationFiscalCode(), msg.getPaymentPositions());
        ResponseGPD response = applyRequest(ctx, requestGPD, method);

        if(!response.is2xxSuccessful()) {
            // if BULK creation wasn't successful, switch to single debt position creation
            Map<String, ResponseGPD> responseByIUPD = processOperationOneByOne(ctx, msg, method);
            ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][ServiceFunction] Call Status update for %s IUPDs", ctx.getInvocationId(), responseByIUPD.keySet().size()));
            statusService.appendResponses(ctx.getInvocationId(), msg.getOrganizationFiscalCode(), msg.getUploadKey(), responseByIUPD);
        } else {
            // if BULK creation was successful
            List<String> IUPDs = msg.getPaymentPositions().getIUPD();
            statusService.appendResponse(ctx.getInvocationId(), msg.getOrganizationFiscalCode(), msg.getUploadKey(), IUPDs, response);
        }
    }

    private Map<String, ResponseGPD> processOperationOneByOne(ExecutionContext ctx, UploadMessage<T> msg, Function<RequestGPD<T>, ResponseGPD> method) {
        ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][ServiceFunction] Single mode processing", ctx.getInvocationId()));
        Map<String, ResponseGPD> responseByIUPD = new HashMap<>();
        List<String> iupdList = msg.getPaymentPositions().getIUPD();

        for(String iupd: iupdList) {
            RequestGPD<T> requestGPD = generateRequest(RequestGPD.Mode.SINGLE, ctx.getLogger(), ctx.getInvocationId(), msg.getOrganizationFiscalCode(), msg.getPaymentPositions());
            ResponseGPD response = applyRequest(ctx, requestGPD, method);
            responseByIUPD.put(iupd, response);
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

    public void retry(ExecutionContext ctx, UploadMessage<T> msg, Map<String, ResponseGPD> retryResponses) {
        T retryPositions = msg.getPaymentPositions().filterById(retryResponses.keySet().stream().toList());

        try {
            String message = generateMessage(msg.getUploadKey(), msg.getOrganizationFiscalCode(), msg.getBrokerCode(), msg.getRetryCounter()+1, retryPositions);
            QueueService.enqueue(ctx.getInvocationId(), ctx.getLogger(), message, RETRY_DELAY);
        } catch (AppException e) {
            ctx.getLogger().log(Level.SEVERE, () -> String.format("[id=%s][ServiceFunction] Processing function exception: %s, caused by: %s", ctx.getInvocationId(), e.getMessage(), e.getCause()));
        }
    }

    private String generateMessage(String uploadKey, String fiscalCode, String broker, int retryCounter, T paymentPositions) throws AppException {
        UploadMessage<T> message = UploadMessage.<T>builder()
                                   .uploadKey(uploadKey)
                                   .organizationFiscalCode(fiscalCode)
                                   .brokerCode(broker)
                                   .retryCounter(retryCounter)
                                   .paymentPositions(paymentPositions)
                                   .build();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.INDENT_OUTPUT); // remove useless whitespaces from message
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new AppException(e.getMessage());
        }
    }
}
