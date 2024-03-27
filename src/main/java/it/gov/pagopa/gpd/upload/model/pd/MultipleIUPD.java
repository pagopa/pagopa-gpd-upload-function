package it.gov.pagopa.gpd.upload.model.pd;

import it.gov.pagopa.gpd.upload.model.ModelGPD;
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
public class MultipleIUPD implements ModelGPD {
    @Valid
    private List<String> paymentPositionIUPDs;
}
