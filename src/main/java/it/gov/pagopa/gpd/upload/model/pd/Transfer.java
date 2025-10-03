package it.gov.pagopa.gpd.upload.model.pd;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Transfer implements Serializable {

    /**
     * generated serialVersionUID
     */
    private static final long serialVersionUID = 5593063492841435180L;

    @NotBlank(message = "id transfer is required")
    private String idTransfer;

    @NotNull(message = "amount is required")
    private Long amount;

    private String organizationFiscalCode;

    @NotBlank(message = "remittance information is required")
    private String remittanceInformation; // causale

    @NotBlank(message = "category is required")
    private String category; // taxonomy

    private String iban;

    private String postalIban;

    private Stamp stamp;

    private String companyName;

    @Valid
    @Size(min=0, max=10)
    private List<TransferMetadata> transferMetadata = new ArrayList<>();

    public void addTransferMetadata(TransferMetadata trans) {
    	transferMetadata.add(trans);
    }

    public void removeTransferMetadata(TransferMetadata trans) {
    	transferMetadata.remove(trans);
    }

}
