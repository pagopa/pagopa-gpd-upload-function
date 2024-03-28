package it.gov.pagopa.gpd.upload.functions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.gpd.upload.functions.utils.TestUtil;
import it.gov.pagopa.gpd.upload.model.CRUDOperation;
import it.gov.pagopa.gpd.upload.model.QueueMessage;
import it.gov.pagopa.gpd.upload.service.QueueService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.List;
import java.util.logging.Logger;

import static org.mockito.Mockito.when;

public class QueueServiceTest {
    private final ExecutionContext context = Mockito.mock(ExecutionContext.class);

    @Test
    void testUpdateQueueMessage() {
        when(context.getLogger()).thenReturn(Logger.getLogger("gpd-upload-test-logger"));
        QueueMessage.QueueMessageBuilder builder = QueueService.getInstance().generateMessageBuilder(CRUDOperation.UPDATE, "key", "orgFiscalCode", "brokerCode");
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        Assertions.assertTrue(new EQueueService().enqueueUpsertMessage(context, om, List.of(TestUtil.getMockDebtPosition()), builder, 0));
    }

    @Test
    void testDeleteQueueMessage() {
        when(context.getLogger()).thenReturn(Logger.getLogger("gpd-upload-test-logger"));
        QueueMessage.QueueMessageBuilder builder = QueueService.getInstance().generateMessageBuilder(CRUDOperation.DELETE, "key", "orgFiscalCode", "brokerCode");
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        Assertions.assertTrue(new EQueueService().enqueueDeleteMessage(context, om, List.of("IUPD-1"), builder, 0));
    }

    public class EQueueService extends QueueService {
        @Override
        public boolean enqueue(String invocationId, Logger logger, String message, int initialVisibilityDelayInSeconds) {
            return true;
        }
    }
}
