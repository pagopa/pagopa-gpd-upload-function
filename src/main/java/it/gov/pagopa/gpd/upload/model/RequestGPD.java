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
public class RequestGPD<T extends ModelGPD> {
    private Mode mode;
    private String orgFiscalCode;
    private T body;
    private Logger logger;
    private String invocationId;

    public enum Mode {
        BULK,
        SINGLE
    }
}
