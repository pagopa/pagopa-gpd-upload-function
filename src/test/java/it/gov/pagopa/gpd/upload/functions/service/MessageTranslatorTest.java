package it.gov.pagopa.gpd.upload.functions.service;

import it.gov.pagopa.gpd.upload.entity.DeleteMessage;
import it.gov.pagopa.gpd.upload.entity.UpsertMessage;
import it.gov.pagopa.gpd.upload.functions.utils.TestUtil;
import it.gov.pagopa.gpd.upload.model.CRUDOperation;
import it.gov.pagopa.gpd.upload.model.QueueMessage;
import it.gov.pagopa.gpd.upload.service.MessageTranslator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;


public class MessageTranslatorTest {

    @Test
    void testDelete() {
        MessageTranslator msgTranslator = MessageTranslator.getInstance();
        DeleteMessage deleteMessage = new DeleteMessage(TestUtil.getDeleteQueueMessage());
        QueueMessage queueMessage = msgTranslator.translate(deleteMessage, List.of("IUPD-1"));

        Assertions.assertEquals(queueMessage.getUploadKey(), "mock");
    }

    @Test
    void testUpsert() {
        MessageTranslator msgTranslator = MessageTranslator.getInstance();
        QueueMessage queueMessageInput = TestUtil.getCreateQueueMessage();
        UpsertMessage upsertMessage = new UpsertMessage(queueMessageInput);
        QueueMessage queueMessage = msgTranslator.translate(upsertMessage, List.of(queueMessageInput.getPaymentPositions().stream().findFirst().get().getIupd()));

        Assertions.assertEquals(queueMessage.getUploadKey(), "mock");
    }
}
