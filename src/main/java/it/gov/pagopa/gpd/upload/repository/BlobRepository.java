package it.gov.pagopa.gpd.upload.repository;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.util.MapUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.logging.Level;
import java.util.logging.Logger;

@Slf4j
public class BlobRepository {

    private final String connectionString = System.getenv("GPD_SA_CONNECTION_STRING");

    private static final String REPORT_SUFFIX = "report";
    private static final String INPUT_DIRECTORY = "input";
    private static final String OUTPUT_DIRECTORY = "output";
    private BlobServiceClient blobServiceClient;
    private Logger logger;

    private static volatile BlobRepository instance;
    public static BlobRepository getInstance(Logger logger) {
        if (instance == null) {
            synchronized (BlobRepository.class) {
                if (instance == null) {
                    instance = new BlobRepository(logger);
                }
            }
        }
        return instance;
    }

    private BlobRepository(Logger logger) {
        this.logger = logger;
    }

    public BinaryData download(String broker, String fiscalCode, String filename) {
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
            logger.log(Level.INFO, () -> "blob doesn't exist: " + blobName);

        return blobClient.downloadContent();
    }

    public void report(Status status) {
        ObjectMapper om = new ObjectMapper();
        om.enable(SerializationFeature.INDENT_OUTPUT);
        om.registerModule(new JavaTimeModule());
        try {
            String report = om.writeValueAsString(MapUtils.convert(status));
            BlobRepository.getInstance(logger).uploadReport(report, status.brokerID, status.fiscalCode, status.id + ".json");
        } catch (JsonProcessingException e) {
            logger.log(Level.SEVERE, () -> "JsonProcessingException " + e.getMessage());
        }
    }

    public void uploadReport(String data, String broker, String fiscalCode, String filename) {
        String blobPath = "/" + fiscalCode + "/" + OUTPUT_DIRECTORY + "/" + REPORT_SUFFIX + filename;
        this.upload(data, broker, blobPath);
    }

    public void upload(String data, String container, String blobPath) {
        try {
            blobServiceClient = new BlobServiceClientBuilder()
                                        .connectionString(connectionString)
                                        .buildClient();
            blobServiceClient.createBlobContainerIfNotExists(container);
            BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(container);

            if (!blobContainerClient.exists())
                logger.log(Level.SEVERE, () -> "container doesn't exist");
            BlobClient blobClient = blobContainerClient.getBlobClient(blobPath);
            blobClient.upload(BinaryData.fromString(data));
        } catch (BlobStorageException e) {
            logger.log(Level.SEVERE, () -> "BlobStorageException " + e.getMessage());
        }
    }
}


