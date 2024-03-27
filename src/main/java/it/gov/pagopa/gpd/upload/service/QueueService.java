package it.gov.pagopa.gpd.upload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.queue.CloudQueue;
import com.microsoft.azure.storage.queue.CloudQueueMessage;
import it.gov.pagopa.gpd.upload.entity.UploadMessage;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QueueService {

    private static final String GPD_SA_CONNECTION_STRING = System.getenv("GPD_SA_CONNECTION_STRING");
    private static final String VALID_POSITIONS_QUEUE = System.getenv("VALID_POSITIONS_QUEUE");

    public static void enqueue(String invocationId, Logger logger, String message, int initialVisibilityDelayInSeconds) {
        try {
            CloudQueue queue = CloudStorageAccount.parse(GPD_SA_CONNECTION_STRING).createCloudQueueClient()
                                       .getQueueReference(VALID_POSITIONS_QUEUE);
            CloudQueueMessage cloudQueueMessage = new CloudQueueMessage(message);

            logger.log(Level.INFO, () -> String.format("[id=%s][ServiceFunction] Add message of length %s to queue %s", invocationId, message.length(), VALID_POSITIONS_QUEUE));

            // timeToLiveInSeconds = 0 is default -> 7 days
            queue.addMessage(cloudQueueMessage, 0, initialVisibilityDelayInSeconds, null, null);
        } catch (URISyntaxException | StorageException | InvalidKeyException e) {
            logger.log(Level.SEVERE, () -> String.format("[id=%s][QueueService] Processing function exception: %s, caused by: %s", invocationId, e.getMessage(), e.getCause()));
        }
    }
}
