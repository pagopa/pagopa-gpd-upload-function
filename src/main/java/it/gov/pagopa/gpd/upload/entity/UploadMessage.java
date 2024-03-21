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
    private Operation operation;
    private String uploadKey;
    private String organizationFiscalCode;
    private String brokerCode;
    private Integer retryCounter;
    private PaymentPositions paymentPositions;
}
