package it.gov.pagopa.gpd.upload.functions.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.gov.pagopa.gpd.upload.entity.DeleteMessage;
import it.gov.pagopa.gpd.upload.entity.UpsertMessage;
import it.gov.pagopa.gpd.upload.functions.util.TestUtil;
import it.gov.pagopa.gpd.upload.model.RequestGPD;
import it.gov.pagopa.gpd.upload.model.pd.MultipleIUPD;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;
import it.gov.pagopa.gpd.upload.service.RequestTranslator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

public class RequestTranslatorTest {

    @Test
    void testDeleteSingle() throws JsonProcessingException {
        RequestTranslator requestTranslator = RequestTranslator.getInstance();
        DeleteMessage deleteMessage = new DeleteMessage(TestUtil.getDeleteQueueMessage());
        RequestGPD requestGPD = requestTranslator.create(deleteMessage, RequestGPD.Mode.SINGLE, Optional.of("IUPD-1"));

        String expectedBody = new ObjectMapper().writeValueAsString(new MultipleIUPD(deleteMessage.getPaymentPositionIUPDs()));
        Assertions.assertEquals(expectedBody, requestGPD.getBody());
    }

    @Test
    void testDeleteBulk() throws JsonProcessingException {
        RequestTranslator requestTranslator = RequestTranslator.getInstance();
        DeleteMessage deleteMessage = new DeleteMessage(TestUtil.getDeleteQueueMessage());
        RequestGPD requestGPD = requestTranslator.create(deleteMessage, RequestGPD.Mode.BULK, Optional.ofNullable(null));

        String expectedBody = new ObjectMapper().writeValueAsString(new MultipleIUPD(deleteMessage.getPaymentPositionIUPDs()));
        Assertions.assertEquals(expectedBody, requestGPD.getBody());
    }

    @Test
    void testUpsertSingle() throws JsonProcessingException {
        RequestTranslator requestTranslator = RequestTranslator.getInstance();
        UpsertMessage upsertMessage = new UpsertMessage(TestUtil.getCreateQueueMessage());
        RequestGPD requestGPD = requestTranslator.create(upsertMessage, RequestGPD.Mode.SINGLE, Optional.of("IUPD_77777777777_92bd6"));

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        String expectedBody = objectMapper.writeValueAsString(new PaymentPositions(upsertMessage.getPaymentPositions()));
        Assertions.assertEquals(expectedBody, requestGPD.getBody());
    }

    @Test
    void testUpsertBulk() throws JsonProcessingException {
        RequestTranslator requestTranslator = RequestTranslator.getInstance();
        UpsertMessage upsertMessage = new UpsertMessage(TestUtil.getCreateQueueMessage());
        RequestGPD requestGPD = requestTranslator.create(upsertMessage, RequestGPD.Mode.BULK, Optional.ofNullable(null));

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        String expectedBody = objectMapper.writeValueAsString(new PaymentPositions(upsertMessage.getPaymentPositions()));
        Assertions.assertEquals(expectedBody, requestGPD.getBody());
    }
}
