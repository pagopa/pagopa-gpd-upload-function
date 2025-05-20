package it.gov.pagopa.gpd.upload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import it.gov.pagopa.gpd.upload.client.GPDClient;
import it.gov.pagopa.gpd.upload.entity.DeleteMessage;
import it.gov.pagopa.gpd.upload.entity.DebtPositionMessage;
import it.gov.pagopa.gpd.upload.entity.UpsertMessage;
import it.gov.pagopa.gpd.upload.model.QueueMessage;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.RequestGPD;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.repository.BlobRepository;
import it.gov.pagopa.gpd.upload.service.CRUDService;
import it.gov.pagopa.gpd.upload.service.StatusService;
import it.gov.pagopa.gpd.upload.util.IdempotencyUploadTracker;
import it.gov.pagopa.gpd.upload.util.MapUtils;

import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Following function handles request to GPD and update STATUS and REPORT
 */
public class ServiceFunction {

    @FunctionName("PaymentPositionDequeueFunction")
    public void run(
            @QueueTrigger(name = "ValidPositionsTrigger", queueName = "%VALID_POSITIONS_QUEUE%", connection = "GPD_SA_CONNECTION_STRING") String message,
            final ExecutionContext ctx) {
        Logger logger = ctx.getLogger();
        String invocationId = ctx.getInvocationId();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        String subjectFormat = "/containers/%s/blobs/%s/%s.json";       
        String subject = String.format(subjectFormat,"NA","NA","NA");
        try {
            QueueMessage msg = objectMapper.readValue(message, QueueMessage.class);
            // extract from message
            String key = msg.getUploadKey();
            String orgFiscalCode = msg.getOrganizationFiscalCode();
            // process message request
            Function<RequestGPD, ResponseGPD> method = getMethod(msg, getGPDClient(ctx));
            getOperationService(ctx, method, getPositionMessage(msg)).processRequestInBulk();
            // check if upload is completed
            Status status = getStatusService(ctx).getStatus(invocationId, orgFiscalCode, key);
            if(status.upload.getCurrent() == status.upload.getTotal()) {
            	subject = String.format(subjectFormat,
            			msg.getBrokerCode(),msg.getOrganizationFiscalCode(),msg.getUploadKey());
            	// Unlock idempotency key
            	IdempotencyUploadTracker.unlock(subject);
                LocalDateTime endTime = LocalDateTime.now();
                status.upload.setEnd(endTime);
                getStatusService(ctx).updateStatusEndTime(orgFiscalCode, key, endTime);
                generateReport(logger, key, status);
            }
            Runtime.getRuntime().gc();
        } catch (Exception e) {
            logger.log(Level.SEVERE, () -> String.format("[id=%s][ServiceFunction] Processing function exception: %s, message: %s, caused by: %s, localized-message: %s",
                    invocationId, e.getClass(), e.getMessage(), e.getCause(), e.getLocalizedMessage()));
            // Unlock idempotency key
            IdempotencyUploadTracker.unlock(subject);
        }
    }

    public boolean generateReport(Logger logger, String uploadKey, Status status) throws AppException, JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.registerModule(new JavaTimeModule());
        return BlobRepository.getInstance(logger).uploadReport(objectMapper.writeValueAsString(MapUtils.convert(status)), status.getBrokerID(), status.getFiscalCode(), uploadKey + ".json");
    }

    public Function<RequestGPD, ResponseGPD> getMethod(QueueMessage msg, GPDClient gpdClient) {
        return switch (msg.getCrudOperation()) {
            case CREATE -> gpdClient::createDebtPosition;
            case UPDATE -> gpdClient::updateDebtPosition;
            case DELETE -> gpdClient::deleteDebtPosition;
        };
    }

    public CRUDService getOperationService(ExecutionContext ctx, Function<RequestGPD, ResponseGPD> method, DebtPositionMessage debtPositionMessage) {
        return new CRUDService(ctx, method, debtPositionMessage, getStatusService(ctx));
    }

    public DebtPositionMessage getPositionMessage(QueueMessage queueMessage) {
        return switch (queueMessage.getCrudOperation()) {
            case CREATE, UPDATE -> new UpsertMessage(queueMessage);
            case DELETE -> new DeleteMessage(queueMessage);
        };
    }

    public StatusService getStatusService(ExecutionContext ctx) {
        return StatusService.getInstance(ctx.getLogger());
    }

    public GPDClient getGPDClient(ExecutionContext context) {
        return GPDClient.getInstance(context.getLogger());
    }
}
