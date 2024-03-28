package it.gov.pagopa.gpd.upload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.gov.pagopa.gpd.upload.entity.DeleteMessage;
import it.gov.pagopa.gpd.upload.entity.UpsertMessage;
import it.gov.pagopa.gpd.upload.model.RequestGPD;
import it.gov.pagopa.gpd.upload.model.pd.MultipleIUPD;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;

import java.util.List;
import java.util.Optional;

public class RequestTranslator {
    private static RequestTranslator instance;

    public RequestTranslator() {
    }

    public static RequestTranslator getInstance() {
        if (instance == null) {
            instance = new RequestTranslator();
        }
        return instance;
    }

    public RequestGPD create(UpsertMessage upsertMessage, RequestGPD.Mode mode, Optional<String> IUPD) {
        return switch (mode) {
            case BULK -> generateRequest(RequestGPD.Mode.BULK, upsertMessage.getOrganizationFiscalCode(),
                    new PaymentPositions(upsertMessage.getPaymentPositions()));
            case SINGLE -> generateRequest(RequestGPD.Mode.SINGLE, upsertMessage.getOrganizationFiscalCode(),
                            new PaymentPositions(upsertMessage.getPaymentPositions()
                                                         .stream()
                                                         .filter(pp -> pp.getIupd().equals(IUPD.get())).toList()));
        };
    }

    public RequestGPD create(DeleteMessage deleteMessage, RequestGPD.Mode mode, Optional<String> IUPD) {
        return switch (mode) {
            case BULK -> generateRequest(RequestGPD.Mode.BULK, deleteMessage.getOrganizationFiscalCode(),
                    new MultipleIUPD(deleteMessage.getPaymentPositionIUPDs()));
            case SINGLE -> generateRequest(RequestGPD.Mode.SINGLE, deleteMessage.getOrganizationFiscalCode(),
                    new MultipleIUPD(List.of(new String[]{IUPD.get()})));
        };
    }

    private RequestGPD generateRequest(RequestGPD.Mode mode, String orgFiscalCode, PaymentPositions paymentPositions) {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        try {
            return RequestGPD.builder()
                           .mode(mode)
                           .orgFiscalCode(orgFiscalCode)
                           .body(om.writeValueAsString(paymentPositions))
                           .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private RequestGPD generateRequest(RequestGPD.Mode mode, String orgFiscalCode, MultipleIUPD multipleIUPD) {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        try {
            return RequestGPD.builder()
                           .mode(mode)
                           .orgFiscalCode(orgFiscalCode)
                           .body(om.writeValueAsString(multipleIUPD))
                           .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
