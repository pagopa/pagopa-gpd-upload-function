package it.gov.pagopa.gpd.upload.model;

import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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
    private CRUDOperation operation;
    @Valid
    private List<@Valid PaymentPosition> paymentPositions;

    @Size(max = 100)
    private List<String> paymentPositionIUPDs;

    public @Valid List<@Valid PaymentPosition> getPaymentPositions() {
        return this.paymentPositions;
    }

    public @Valid List<String> getPaymentPositionIUPDs() {
        return this.paymentPositionIUPDs;
    }

    public boolean validOneOf() {
        // check if both list are not null, in this case one of requirement is not met
        boolean xorNotNull = paymentPositions != null ^ paymentPositionIUPDs != null;
        boolean wrongDeleteMapping = CRUDOperation.DELETE.equals(operation) && paymentPositionIUPDs == null;
        boolean wrongCreateUpdateMapping = !CRUDOperation.DELETE.equals(operation) && paymentPositions == null;

        return xorNotNull && !wrongDeleteMapping && !wrongCreateUpdateMapping;
    }
}
