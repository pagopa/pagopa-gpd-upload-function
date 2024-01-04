package it.gov.pagopa.gpd.upload.model.pd;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TransferModel implements Serializable {

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
    
    @Valid
    @Size(min=0, max=10)
    private List<TransferMetadataModel> transferMetadata = new ArrayList<>();

    public void addTransferMetadata(TransferMetadataModel trans) {
    	transferMetadata.add(trans);
    }

    public void removeTransferMetadata(TransferMetadataModel trans) {
    	transferMetadata.remove(trans);
    }

}
