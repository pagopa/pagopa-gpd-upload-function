package it.gov.pagopa.gpd.upload.entity;

import it.gov.pagopa.gpd.upload.model.Operation;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;
import lombok.*;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UploadMessage {
    public Operation operation;
    public String uploadKey;
    public String organizationFiscalCode;
    public String brokerCode;
    public Integer retryCounter;
    public PaymentPositions paymentPositions;
}
