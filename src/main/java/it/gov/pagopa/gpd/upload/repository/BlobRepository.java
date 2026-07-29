package it.gov.pagopa.gpd.upload.repository;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobStorageException;
import it.gov.pagopa.gpd.upload.model.enumeration.ServiceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static it.gov.pagopa.gpd.upload.util.Constants.BLOB_KEY;
import static it.gov.pagopa.gpd.upload.util.Constants.SERVICE_TYPE_KEY;

public class BlobRepository {
    private static final Logger log = LoggerFactory.getLogger(BlobRepository.class);

    private static final String REPORT_SUFFIX = "report";
    private static final String INPUT_DIRECTORY = "input";
    private static final String OUTPUT_DIRECTORY = "output";
    private static final String BLOB_PATH_DELIMITER = "/";

    private final String connectionString = System.getenv("GPD_SA_CONNECTION_STRING");

    private BlobServiceClient blobServiceClient;

    public Map<String, Object> download(String broker, String fiscalCode, String filename) {
        blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();

        blobServiceClient.createBlobContainerIfNotExists(broker);

        BlobContainerClient container = blobServiceClient.getBlobContainerClient(broker);

        if (Boolean.FALSE.equals(container.exists())) {
            log.warn("[BlobRepository] Container {} does not exist", broker);
        }

        String blobName = buildInputBlobPath(fiscalCode, filename);

        BlobClient blobClient = container.getBlobClient(blobName);

        if (Boolean.FALSE.equals(blobClient.exists())) {
            log.warn("[BlobRepository] Blob {} does not exist in container {}", blobName, broker);
        }

        BlobProperties properties = blobClient.getProperties();

        ServiceType serviceType = ServiceType.valueOf(
                properties.getMetadata().getOrDefault(SERVICE_TYPE_KEY, ServiceType.GPD.name())
        );

        log.info("[BlobRepository] Downloading blob {} from container {} with serviceType {}",
                blobName, broker, serviceType);

        return Map.of(
                BLOB_KEY, blobClient.downloadContent(),
                SERVICE_TYPE_KEY, serviceType
        );
    }

    public boolean uploadReport(String data, String broker, String fiscalCode, String filename, ServiceType serviceType) {
        String blobPath = buildReportBlobPath(fiscalCode, filename);

        final boolean reportUploaded = upload(data, broker, blobPath);

        if (reportUploaded) {
            setServiceTypeMetadata(serviceType, broker, blobPath);
        }

        return reportUploaded;
    }

    private boolean upload(String data, String container, String blobPath) {
        try {
            blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();

            blobServiceClient.createBlobContainerIfNotExists(container);

            BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(container);

            if (Boolean.FALSE.equals(blobContainerClient.exists())) {
                log.error("[BlobRepository] Container {} does not exist", container);
            }

            BlobClient blobClient = blobContainerClient.getBlobClient(blobPath);
            blobClient.upload(BinaryData.fromString(data));

            log.info("[BlobRepository] Uploaded report blob {} to container {}", blobPath, container);

            return true;
        } catch (BlobStorageException e) {
            log.error("[BlobRepository] BlobStorageException while uploading blob {} to container {}",
                    blobPath, container, e);
            return false;
        }
    }

    private void setServiceTypeMetadata(ServiceType serviceType, String container, String blobPath) {
        Map<String, String> metadata = Map.of(SERVICE_TYPE_KEY, serviceType.name());

        BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(container);
        blobContainerClient.getBlobClient(blobPath).setMetadata(metadata);

        log.info("[BlobRepository] Set serviceType metadata {} on blob {} in container {}",
                serviceType, blobPath, container);
    }

    private String buildInputBlobPath(String fiscalCode, String filename) {
        return String.join(
                BLOB_PATH_DELIMITER,
                "",
                fiscalCode,
                INPUT_DIRECTORY,
                filename
        );
    }

    private String buildReportBlobPath(String fiscalCode, String filename) {
        return String.join(
                BLOB_PATH_DELIMITER,
                "",
                fiscalCode,
                OUTPUT_DIRECTORY,
                REPORT_SUFFIX + filename
        );
    }
}