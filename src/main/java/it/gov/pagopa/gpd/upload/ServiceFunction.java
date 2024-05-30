package it.gov.pagopa.gpd.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import it.gov.pagopa.gpd.upload.model.RequestGPD;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.repository.BlobRepository;
import it.gov.pagopa.gpd.upload.repository.StatusRepository;
import it.gov.pagopa.gpd.upload.service.CRUDService;

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

        try {
            QueueMessage msg = objectMapper.readValue(message, QueueMessage.class);
            Function<RequestGPD, ResponseGPD> method = getMethod(msg, getGPDClient(ctx));
            getOperationService(ctx, method, getPositionMessage(msg)).processRequestInBulk();

            String key = msg.getUploadKey();
            String orgFiscalCode = msg.getOrganizationFiscalCode();
            StatusRepository statusRepo = StatusRepository.getInstance(logger);

            Status status = statusRepo.getStatus(invocationId, key, orgFiscalCode);
            if(status != null && status.upload.getCurrent() == status.upload.getTotal()) {
                statusRepo.partialUpdate(key, orgFiscalCode, LocalDateTime.now());
                BlobRepository.getInstance(logger).report(status);
            }

            Runtime.getRuntime().gc();
        } catch (Exception e) {
            logger.log(Level.SEVERE, () -> String.format("[id=%s][ServiceFunction] Processing function exception: %s, caused by: %s", invocationId, e.getMessage(), e.getCause()));
        }
    }

    private Function<RequestGPD, ResponseGPD> getMethod(QueueMessage msg, GPDClient gpdClient) {
        return switch (msg.getCrudOperation()) {
            case CREATE -> gpdClient::createDebtPosition;
            case UPDATE -> gpdClient::updateDebtPosition;
            case DELETE -> gpdClient::deleteDebtPosition;
        };
    }

    private CRUDService getOperationService(ExecutionContext ctx, Function<RequestGPD, ResponseGPD> method, DebtPositionMessage debtPositionMessage) {
        return new CRUDService(ctx, method, debtPositionMessage);
    }

    private DebtPositionMessage getPositionMessage(QueueMessage queueMessage) {
        return switch (queueMessage.getCrudOperation()) {
            case CREATE, UPDATE -> new UpsertMessage(queueMessage);
            case DELETE -> new DeleteMessage(queueMessage);
        };
    }

    public GPDClient getGPDClient(ExecutionContext context) {
        return GPDClient.getInstance(context.getLogger());
    }
}
