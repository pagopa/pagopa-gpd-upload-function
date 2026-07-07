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
import it.gov.pagopa.gpd.upload.model.RequestGPD;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.repository.BlobRepository;
import it.gov.pagopa.gpd.upload.service.CRUDService;
import it.gov.pagopa.gpd.upload.service.StatusService;
import it.gov.pagopa.gpd.upload.util.IdempotencyUploadTracker;
import it.gov.pagopa.gpd.upload.util.MapUtils;

import java.time.LocalDateTime;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Following function handles request to GPD and update STATUS and REPORT
 */
public class ServiceFunction {
	private static final Logger log = LoggerFactory.getLogger(ServiceFunction.class);

	private static final String LOG_PREFIX = "[id={}][upload={}][ServiceFunction]";
	private static final String SUBJECT_FORMAT = "/containers/%s/blobs/%s/%s";

	@FunctionName("PaymentPositionDequeueFunction")
	public void run(
	        @QueueTrigger(name = "ValidPositionsTrigger", queueName = "%VALID_POSITIONS_QUEUE%", connection = "GPD_SA_CONNECTION_STRING") String message,
	        final ExecutionContext ctx) {
	    String invocationId = ctx.getInvocationId();
	    ObjectMapper objectMapper = new ObjectMapper();
	    objectMapper.registerModule(new JavaTimeModule());
	    String subject = String.format(SUBJECT_FORMAT, "NA", "NA", "NA");
	    String uploadKey = "-";

	    try {
	        QueueMessage msg = objectMapper.readValue(message, QueueMessage.class);
	        // extract from message
	        uploadKey = msg.getUploadKey();
	        String orgFiscalCode = msg.getOrganizationFiscalCode();
	        // process message request
	        log.info(LOG_PREFIX + " Processing queue message for organization {} and operation {}",
	                invocationId, uploadKey, orgFiscalCode, msg.getCrudOperation());
	        Function<RequestGPD, ResponseGPD> method = getMethod(msg, getGPDClient());
	        getOperationService(ctx, method, getPositionMessage(msg)).processRequestInBulk();
	        // check if upload is completed
	        Status status = getStatusService(ctx).getStatus(invocationId, orgFiscalCode, uploadKey);
	        if (status.upload.getCurrent() == status.upload.getTotal()) {
	            subject = String.format(
	                    SUBJECT_FORMAT,
	                    msg.getBrokerCode(),
	                    msg.getOrganizationFiscalCode(),
	                    msg.getUploadKey()
	            );
	            // Unlock idempotency key
	            IdempotencyUploadTracker.unlock(subject);
	            LocalDateTime endTime = LocalDateTime.now();
	            status.upload.setEnd(endTime);
	            getStatusService(ctx).updateStatusEndTime(orgFiscalCode, uploadKey, endTime);
	            boolean reportGenerated = generateReport(uploadKey, status);
	            if (reportGenerated) {
	                log.info(LOG_PREFIX + " Upload completed and report generated. Subject unlocked: {}",
	                        invocationId, uploadKey, subject);
	            } else {
	                log.warn(LOG_PREFIX + " Upload completed but report generation failed. Subject unlocked: {}",
	                        invocationId, uploadKey, subject);
	            }
	        }
	        Runtime.getRuntime().gc();
	    } catch (Exception e) {
	        log.error(LOG_PREFIX + " Processing function exception. Subject will be unlocked: {}",
	                invocationId, uploadKey, subject, e);
	        // Unlock idempotency key
	        IdempotencyUploadTracker.unlock(subject);
	    }
	}

	public boolean generateReport(String uploadKey, Status status) throws JsonProcessingException {
	    ObjectMapper objectMapper = new ObjectMapper();
	    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
	    objectMapper.registerModule(new JavaTimeModule());

	    return getBlobRepository().uploadReport(
	            objectMapper.writeValueAsString(MapUtils.convert(status)),
	            status.getBrokerID(),
	            status.getFiscalCode(),
	            uploadKey + ".json",
	            status.getServiceType()
	    );
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
        return StatusService.getInstance();
    }

    public GPDClient getGPDClient() {
    	return new GPDClient();
    }
    
    public BlobRepository getBlobRepository() {
        return new BlobRepository();
    }
}
