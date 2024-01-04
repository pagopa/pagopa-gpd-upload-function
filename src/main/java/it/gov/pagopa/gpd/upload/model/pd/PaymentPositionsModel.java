package it.gov.pagopa.gpd.upload.model.pd;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;
import java.util.List;

@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class PaymentPositionsModel {
    @Valid
    private List<@Valid PaymentPositionModel> paymentPositions;

    public @Valid List<@Valid PaymentPositionModel> getPaymentPositions() {
        return this.paymentPositions;
    }
}
