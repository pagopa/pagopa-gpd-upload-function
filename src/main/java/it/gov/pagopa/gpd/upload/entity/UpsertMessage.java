package it.gov.pagopa.gpd.upload.entity;

import it.gov.pagopa.gpd.upload.model.QueueMessage;
import it.gov.pagopa.gpd.upload.model.RequestGPD;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import it.gov.pagopa.gpd.upload.service.MessageTranslator;
import it.gov.pagopa.gpd.upload.service.RequestTranslator;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
@Setter
public class UpsertMessage extends DebtPositionMessage {
    private List<PaymentPosition> paymentPositions;

    public UpsertMessage(QueueMessage m) {
        super(m.getCrudOperation(), m.getUploadKey(), m.getOrganizationFiscalCode(), m.getBrokerCode(), m.getRetryCounter());
        this.paymentPositions = m.getPaymentPositions();
    }

    @Override
    public List<String> getIUPDList() {
        return paymentPositions.stream()
                .map(PaymentPosition::getIupd)
                .collect(Collectors.toList());
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
