package it.gov.pagopa.gpd.upload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import it.gov.pagopa.gpd.upload.client.GPDClient;
import it.gov.pagopa.gpd.upload.entity.PaymentPositionsMessage;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.exception.AppException;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Following function handles request to GPD and update STATUS and REPORT
 */
public class ServiceFunction {

    private final Integer MAX_RETRY = Integer.valueOf(System.getenv("MAX_RETRY"));
    private final Integer RETRY_DELAY = Integer.valueOf(System.getenv("RETRY_DELAY_IN_SECONDS"));


    @FunctionName("PaymentPositionDequeueFunction")
    public void run(
            @QueueTrigger(name = "ValidPositionsTrigger", queueName = "%VALID_POSITIONS_QUEUE%", connection = "GPD_SA_CONNECTION_STRING") String message,
            final ExecutionContext context) {
        Logger logger = context.getLogger();
        String invocationId = context.getInvocationId();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        try {
            PaymentPositionsMessage msg = objectMapper.readValue(message, PaymentPositionsMessage.class);
            create(invocationId, logger, msg.uploadKey, msg.organizationFiscalCode, msg.brokerCode, msg.getPaymentPositions(), msg.retryCounter);
            // check if upload is completed
            Status status = StatusService.getInstance(logger).getStatus(invocationId, msg.organizationFiscalCode, msg.uploadKey);
            if(status.upload.getCurrent() == status.upload.getTotal()) {
                StatusService.getInstance(logger).updateStatusEndTime(invocationId, status.fiscalCode, status.id, LocalDateTime.now());
                report(invocationId, logger, msg.uploadKey, msg.brokerCode, msg.organizationFiscalCode);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, () -> String.format("[id=%s][ServiceFunction] Processing function exception: %s, caused by: %s", invocationId, e.getMessage(), e.getCause()));
        }
    }

    private void create(String invocationId, Logger logger, String uploadKey, String fiscalCode, String broker, PaymentPositions paymentPositions, Integer retryCounter) throws AppException {
        // constraint: paymentPositions size less than max bulk item per call -> respected by design (max queue message = 64KB)
        GPDClient gpdClient = GPDClient.getInstance();
        ResponseGPD response = gpdClient.createBulkDebtPositions(fiscalCode, paymentPositions, logger, invocationId);
        StatusService statusService = StatusService.getInstance(logger);

        logger.log(Level.INFO, () -> String.format("[id=%s][ServiceFunction] Create %s payment positions calling GPD-Core", invocationId, paymentPositions.getPaymentPositions().size()));

        if(response.getStatus() != HttpStatus.CREATED.value()) {
            logger.log(Level.INFO, () -> String.format("[id=%s][ServiceFunction] Call GPD-Core one-by-one", invocationId));

            Map<String, ResponseGPD> responseByIUPD = new HashMap<>();
            // if BULK creation wasn't successful, switch to single debt position creation
            for(PaymentPosition paymentPosition : paymentPositions.getPaymentPositions()) {
                response = gpdClient.createDebtPosition(invocationId, logger, fiscalCode, paymentPosition);
                responseByIUPD.put(paymentPosition.getIupd(), response);
            }

            // Selecting responses where retry == true
            Map<String, ResponseGPD> retryResponses = responseByIUPD.entrySet().stream()
                                                           .filter(entry -> entry.getValue().getRetryStep().equals(RetryStep.RETRY))
                                                           .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            if (!retryResponses.isEmpty() && retryCounter < MAX_RETRY) {
                // Remove retryResponses from responseMap and enqueue retry responses
                responseByIUPD.entrySet().removeAll(retryResponses.entrySet());
                List<PaymentPosition> retryPP = paymentPositions.getPaymentPositions().stream()
                                                        .filter(paymentPosition -> retryResponses.containsKey(paymentPosition.getIupd()))
                                                        .collect(Collectors.toList());
                String message = QueueService.createMessage(uploadKey, fiscalCode, broker, ++retryCounter, retryPP);
                QueueService.enqueue(invocationId, logger, message, RETRY_DELAY);
            }

            logger.log(Level.INFO, () -> String.format("[id=%s][ServiceFunction] Call update status of following iUPDs number: %s", invocationId, responseByIUPD.keySet().size()));
            statusService.appendResponses(invocationId, fiscalCode, uploadKey, responseByIUPD);
        } else {
            // if BULK creation was successful
            List<String> IUPDs = paymentPositions.getPaymentPositions().stream().map(pp -> pp.getIupd()).collect(Collectors.toList());
            statusService.appendResponse(invocationId, fiscalCode, uploadKey, IUPDs, response);
        }
    }

    private void report(String invocationId, Logger logger, String uploadKey, String broker, String fiscalCode) throws AppException, JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.registerModule(new JavaTimeModule());
        Status status = StatusService.getInstance(logger).getStatus(invocationId, fiscalCode, uploadKey);
        BlobRepository.getInstance(logger).uploadReport(objectMapper.writeValueAsString(MapUtils.convert(status)), broker, fiscalCode, uploadKey + ".json");
    }
}
