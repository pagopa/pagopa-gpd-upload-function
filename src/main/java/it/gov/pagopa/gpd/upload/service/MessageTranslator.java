package it.gov.pagopa.gpd.upload.service;

import it.gov.pagopa.gpd.upload.entity.DeleteMessage;
import it.gov.pagopa.gpd.upload.entity.UpsertMessage;
import it.gov.pagopa.gpd.upload.model.QueueMessage;

import java.util.List;

public class MessageTranslator {
    private static MessageTranslator instance;

    public MessageTranslator() {
    }

    public static MessageTranslator getInstance() {
        if (instance == null) {
            instance = new MessageTranslator();
        }
        return instance;
    }

    public QueueMessage translate(UpsertMessage upsertMessage, List<String> filterByIUPD) {
        return QueueMessage.builder()
                       .crudOperation(upsertMessage.getCrudOperation())
                       .uploadKey(upsertMessage.getUploadKey())
                       .organizationFiscalCode(upsertMessage.getOrganizationFiscalCode())
                       .brokerCode(upsertMessage.getBrokerCode())
                       .retryCounter(upsertMessage.getRetryCounter())
                       .paymentPositions(upsertMessage.getPaymentPositions().stream().
                                                 filter(pp -> filterByIUPD.contains(pp.getIupd())).toList()).build();
    }

    public QueueMessage translate(DeleteMessage upsertMessage, List<String> filterByIUPD) {
        return QueueMessage.builder()
                       .crudOperation(upsertMessage.getCrudOperation())
                       .uploadKey(upsertMessage.getUploadKey())
                       .organizationFiscalCode(upsertMessage.getOrganizationFiscalCode())
                       .brokerCode(upsertMessage.getBrokerCode())
                       .retryCounter(upsertMessage.getRetryCounter())
                       .paymentPositionIUPDs(filterByIUPD).build();
    }
}
