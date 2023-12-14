package it.gov.pagopa.gpd.upload.model.pd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import javax.validation.constraints.Pattern;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stamp implements Serializable {
    private static final long serialVersionUID = -5862140737726963810L;

    @NotBlank
    private String hashDocument;

    @NotBlank
    @Size(min = 2, max = 2)
    private String stampType;

    @NotBlank
    @Pattern(regexp = "[A-Z]{2}")
    private String provincialResidence;
}
