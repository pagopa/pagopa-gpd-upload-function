package it.gov.pagopa.gpd.upload.entity;

import it.gov.pagopa.gpd.upload.model.UploadOperation;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import lombok.*;

import java.util.List;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UploadMessage {
    private UploadOperation uploadOperation;
    private String uploadKey;
    private String organizationFiscalCode;
    private String brokerCode;
    private Integer retryCounter;
    private List<PaymentPosition> paymentPositions;
    private List<String> paymentPositionIUPDs;
}
