package it.gov.pagopa.gpd.upload.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.Constants;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.queue.CloudQueue;
import com.microsoft.azure.storage.queue.CloudQueueMessage;
import it.gov.pagopa.gpd.upload.model.CRUDOperation;
import it.gov.pagopa.gpd.upload.model.QueueMessage;
import it.gov.pagopa.gpd.upload.model.enumeration.ServiceType;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.List;

public class QueueService {
    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private static volatile QueueService instance;

    private static final String GPD_SA_CONNECTION_STRING = System.getenv("GPD_SA_CONNECTION_STRING");
    private static final String VALID_POSITIONS_QUEUE =
            System.getenv("VALID_POSITIONS_QUEUE") != null ? System.getenv("VALID_POSITIONS_QUEUE") : "VALID_POSITIONS_QUEUE";

    public static final Integer CHUNK_SIZE =
            System.getenv("CHUNK_SIZE") != null ? Integer.parseInt(System.getenv("CHUNK_SIZE")) : 20;

    private CloudQueue cloudQueue;

    public QueueService() {
        try {
            cloudQueue = CloudStorageAccount.parse(GPD_SA_CONNECTION_STRING)
                    .createCloudQueueClient()
                    .getQueueReference(VALID_POSITIONS_QUEUE);
        } catch (URISyntaxException | StorageException | InvalidKeyException e) {
            log.error("[QueueService] Processing function exception while initializing queue {}",
                    VALID_POSITIONS_QUEUE, e);
        }
    }

    public QueueService(CloudQueue cloudQueue) {
        this.cloudQueue = cloudQueue;
    }

    public static QueueService getInstance() {
        if (instance == null) {
            synchronized (QueueService.class) {
                if (instance == null) {
                    instance = new QueueService();
                }
            }
        }
        return instance;
    }

    public boolean enqueue(String invocationId, String message, int initialVisibilityDelayInSeconds) {
        try {
            if (cloudQueue == null) {
                log.error("[id={}][QueueService] Unable to add message to queue {}: cloudQueue is not initialized",
                        invocationId, VALID_POSITIONS_QUEUE);
                return false;
            }

            log.info("[id={}][QueueService] Add message of length {} to queue {}",
                    invocationId, message.length(), VALID_POSITIONS_QUEUE);

            CloudQueueMessage cloudQueueMessage = new CloudQueueMessage(message);
            // timeToLiveInSeconds = 0 is default -> 7 days
            cloudQueue.addMessage(cloudQueueMessage, 0, initialVisibilityDelayInSeconds, null, null);

            return true;
        } catch (StorageException e) {
            log.error("[id={}][QueueService] Processing function exception while adding message to queue {}",
                    invocationId, VALID_POSITIONS_QUEUE, e);
            return false;
        }
    }

    public QueueMessage.QueueMessageBuilder generateMessageBuilder(
            CRUDOperation operation,
            String uploadKey,
            String orgFiscalCode,
            String brokerCode,
            ServiceType serviceType) {

        return QueueMessage.builder()
                .crudOperation(operation)
                .uploadKey(uploadKey)
                .organizationFiscalCode(orgFiscalCode)
                .brokerCode(brokerCode)
                .serviceType(serviceType)
                .retryCounter(0);
    }

    public boolean enqueueDeleteMessage(
            ExecutionContext ctx,
            ObjectMapper om,
            List<String> iupdList,
            QueueMessage.QueueMessageBuilder builder,
            int delay) {

        for (int i = 0; i < iupdList.size(); i += CHUNK_SIZE) {
            List<String> iupdSubList = iupdList.subList(i, Math.min(i + CHUNK_SIZE, iupdList.size()));
            QueueMessage message = builder.paymentPositionIUPDs(iupdSubList).build();

            try {
                boolean enqueued = enqueue(ctx.getInvocationId(), om.writeValueAsString(message), delay);
                if (!enqueued) {
                    return false;
                }
            } catch (Exception e) {
                log.error("[id={}][QueueService] Processing function exception while enqueueing delete message",
                        ctx.getInvocationId(), e);
                return false;
            }
        }

        return true;
    }

    public boolean enqueueUpsertMessage(
            ExecutionContext ctx,
            ObjectMapper om,
            List<PaymentPosition> paymentPositions,
            QueueMessage.QueueMessageBuilder builder,
            int delay,
            Integer chunkSize) {

        chunkSize = chunkSize != null ? chunkSize : CHUNK_SIZE;

        if (chunkSize == 0) {
            log.error("[id={}][QueueService] Unable to enqueue upsert message: chunk size is zero",
                    ctx.getInvocationId());
            return false;
        }

        for (int i = 0; i < paymentPositions.size(); i += chunkSize) {
            List<PaymentPosition> positionSubList =
                    paymentPositions.subList(i, Math.min(i + chunkSize, paymentPositions.size()));
            QueueMessage queueMessage = builder.paymentPositions(positionSubList).build();

            try {
                String message = om.writeValueAsString(queueMessage);

                if (message.length() > 64 * Constants.KB) { // 64 KB is the max size for the queue message
                    log.warn("[id={}][QueueService] Queue message length {} exceeds 64 KB. Reducing chunk size from {} to {}",
                            ctx.getInvocationId(), message.length(), chunkSize, chunkSize / 2);

                    boolean enqueued = enqueueUpsertMessage(ctx, om, positionSubList, builder, delay, chunkSize / 2);
                    if (!enqueued) {
                        return false;
                    }
                } else {
                    boolean enqueued = enqueue(ctx.getInvocationId(), message, delay);
                    if (!enqueued) {
                        return false;
                    }
                }

            } catch (Exception e) {
                log.error("[id={}][QueueService] Processing function exception while enqueueing upsert message",
                        ctx.getInvocationId(), e);
                return false;
            }
        }

        return true;
    }
}