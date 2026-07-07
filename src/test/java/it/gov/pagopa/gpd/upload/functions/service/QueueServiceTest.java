package it.gov.pagopa.gpd.upload.functions.service;

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
                List.of(new String[]{"IUPD1"}),
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
                List.of(new String[]{"IUPD1"}),
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
}