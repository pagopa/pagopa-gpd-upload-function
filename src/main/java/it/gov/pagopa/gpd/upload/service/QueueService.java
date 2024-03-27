package it.gov.pagopa.gpd.upload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.queue.CloudQueue;
import com.microsoft.azure.storage.queue.CloudQueueMessage;
import it.gov.pagopa.gpd.upload.entity.UploadMessage;
import it.gov.pagopa.gpd.upload.model.UploadOperation;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QueueService {

    private static final String GPD_SA_CONNECTION_STRING = System.getenv("GPD_SA_CONNECTION_STRING");
    private static final String VALID_POSITIONS_QUEUE = System.getenv("VALID_POSITIONS_QUEUE");
    private static final Integer CHUNK_SIZE = System.getenv("CHUNK_SIZE") != null ? Integer.parseInt(System.getenv("CHUNK_SIZE")) : 30;

    public static boolean enqueue(String invocationId, Logger logger, String message, int initialVisibilityDelayInSeconds) {
        try {
            CloudQueue queue = CloudStorageAccount.parse(GPD_SA_CONNECTION_STRING).createCloudQueueClient()
                                       .getQueueReference(VALID_POSITIONS_QUEUE);
            CloudQueueMessage cloudQueueMessage = new CloudQueueMessage(message);

            logger.log(Level.INFO, () -> String.format("[id=%s][QueueService] Add message of length %s to queue %s", invocationId, message.length(), VALID_POSITIONS_QUEUE));

            // timeToLiveInSeconds = 0 is default -> 7 days
            queue.addMessage(cloudQueueMessage, 0, initialVisibilityDelayInSeconds, null, null);
        } catch (URISyntaxException | StorageException | InvalidKeyException e) {
            logger.log(Level.SEVERE, () -> String.format("[id=%s][QueueService] Processing function exception: %s, caused by: %s", invocationId, e.getMessage(), e.getCause()));
        }
        return true;
    }

    public static UploadMessage.UploadMessageBuilder generateMessageBuilder(UploadOperation operation, String uploadKey, String orgFiscalCode, String brokerCode) {
        return UploadMessage.builder()
                       .uploadOperation(operation)
                       .uploadKey(uploadKey)
                       .organizationFiscalCode(orgFiscalCode)
                       .brokerCode(brokerCode)
                       .retryCounter(0);
    }

    public static boolean enqueueDeleteMessage(ExecutionContext ctx, ObjectMapper om, List<String> IUPDList, UploadMessage.UploadMessageBuilder builder, int delay) {
        for (int i = 0; i < IUPDList.size(); i += CHUNK_SIZE) {
            List<String> IUPDSubList = IUPDList.subList(i, Math.min(i + CHUNK_SIZE, IUPDList.size()));
            UploadMessage message = builder.paymentPositionIUPDs(IUPDSubList).build();

            try {
                enqueue(ctx.getInvocationId(), ctx.getLogger(), om.writeValueAsString(message), delay);
            } catch (JsonProcessingException e) {
                ctx.getLogger().log(Level.SEVERE, () -> String.format("[id=%s][QueueService] Processing function exception: %s, caused by: %s", ctx.getInvocationId(), e.getMessage(), e.getCause()));
                return false;
            }
        }
        return true;
    }

    public static boolean enqueueUpsertMessage(ExecutionContext ctx, ObjectMapper om, List<PaymentPosition> paymentPositions, UploadMessage.UploadMessageBuilder builder, int delay) {
        for (int i = 0; i < paymentPositions.size(); i += CHUNK_SIZE) {
            List<PaymentPosition> positionSubList = paymentPositions.subList(i, Math.min(i + CHUNK_SIZE, paymentPositions.size()));
            UploadMessage message = builder.paymentPositions(positionSubList).build();
            try {
                enqueue(ctx.getInvocationId(), ctx.getLogger(), om.writeValueAsString(message), delay);
            } catch (JsonProcessingException e) {
                ctx.getLogger().log(Level.SEVERE, () -> String.format("[id=%s][QueueService] Processing function exception: %s, caused by: %s", ctx.getInvocationId(), e.getMessage(), e.getCause()));
                return false;
            }
        }
        return true;
    }
}
