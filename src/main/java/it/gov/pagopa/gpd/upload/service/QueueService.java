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

    public QueueMessage.QueueMessageBuilder generateMessageBuilder(CRUDOperation operation, String uploadKey, String orgFiscalCode, String brokerCode) {
        return QueueMessage.builder()
                       .crudOperation(operation)
                       .uploadKey(uploadKey)
                       .organizationFiscalCode(orgFiscalCode)
                       .brokerCode(brokerCode)
                       .retryCounter(0);
    }

    public boolean enqueueDeleteMessage(ExecutionContext ctx, ObjectMapper om, List<String> IUPDList, QueueMessage.QueueMessageBuilder builder, int delay) {
        for (int i = 0; i < IUPDList.size(); i += CHUNK_SIZE) {
            QueueMessage message = builder.paymentPositionIUPDs(IUPDList).build();
            try {
                enqueue(ctx.getInvocationId(), om.writeValueAsString(message), delay);
            } catch (Exception e) {
                ctx.getLogger().log(Level.SEVERE, () -> String.format("[id=%s][QueueService] Processing function exception: %s, caused by: %s", ctx.getInvocationId(), e.getMessage(), e.getCause()));
                return false;
            }
        }
        return true;
    }

    public boolean enqueueUpsertMessage(ExecutionContext ctx, ObjectMapper om, List<PaymentPosition> paymentPositions, QueueMessage.QueueMessageBuilder builder, int delay) {
        for (int i = 0; i < paymentPositions.size(); i += CHUNK_SIZE) {
            QueueMessage queueMessage = builder.paymentPositions(paymentPositions).build();
            try {
                enqueue(ctx.getInvocationId(), om.writeValueAsString(queueMessage), delay);
            } catch (Exception e) {
                ctx.getLogger().log(Level.SEVERE, () -> String.format("[id=%s][QueueService] Processing function exception: %s, caused by: %s", ctx.getInvocationId(), e.getMessage(), e.getCause()));
                return false;
            }
        }
        return true;
    }
}