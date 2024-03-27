package it.gov.pagopa.gpd.upload.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.logging.Logger;

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
    private Logger logger;
    private String invocationId;

    public enum Mode {
        BULK,
        SINGLE
    }
}
