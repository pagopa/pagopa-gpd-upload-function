package it.gov.pagopa.gpd.upload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
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
import it.gov.pagopa.gpd.upload.model.ModelGPD;
import it.gov.pagopa.gpd.upload.model.RequestGPD;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositionIUPDs;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;
import it.gov.pagopa.gpd.upload.repository.BlobRepository;
import it.gov.pagopa.gpd.upload.service.OperationService;
import it.gov.pagopa.gpd.upload.service.StatusService;
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
            final ExecutionContext context) {
        Logger logger = context.getLogger();
        String invocationId = context.getInvocationId();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        try {
            // JavaType javaType = objectMapper.getTypeFactory().constructParametricType(UploadMessage.class, PaymentPositionIUPDs.class);
            UploadMessage msg = objectMapper.readValue(message, UploadMessage.class);
            GPDClient gpdClient = getGPDClient();
            var method = getMethod(msg, gpdClient);
            var operationService = getService(msg);

            operationService.processOperation(context, msg, method);
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

    private void report(ExecutionContext ctx, Logger logger, String uploadKey, String broker, String fiscalCode) throws AppException, JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.registerModule(new JavaTimeModule());
        Status status = getStatusService(ctx).getStatus(ctx.getInvocationId(), fiscalCode, uploadKey);
        BlobRepository.getInstance(logger).uploadReport(objectMapper.writeValueAsString(MapUtils.convert(status)), broker, fiscalCode, uploadKey + ".json");
    }

    private Function<RequestGPD<ModelGPD>, ResponseGPD> getMethod(UploadMessage<ModelGPD> msg, GPDClient gpdClient) {
        return switch (msg.getUploadOperation()) {
            case CREATE -> gpdClient::createDebtPosition;
            case UPDATE -> gpdClient::updateDebtPosition;
            case DELETE -> gpdClient::deleteDebtPosition;
        };
    }

    private OperationService getService(UploadMessage<ModelGPD> msg) {
        return switch (msg.getUploadOperation()) {
            case CREATE, UPDATE -> new OperationService<PaymentPositions>();
            case DELETE -> new OperationService<PaymentPositionIUPDs>();
        };
    }

    public StatusService getStatusService(ExecutionContext ctx) {
        return StatusService.getInstance(ctx.getLogger());
    }

    public GPDClient getGPDClient() {
        return GPDClient.getInstance();
    }
}
