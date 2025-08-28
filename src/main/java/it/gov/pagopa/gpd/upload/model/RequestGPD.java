package it.gov.pagopa.gpd.upload.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import it.gov.pagopa.gpd.upload.model.enumeration.ServiceType;
import lombok.*;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RequestGPD {
    private Mode mode;
    private String orgFiscalCode;
    private String body;
    private ServiceType serviceType;

    public enum Mode {
        BULK,
        SINGLE
    }
}
