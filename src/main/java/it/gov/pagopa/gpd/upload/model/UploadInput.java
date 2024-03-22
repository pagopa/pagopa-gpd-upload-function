package it.gov.pagopa.gpd.upload.model;

import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class UploadInput {
    private UploadOperation uploadOperation;
    @Valid
    private List<@Valid PaymentPosition> paymentPositions;

    public @Valid List<@Valid PaymentPosition> getPaymentPositions() {
        return this.paymentPositions;
    }
}
