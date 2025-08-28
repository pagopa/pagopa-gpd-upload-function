package it.gov.pagopa.gpd.upload.entity;

import it.gov.pagopa.gpd.upload.model.CRUDOperation;
import it.gov.pagopa.gpd.upload.model.QueueMessage;
import it.gov.pagopa.gpd.upload.model.RequestGPD;
import it.gov.pagopa.gpd.upload.model.enumeration.ServiceType;
import it.gov.pagopa.gpd.upload.service.MessageTranslator;
import it.gov.pagopa.gpd.upload.service.RequestTranslator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Getter
@Setter
public abstract class DebtPositionMessage {
    private CRUDOperation crudOperation;
    private String uploadKey;
    private String organizationFiscalCode;
    private String brokerCode;
    private Integer retryCounter;
    private ServiceType serviceType;

    public abstract List<String> getIUPDList();

    public abstract RequestGPD getRequest(RequestTranslator requestTranslator, RequestGPD.Mode mode, Optional<String> optIUPD);

    public abstract QueueMessage getQueueMessage(MessageTranslator messageTranslator, List<String> filterByIUPD);
}
