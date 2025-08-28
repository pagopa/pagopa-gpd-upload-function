package it.gov.pagopa.gpd.upload.functions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.queue.CloudQueue;
import it.gov.pagopa.gpd.upload.functions.util.TestUtil;
import it.gov.pagopa.gpd.upload.model.CRUDOperation;
import it.gov.pagopa.gpd.upload.model.QueueMessage;
import it.gov.pagopa.gpd.upload.model.enumeration.ServiceType;
import it.gov.pagopa.gpd.upload.service.QueueService;
import org.junit.ClassRule;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.List;
import java.util.logging.Logger;

import static org.mockito.Mockito.when;

@Testcontainers
public class QueueServiceTest {
    private final ExecutionContext context = Mockito.mock(ExecutionContext.class);
    private QueueService queueService;

    @ClassRule
    @Container
    public static GenericContainer<?> azurite =
            new GenericContainer<>(
                    DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:latest"))
                    .withExposedPorts(10001, 10002, 10000);

    String localContainerConnectionString = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;QueueEndpoint=http://127.0.0.1:10001/devstoreaccount1;";

    @Test
    void testUpdateQueueMessage() throws URISyntaxException, InvalidKeyException, StorageException {
        CloudQueue cloudQueue = CloudStorageAccount.parse(localContainerConnectionString)
                                                    .createCloudQueueClient()
                                                    .getQueueReference("VALID_POSITIONS_QUEUE");

        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        queueService = new QueueService(logger, cloudQueue);
        when(context.getLogger()).thenReturn(logger);
        QueueMessage.QueueMessageBuilder builder = queueService.generateMessageBuilder(CRUDOperation.UPDATE, "key", "orgFiscalCode", "brokerCode", ServiceType.GPD);
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        queueService.enqueueUpsertMessage(context, om, List.of(TestUtil.getMockDebtPosition()), builder, 0, null);
    }

    @Test
    void testDeleteQueueMessage() throws URISyntaxException, InvalidKeyException, StorageException {
        CloudQueue cloudQueue = CloudStorageAccount.parse(localContainerConnectionString)
                                        .createCloudQueueClient()
                                        .getQueueReference("VALID_POSITIONS_QUEUE");

        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        queueService = new QueueService(logger, cloudQueue);
        when(context.getLogger()).thenReturn(logger);
        QueueMessage.QueueMessageBuilder builder = queueService.generateMessageBuilder(CRUDOperation.DELETE, "key", "orgFiscalCode", "brokerCode", ServiceType.GPD);
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        queueService.enqueueDeleteMessage(context, om, List.of(new String[]{"IUPD1"}), builder, 0);
    }
}
