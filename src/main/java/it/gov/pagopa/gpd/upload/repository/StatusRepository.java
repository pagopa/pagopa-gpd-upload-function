package it.gov.pagopa.gpd.upload.repository;

import com.azure.cosmos.*;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosPatchOperations;
import com.azure.cosmos.models.PartitionKey;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.exception.AppException;

import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StatusRepository {
    private static volatile StatusRepository instance;
    private final String cosmosURI = System.getenv("COSMOS_URI");
    private final String cosmosKey = System.getenv("COSMOS_KEY");
    private final String databaseName = System.getenv("GPD_DB_NAME");
    private final String containerName = System.getenv("GPD_CONTAINER_NAME");
    private CosmosContainer container;
    private final Logger logger;

    public static StatusRepository getInstance(Logger logger) {
        if (instance == null) {
            synchronized (StatusRepository.class) {
                if (instance == null) {
                    instance = new StatusRepository(logger);
                }
            }
        }
        return instance;
    }

    private StatusRepository(Logger logger) {
        this.logger = logger;
        this.initCosmosClient();
    }

    private void initCosmosClient() {
        CosmosClient cosmosClient = new CosmosClientBuilder()
                .endpoint(cosmosURI)
                .key(cosmosKey)
                .consistencyLevel(ConsistencyLevel.EVENTUAL)
                .buildClient();
        container = cosmosClient.getDatabase(databaseName).getContainer(containerName);
    }

    //  Read document by ID and Partition Key
    public synchronized Status createIfNotExist(String invocationId, String statusId, String partitionKey, Status statusIfNotExist) throws AppException {
        try {
            CosmosItemResponse<Status> response = container.readItem(statusId, new PartitionKey(partitionKey), Status.class);
            logger.log(Level.INFO, () -> String.format("[id=%s][StatusRepository] Item with ID %s already exists. Skipping creation.", invocationId, statusId));
            return response.getItem();
        } catch (CosmosException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
                logger.log(Level.INFO, () -> String.format("[id=%s][StatusRepository] Item with ID %s doesn't exist. It will be created.", invocationId, statusId));
                this.upsertStatus(invocationId, statusIfNotExist.id, statusIfNotExist);
                return statusIfNotExist;
            } else {
                logger.log(Level.SEVERE, () -> String.format("[id=%s][StatusRepository] Error while create status item, code: %s", invocationId, ex.getStatusCode()));
                throw new AppException("Error " + ex.getStatusCode() + " while reading Status item: " + statusId);
            }
        }
    }

    public synchronized Status getStatus(String invocationId, String id, String partitionKey) throws AppException {
        try {
            CosmosItemResponse<Status> response = container.readItem(id, new PartitionKey(partitionKey), Status.class);
            logger.log(Level.INFO, () -> String.format("Read Status document with id %s response: %s", id, response.getStatusCode()));
            return response.getItem();
        } catch (CosmosException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
                logger.log(Level.INFO, () -> String.format("Read Status document with id %s not found", id));
                return null;
            } else {
                logger.log(Level.SEVERE, () -> String.format("[id=%s][StatusRepository] Error while reading status item, code: %s", invocationId, ex.getStatusCode()));
                throw new AppException("Error " + ex.getStatusCode() + " while reading Status item: " + id);
            }
        }
    }

    public synchronized void upsertStatus(String invocationId, String id, Status status) throws AppException {
        try {
            CosmosItemResponse<Status> response = container.upsertItem(status, new CosmosItemRequestOptions());
            if(response.getStatusCode() < 200 || response.getStatusCode() > 299) {
                logger.log(Level.SEVERE, () -> String.format("[id=%s][StatusRepository] Error while upsert status item, code: %s", invocationId, response.getStatusCode()));
                throw new AppException("Error while upsert Status item " + id);
            }
        } catch (CosmosException e) {
            logger.log(Level.SEVERE, () -> String.format("[id=%s][StatusRepository] Error while upsert status item, code: %s, message: %s", invocationId, e.getStatusCode(), e.getMessage()));
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
}
