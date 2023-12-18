package it.gov.pagopa.gpd.upload.repository;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.exception.AppException;

import java.util.logging.Logger;

public class StatusRepository {

    private String cosmosURI = System.getenv("COSMOS_URI");
    private String cosmosKey = System.getenv("COSMOS_KEY");
    private String databaseName = System.getenv("GPD_DB_NAME");
    private String containerName = System.getenv("GPD_CONTAINER_NAME");
    private CosmosClient cosmosClient;
    private CosmosContainer container;
    private static StatusRepository instance = null;
    private Logger logger;
    public static StatusRepository getInstance(Logger logger) {
        if (instance == null) {
            instance = new StatusRepository(logger);
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
                .buildClient();
        container = cosmosClient.getDatabase(databaseName).getContainer(containerName);
    }

    //  Read document by ID and Partition Key
    public Status readStatus(String id, String partitionKey) throws AppException {
        try {
            Status status = container.readItem(id, new PartitionKey(partitionKey), Status.class).getItem();
            return status;
        } catch (Exception e) {
            logger.warning("Error while reading Status item: " + e.getMessage());
            throw new AppException("Error while reading Status item " + id);
        }
    }

    public void upsertStatus(String id, Status status) throws Exception {
        logger.info("Upsert Status item " + id);

        try {
            CosmosItemResponse<Status> response = container.upsertItem(status, new CosmosItemRequestOptions());
            if(response.getStatusCode() < 200 || response.getStatusCode() > 299) {
                throw new AppException("Error while upsert Status item " + id);
            }
        } catch (Exception e) {
            logger.warning("Error while reading Status item: " + e.getMessage());
            throw new AppException("Error while upsert Status item " + id);
        }
    }
}
