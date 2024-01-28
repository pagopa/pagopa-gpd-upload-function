package it.gov.pagopa.gpd.upload.model.pd;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TransferMetadata implements Serializable {

    
    /**
	 * generated serialVersionUID
	 */
	private static final long serialVersionUID = -1509450417943158597L;

	@NotBlank(message = "key is required")
    private String key;

    private String value;

}
