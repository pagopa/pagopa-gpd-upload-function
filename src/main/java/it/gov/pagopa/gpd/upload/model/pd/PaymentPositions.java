package it.gov.pagopa.gpd.upload.model.pd;

import it.gov.pagopa.gpd.upload.model.ModelGPD;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.stream.Collectors;

@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class PaymentPositions implements ModelGPD {
    @Valid
    private List<@Valid PaymentPosition> paymentPositions;

    public @Valid List<@Valid PaymentPosition> getPaymentPositions() {
        return this.paymentPositions;
    }

    public PaymentPositions filterById(List<String> ids) {
        return new PaymentPositions();
    }
}
