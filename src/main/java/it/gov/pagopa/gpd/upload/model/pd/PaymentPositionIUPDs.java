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
public class PaymentPositionIUPDs implements ModelGPD<PaymentPositionIUPDs> {
    @Valid
    private List<String> paymentPositionIUDPs;

    public @Valid List<String> getPaymentPositionIUDPs() {
        return this.paymentPositionIUDPs;
    }

    @Override
    public List<String> getIUPD() {
        return paymentPositionIUDPs;
    }

    @Override
    public PaymentPositionIUPDs filterById(List<String> ids) {
        return new PaymentPositionIUPDs();
    }
}
