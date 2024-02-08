package it.gov.pagopa.gpd.upload.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResponseGPD {
    @JsonIgnore
    private RetryStep retryStep;
    private String detail;
    private int status;

    public void setRetryStep(RetryStep retryStep) {
        this.retryStep = retryStep;
    }
}
