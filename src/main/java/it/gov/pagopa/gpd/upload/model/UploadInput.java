package it.gov.pagopa.gpd.upload.model;

import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class UploadInput {
    private Operation operation;
    @Valid
    private PaymentPositions paymentPositions;
}
