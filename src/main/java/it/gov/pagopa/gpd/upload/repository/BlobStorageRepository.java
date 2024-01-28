package it.gov.pagopa.gpd.upload.repository;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.logging.Level;
import java.util.logging.Logger;

@Slf4j
public class BlobStorageRepository {

    private final String connectionString = System.getenv("GPD_SA_CONNECTION_STRING");;

    private static final String INPUT_DIRECTORY = "input";
    private static final String OUTPUT_DIRECTORY = "output";

    private BlobServiceClient blobServiceClient;

    public BinaryData download(Logger logger, String broker, String fiscalCode, String filename) {
        blobServiceClient = new BlobServiceClientBuilder()
                                    .connectionString(connectionString)
                                    .buildClient();
        blobServiceClient.createBlobContainerIfNotExists(broker);
        BlobContainerClient container = blobServiceClient.getBlobContainerClient(broker);

        if(!container.exists())
            logger.log(Level.INFO, () -> "container doesn't exist");

        String blobName = "/" + fiscalCode + "/" + INPUT_DIRECTORY + "/" + filename;
        BlobClient blobClient = container.getBlobClient(blobName);
        if(!blobClient.exists())
            logger.log(Level.INFO, () -> "blob doesn't exist");

        return blobClient.downloadContent();
    }

    public void uploadOutput(Logger logger, String data, String broker, String fiscalCode, String filename) {
        blobServiceClient = new BlobServiceClientBuilder()
                                    .connectionString(connectionString)
                                    .buildClient();
        blobServiceClient.createBlobContainerIfNotExists(broker);
        BlobContainerClient container = blobServiceClient.getBlobContainerClient(broker);

        if(!container.exists())
            logger.log(Level.INFO, () -> "container doesn't exist");

        String blobName = "/" + fiscalCode + "/" + OUTPUT_DIRECTORY + "/" + filename;
        BlobClient blobClient = container.getBlobClient(blobName);
        if(!blobClient.exists())
            logger.log(Level.INFO, () -> "blob doesn't exist");

        blobClient.upload(BinaryData.fromString(data));
    }
}
