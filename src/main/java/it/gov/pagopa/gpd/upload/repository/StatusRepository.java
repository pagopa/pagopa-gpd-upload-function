package it.gov.pagopa.gpd.upload.repository;

import com.azure.cosmos.*;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosPatchOperations;
import com.azure.cosmos.models.PartitionKey;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.exception.AppException;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatusRepository {
	private static final Logger log = LoggerFactory.getLogger(StatusRepository.class);
    private static final String LOG_PREFIX = "[id={}][StatusRepository]";

    private static volatile StatusRepository instance;

    private final String cosmosURI = System.getenv("COSMOS_URI");
    private final String cosmosKey = System.getenv("COSMOS_KEY");
    private final String databaseName = System.getenv("GPD_DB_NAME");
    private final String containerName = System.getenv("GPD_CONTAINER_NAME");

    private CosmosContainer container;
    private final ThrottlingRetryOptions throttlingRetryOptions = new ThrottlingRetryOptions();

    public static StatusRepository getInstance() {
        if (instance == null) {
            synchronized (StatusRepository.class) {
                if (instance == null) {
                    instance = new StatusRepository();
                }
            }
        }
        return instance;
    }

    private StatusRepository() {
        this.initCosmosClient();
    }

    private void initCosmosClient() {
        throttlingRetryOptions.setMaxRetryWaitTime(Duration.ofMinutes(10));
        throttlingRetryOptions.setMaxRetryAttemptsOnThrottledRequests(100);
        CosmosClient cosmosClient = new CosmosClientBuilder()
                .endpoint(cosmosURI)
                .key(cosmosKey)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .throttlingRetryOptions(throttlingRetryOptions)
                .buildClient();
        container = cosmosClient.getDatabase(databaseName).getContainer(containerName);
    }

    //  Read document by ID and Partition Key
    public synchronized Status createIfNotExist(String invocationId, String statusId, String partitionKey, Status statusIfNotExist) throws AppException {
        try {
            CosmosItemResponse<Status> response = container.readItem(statusId, new PartitionKey(partitionKey), Status.class);
            log.info(LOG_PREFIX + " Item with ID {} already exists. Skipping creation.",
                    invocationId, statusId);
            return response.getItem();
        } catch (CosmosException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
            	log.info(LOG_PREFIX + " Item with ID {} doesn't exist. It will be created.",
            	        invocationId, statusId);
                this.upsertStatus(invocationId, statusIfNotExist.id, statusIfNotExist);
                return statusIfNotExist;
            } else {
            	log.error(LOG_PREFIX + " Error while creating status item {}, code {}",
            	        invocationId, statusId, ex.getStatusCode(), ex);
            	throw new AppException("Error " + ex.getStatusCode() + " while reading Status item: " + statusId);
            }
        }
    }

    public synchronized Status getStatus(String invocationId, String id, String partitionKey) throws AppException {
        try {
            CosmosItemResponse<Status> response = container.readItem(id, new PartitionKey(partitionKey), Status.class);
            log.info(LOG_PREFIX + " Read Status document with id {} response: {}",
                    invocationId, id, response.getStatusCode());
            return response.getItem();
        } catch (CosmosException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
            	log.info(LOG_PREFIX + " Read Status document with id {} not found",
            	        invocationId, id);
                return null;
            } else {
            	log.error(LOG_PREFIX + " Error while reading status item {}, code {}",
            	        invocationId, id, ex.getStatusCode(), ex);
            	throw new AppException("Error " + ex.getStatusCode() + " while reading Status item: " + id);
            }
        }
    }

    public synchronized void upsertStatus(String invocationId, String id, Status status) throws AppException {
        try {
            CosmosItemResponse<Status> response = container.upsertItem(status, new CosmosItemRequestOptions());
            if(response.getStatusCode() < 200 || response.getStatusCode() > 299) {
            	log.error(LOG_PREFIX + " Error while upserting status item {}, code {}",
            	        invocationId, id, response.getStatusCode());
                throw new AppException("Error while upsert Status item " + id);
            }
        } catch (CosmosException e) {
        	log.error(LOG_PREFIX + " Error while upserting status item {}, code {}, message {}",
        	        invocationId, id, e.getStatusCode(), e.getMessage(), e);
        	throw new AppException("Error while upsert Status item " + id);
        }
    }

    public boolean partialUpdate(String id, String fiscalCode, LocalDateTime endDateTime) {
        CosmosPatchOperations operations = CosmosPatchOperations
                .create()
                .replace("/upload/end", endDateTime);
        CosmosItemResponse<Status> response = container.patchItem(
                id,
                new PartitionKey(fiscalCode),
                operations,
                Status.class
        );
        return response.getStatusCode() == HttpStatus.OK.value();
    }

    private static RetryBackoffSpec getRetryPolicy() {
        // Customize the retry policy for handling 429 status codes
        return Retry.backoff(5, Duration.ofSeconds(1))
                       .maxBackoff(Duration.ofSeconds(30))
                       .filter(throwable -> throwable instanceof com.azure.cosmos.CosmosException &&
                                                    ((com.azure.cosmos.CosmosException) throwable).getStatusCode() == 429)
                       .doBeforeRetry(retrySignal ->
                       log.warn("[StatusRepository] Retry attempt #{} after {} retries due to {}",
                               retrySignal.totalRetries() + 1,
                               retrySignal.totalRetries(),
                               retrySignal.failure().getMessage())
                       );
    }
}
