package it.gov.pagopa.gpd.upload.repository;

import com.azure.cosmos.*;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosPatchOperations;
import com.azure.cosmos.models.PartitionKey;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.entity.ResponseEntry;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.util.MapUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StatusRepository {
    private static volatile StatusRepository instance;
    private String cosmosURI = System.getenv("COSMOS_URI");
    private String cosmosKey = System.getenv("COSMOS_KEY");
    private String databaseName = System.getenv("GPD_DB_NAME");
    private String containerName = System.getenv("GPD_CONTAINER_NAME");
    private CosmosClient cosmosClient;
    private CosmosContainer container;
    private Logger logger;

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
        cosmosClient = new CosmosClientBuilder()
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
            return response.getItem();
        } catch (CosmosException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
                logger.log(Level.INFO, () -> "Item with ID " + id);
                return null;
            } else {
                logger.log(Level.SEVERE, () -> String.format("[id=%s][StatusRepository] Error while reading status item, code: %s", invocationId, ex.getStatusCode()));
                throw new AppException("Error " + ex.getStatusCode() + " while reading Status item: " + id);
            }
        }
    }

    private synchronized void upsertStatus(String invocationId, String id, Status status) throws AppException {
        logger.log(Level.INFO, () ->"Upsert Status id " + id);

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

    public void increment(String id, String fiscalCode, ResponseEntry entry) {
        String key = MapUtils.getKey(HttpStatus.valueOf(entry.getStatusCode()));
        List<String> requestIDs = entry.getRequestIDs();

        // The number of patch operations cannot exceed '10'
        for(int i=0; i<requestIDs.size(); i+=10) {
            List<String> subRequestIDs = requestIDs.subList(i, Math.min(i+10, requestIDs.size()));
            CosmosPatchOperations operations = CosmosPatchOperations
                    .create()
                    .increment("/upload/current", subRequestIDs.size());
            requestIDs.subList(i, Math.min(i+10, requestIDs.size())).forEach(requestID -> operations.add("/upload/"+key+"/requestIDs/-", requestID));
            container.patchItem(
                    id,
                    new PartitionKey(fiscalCode),
                    operations,
                    Status.class
            );
        }
    }

    public void partialUpdate(String id, String fiscalCode, LocalDateTime endDateTime) {
        CosmosPatchOperations operations = CosmosPatchOperations
                                                   .create()
                                                   .replace("/upload/end", endDateTime);
        CosmosItemResponse<Status> response = container.patchItem(
                id,
                new PartitionKey(fiscalCode),
                operations,
                Status.class
        );
        response.getStatusCode();
    }
}
