package it.gov.pagopa.gpd.upload.entity;

import it.gov.pagopa.gpd.upload.model.QueueMessage;
import it.gov.pagopa.gpd.upload.model.RequestGPD;
import it.gov.pagopa.gpd.upload.service.MessageTranslator;
import it.gov.pagopa.gpd.upload.service.RequestTranslator;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Optional;

@Getter
@Setter
public class DeleteMessage extends DebtPositionMessage {
    private List<String> paymentPositionIUPDs;

    public DeleteMessage(QueueMessage m) {
        super(m.getCrudOperation(), m.getUploadKey(), m.getOrganizationFiscalCode(), m.getBrokerCode(), m.getRetryCounter(), m.getServiceType());
        this.paymentPositionIUPDs = m.getPaymentPositionIUPDs();
    }

    @Override
    public List<String> getIUPDList() {
        return paymentPositionIUPDs;
    }

    @Override
    public RequestGPD getRequest(RequestTranslator requestTranslator, RequestGPD.Mode mode, Optional<String> optIUPD) {
        return requestTranslator.create(this, mode, optIUPD);
    }

    @Override
    public QueueMessage getQueueMessage(MessageTranslator messageTranslator, List<String> filterByIUPD) {
        return messageTranslator.translate(this, filterByIUPD);
    }
}
