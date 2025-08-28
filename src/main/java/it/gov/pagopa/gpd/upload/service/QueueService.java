package it.gov.pagopa.gpd.upload.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.Constants;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.queue.CloudQueue;
import com.microsoft.azure.storage.queue.CloudQueueMessage;
import it.gov.pagopa.gpd.upload.model.QueueMessage;
import it.gov.pagopa.gpd.upload.model.CRUDOperation;
import it.gov.pagopa.gpd.upload.model.enumeration.ServiceType;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QueueService {
    private static QueueService instance;
    private static final String GPD_SA_CONNECTION_STRING = System.getenv("GPD_SA_CONNECTION_STRING");
    private static final String VALID_POSITIONS_QUEUE =
            System.getenv("VALID_POSITIONS_QUEUE") != null ? System.getenv("VALID_POSITIONS_QUEUE") : "VALID_POSITIONS_QUEUE";
    public static final Integer CHUNK_SIZE = System.getenv("CHUNK_SIZE") != null ? Integer.parseInt(System.getenv("CHUNK_SIZE")) : 20;
    private CloudQueue cloudQueue;
    private Logger logger;

    public QueueService(Logger logger) {
        try {
            this.logger = logger;
            cloudQueue = CloudStorageAccount.parse(GPD_SA_CONNECTION_STRING).createCloudQueueClient()
                            .getQueueReference(VALID_POSITIONS_QUEUE);
        } catch (URISyntaxException | StorageException | InvalidKeyException e) {
            logger.log(Level.SEVERE, () -> String.format("[QueueService] Processing function exception: %s, caused by: %s", e.getMessage(), e.getCause()));
        }
    }

    public QueueService(Logger logger, CloudQueue cloudQueue) {
        this.logger = logger;
        this.cloudQueue = cloudQueue;
    }

    public static QueueService getInstance(Logger logger) {
        if (instance == null) {
            instance = new QueueService(logger);
        }
        return instance;
    }

    public boolean enqueue(String invocationId, String message, int initialVisibilityDelayInSeconds) {
        try {
            logger.log(Level.INFO, () -> String.format("[id=%s][QueueService] Add message of length %s to queue %s", invocationId, message.length(), VALID_POSITIONS_QUEUE));
            CloudQueueMessage cloudQueueMessage = new CloudQueueMessage(message);
            // timeToLiveInSeconds = 0 is default -> 7 days
            cloudQueue.addMessage(cloudQueueMessage, 0, initialVisibilityDelayInSeconds, null, null);
        } catch (StorageException e) {
            logger.log(Level.SEVERE, () -> String.format("[id=%s][QueueService] Processing function exception: %s, caused by: %s", invocationId, e.getMessage(), e.getCause()));
        }
        return true;
    }

    public QueueMessage.QueueMessageBuilder generateMessageBuilder(CRUDOperation operation, String uploadKey, String orgFiscalCode, String brokerCode, ServiceType serviceType) {
        return QueueMessage.builder()
                       .crudOperation(operation)
                       .uploadKey(uploadKey)
                       .organizationFiscalCode(orgFiscalCode)
                       .brokerCode(brokerCode)
                       .serviceType(serviceType)
                       .retryCounter(0);
    }

    public boolean enqueueDeleteMessage(ExecutionContext ctx, ObjectMapper om, List<String> IUPDList, QueueMessage.QueueMessageBuilder builder, int delay) {
        for (int i = 0; i < IUPDList.size(); i += CHUNK_SIZE) {
            List<String> IUPDSubList = IUPDList.subList(i, Math.min(i + CHUNK_SIZE, IUPDList.size()));
            QueueMessage message = builder.paymentPositionIUPDs(IUPDSubList).build();
            try {
                enqueue(ctx.getInvocationId(), om.writeValueAsString(message), delay);
            } catch (Exception e) {
                ctx.getLogger().log(Level.SEVERE, () -> String.format("[id=%s][QueueService] Processing function exception: %s, caused by: %s", ctx.getInvocationId(), e.getMessage(), e.getCause()));
                return false;
            }
        }
        return true;
    }

    public boolean enqueueUpsertMessage(ExecutionContext ctx, ObjectMapper om, List<PaymentPosition> paymentPositions, QueueMessage.QueueMessageBuilder builder, int delay, Integer chunkSize) {
        chunkSize = chunkSize != null ? chunkSize : CHUNK_SIZE;
        if(chunkSize == 0) return false;

        for (int i = 0; i < paymentPositions.size(); i += chunkSize) {
            List<PaymentPosition> positionSubList = paymentPositions.subList(i, Math.min(i + chunkSize, paymentPositions.size()));
            QueueMessage queueMessage = builder.paymentPositions(positionSubList).build();

            try {
                String message = om.writeValueAsString(queueMessage);

                if(message.length() > 64 * Constants.KB) // 64 KB is the max size for the queue message
                    enqueueUpsertMessage(ctx, om, positionSubList, builder, delay, chunkSize/2);
                else
                    enqueue(ctx.getInvocationId(), message, delay);
            } catch (Exception e) {
                ctx.getLogger().log(Level.SEVERE, () -> String.format("[id=%s][QueueService] Processing function exception: %s, caused by: %s", ctx.getInvocationId(), e.getMessage(), e.getCause()));
                return false;
            }
        }
        return true;
    }
}