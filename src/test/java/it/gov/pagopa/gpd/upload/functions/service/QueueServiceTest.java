package it.gov.pagopa.gpd.upload.functions.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.storage.OperationContext;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.queue.CloudQueue;
import com.microsoft.azure.storage.queue.CloudQueueMessage;
import com.microsoft.azure.storage.queue.QueueRequestOptions;
import it.gov.pagopa.gpd.upload.functions.util.TestUtil;
import it.gov.pagopa.gpd.upload.model.CRUDOperation;
import it.gov.pagopa.gpd.upload.model.QueueMessage;
import it.gov.pagopa.gpd.upload.model.enumeration.ServiceType;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import it.gov.pagopa.gpd.upload.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueueServiceTest {
    private final ExecutionContext context = mock(ExecutionContext.class);
    private final CloudQueue cloudQueue = mock(CloudQueue.class);

    private QueueService queueService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(cloudQueue);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        when(context.getInvocationId()).thenReturn("testInvocationId");
    }

    @Test
    void testUpdateQueueMessage() throws Exception {
        QueueMessage.QueueMessageBuilder builder = queueService.generateMessageBuilder(
                CRUDOperation.UPDATE,
                "key",
                "orgFiscalCode",
                "brokerCode",
                ServiceType.GPD
        );

        boolean result = queueService.enqueueUpsertMessage(
                context,
                objectMapper,
                List.of(TestUtil.getMockDebtPosition()),
                builder,
                0,
                null
        );

        assertTrue(result);

        ArgumentCaptor<CloudQueueMessage> messageCaptor = ArgumentCaptor.forClass(CloudQueueMessage.class);

        verify(cloudQueue).addMessage(
                messageCaptor.capture(),
                eq(0),
                eq(0),
                isNull(QueueRequestOptions.class),
                isNull(OperationContext.class)
        );

        String queuePayload = messageCaptor.getValue().getMessageContentAsString();
        QueueMessage queueMessage = objectMapper.readValue(queuePayload, QueueMessage.class);

        assertEquals(CRUDOperation.UPDATE, queueMessage.getCrudOperation());
        assertEquals("key", queueMessage.getUploadKey());
        assertEquals("orgFiscalCode", queueMessage.getOrganizationFiscalCode());
        assertEquals("brokerCode", queueMessage.getBrokerCode());
        assertEquals(ServiceType.GPD, queueMessage.getServiceType());
        assertTrue(queuePayload.contains("paymentPositions"));
    }

    @Test
    void testDeleteQueueMessage() throws Exception {
        QueueMessage.QueueMessageBuilder builder = queueService.generateMessageBuilder(
                CRUDOperation.DELETE,
                "key",
                "orgFiscalCode",
                "brokerCode",
                ServiceType.GPD
        );

        boolean result = queueService.enqueueDeleteMessage(
                context,
                objectMapper,
                List.of("IUPD1"),
                builder,
                0
        );

        assertTrue(result);

        ArgumentCaptor<CloudQueueMessage> messageCaptor = ArgumentCaptor.forClass(CloudQueueMessage.class);

        verify(cloudQueue).addMessage(
                messageCaptor.capture(),
                eq(0),
                eq(0),
                isNull(QueueRequestOptions.class),
                isNull(OperationContext.class)
        );

        String queuePayload = messageCaptor.getValue().getMessageContentAsString();
        QueueMessage queueMessage = objectMapper.readValue(queuePayload, QueueMessage.class);

        assertEquals(CRUDOperation.DELETE, queueMessage.getCrudOperation());
        assertEquals("key", queueMessage.getUploadKey());
        assertEquals("orgFiscalCode", queueMessage.getOrganizationFiscalCode());
        assertEquals("brokerCode", queueMessage.getBrokerCode());
        assertEquals(ServiceType.GPD, queueMessage.getServiceType());
        assertTrue(queuePayload.contains("IUPD1"));
    }

    @Test
    void testDeleteQueueMessageReturnsFalseWhenQueueFails() throws Exception {
        doThrow(new StorageException(
                "QUEUE_ERROR",
                "Queue add message failed",
                500,
                null,
                null
        )).when(cloudQueue).addMessage(
                any(CloudQueueMessage.class),
                eq(0),
                eq(0),
                isNull(QueueRequestOptions.class),
                isNull(OperationContext.class)
        );

        QueueMessage.QueueMessageBuilder builder = queueService.generateMessageBuilder(
                CRUDOperation.DELETE,
                "key",
                "orgFiscalCode",
                "brokerCode",
                ServiceType.GPD
        );

        boolean result = queueService.enqueueDeleteMessage(
                context,
                objectMapper,
                List.of("IUPD1"),
                builder,
                0
        );

        assertFalse(result);

        verify(cloudQueue).addMessage(
                any(CloudQueueMessage.class),
                eq(0),
                eq(0),
                isNull(QueueRequestOptions.class),
                isNull(OperationContext.class)
        );
    }

    @Test
    void enqueueReturnsFalseWhenCloudQueueIsNotInitialized() {
        QueueService serviceWithoutQueue = new QueueService(null);

        boolean result = serviceWithoutQueue.enqueue("testInvocationId", "message", 0);

        assertFalse(result);
    }

    @Test
    void enqueueAddsMessageWithInitialVisibilityDelay() throws Exception {
        boolean result = queueService.enqueue("testInvocationId", "message", 10);

        assertTrue(result);

        ArgumentCaptor<CloudQueueMessage> messageCaptor = ArgumentCaptor.forClass(CloudQueueMessage.class);

        verify(cloudQueue).addMessage(
                messageCaptor.capture(),
                eq(0),
                eq(10),
                isNull(QueueRequestOptions.class),
                isNull(OperationContext.class)
        );

        assertEquals("message", messageCaptor.getValue().getMessageContentAsString());
    }

    @Test
    void enqueueUpsertMessageReturnsFalseWhenChunkSizeIsZero() {
        QueueMessage.QueueMessageBuilder builder = queueService.generateMessageBuilder(
                CRUDOperation.UPDATE,
                "key",
                "orgFiscalCode",
                "brokerCode",
                ServiceType.GPD
        );

        boolean result = queueService.enqueueUpsertMessage(
                context,
                objectMapper,
                List.of(TestUtil.getMockDebtPosition()),
                builder,
                0,
                0
        );

        assertFalse(result);
    }

    @Test
    void enqueueDeleteMessageSplitsMessagesByChunkSize() throws Exception {
        QueueMessage.QueueMessageBuilder builder = queueService.generateMessageBuilder(
                CRUDOperation.DELETE,
                "key",
                "orgFiscalCode",
                "brokerCode",
                ServiceType.GPD
        );

        List<String> iupds = List.of(
                "IUPD01", "IUPD02", "IUPD03", "IUPD04", "IUPD05",
                "IUPD06", "IUPD07", "IUPD08", "IUPD09", "IUPD10",
                "IUPD11", "IUPD12", "IUPD13", "IUPD14", "IUPD15",
                "IUPD16", "IUPD17", "IUPD18", "IUPD19", "IUPD20",
                "IUPD21"
        );

        boolean result = queueService.enqueueDeleteMessage(
                context,
                objectMapper,
                iupds,
                builder,
                0
        );

        assertTrue(result);

        verify(cloudQueue, times(2)).addMessage(
                any(CloudQueueMessage.class),
                eq(0),
                eq(0),
                isNull(QueueRequestOptions.class),
                isNull(OperationContext.class)
        );
    }

    @Test
    void enqueueUpsertMessageSplitsMessagesByCustomChunkSize() throws Exception {
        QueueMessage.QueueMessageBuilder builder = queueService.generateMessageBuilder(
                CRUDOperation.UPDATE,
                "key",
                "orgFiscalCode",
                "brokerCode",
                ServiceType.GPD
        );

        List<PaymentPosition> paymentPositions = List.of(
                TestUtil.getMockDebtPosition(),
                TestUtil.getMockDebtPosition()
        );

        boolean result = queueService.enqueueUpsertMessage(
                context,
                objectMapper,
                paymentPositions,
                builder,
                0,
                1
        );

        assertTrue(result);

        verify(cloudQueue, times(2)).addMessage(
                any(CloudQueueMessage.class),
                eq(0),
                eq(0),
                isNull(QueueRequestOptions.class),
                isNull(OperationContext.class)
        );
    }

    @Test
    void enqueueDeleteMessageReturnsFalseWhenSerializationFails() throws Exception {
        ObjectMapper failingObjectMapper = mock(ObjectMapper.class);

        when(failingObjectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialization error") {
					private static final long serialVersionUID = 1L;});

        QueueMessage.QueueMessageBuilder builder = queueService.generateMessageBuilder(
                CRUDOperation.DELETE,
                "key",
                "orgFiscalCode",
                "brokerCode",
                ServiceType.GPD
        );

        boolean result = queueService.enqueueDeleteMessage(
                context,
                failingObjectMapper,
                List.of("IUPD1"),
                builder,
                0
        );

        assertFalse(result);
    }

    @Test
    void enqueueUpsertMessageReturnsFalseWhenSerializationFails() throws Exception {
        ObjectMapper failingObjectMapper = mock(ObjectMapper.class);

        when(failingObjectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialization error") {
					private static final long serialVersionUID = 1L;});

        QueueMessage.QueueMessageBuilder builder = queueService.generateMessageBuilder(
                CRUDOperation.UPDATE,
                "key",
                "orgFiscalCode",
                "brokerCode",
                ServiceType.GPD
        );

        boolean result = queueService.enqueueUpsertMessage(
                context,
                failingObjectMapper,
                List.of(TestUtil.getMockDebtPosition()),
                builder,
                0,
                null
        );

        assertFalse(result);
    }

    @Test
    void enqueueUpsertMessageReducesChunkSizeWhenMessageExceedsQueueLimit() throws Exception {
        ObjectMapper customObjectMapper = mock(ObjectMapper.class);

        String oversizedMessage = "x".repeat(65 * 1024);
        String validMessage = objectMapper.writeValueAsString(
                queueService.generateMessageBuilder(
                                CRUDOperation.UPDATE,
                                "key",
                                "orgFiscalCode",
                                "brokerCode",
                                ServiceType.GPD
                        )
                        .paymentPositions(List.of(TestUtil.getMockDebtPosition()))
                        .build()
        );

        when(customObjectMapper.writeValueAsString(any()))
                .thenReturn(oversizedMessage)
                .thenReturn(validMessage)
                .thenReturn(validMessage);

        QueueMessage.QueueMessageBuilder builder = queueService.generateMessageBuilder(
                CRUDOperation.UPDATE,
                "key",
                "orgFiscalCode",
                "brokerCode",
                ServiceType.GPD
        );

        boolean result = queueService.enqueueUpsertMessage(
                context,
                customObjectMapper,
                List.of(
                        TestUtil.getMockDebtPosition(),
                        TestUtil.getMockDebtPosition()
                ),
                builder,
                0,
                2
        );

        assertTrue(result);

        verify(cloudQueue, times(2)).addMessage(
                any(CloudQueueMessage.class),
                eq(0),
                eq(0),
                isNull(QueueRequestOptions.class),
                isNull(OperationContext.class)
        );
    }
}