package it.gov.pagopa.gpd.upload.entity;

import it.gov.pagopa.gpd.upload.model.ModelGPD;
import it.gov.pagopa.gpd.upload.model.UploadOperation;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;
import lombok.*;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UploadMessage<T extends ModelGPD> {
    private UploadOperation uploadOperation;
    private String uploadKey;
    private String organizationFiscalCode;
    private String brokerCode;
    private Integer retryCounter;
    private T paymentPositions;
}
