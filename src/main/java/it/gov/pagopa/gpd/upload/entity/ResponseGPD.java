package it.gov.pagopa.gpd.upload.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.gov.pagopa.gpd.upload.model.RetryStep;
import lombok.*;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResponseGPD {
    private RetryStep retryStep;
    @JsonProperty("detail")
    private String detail;
    @JsonProperty("status")
    private int status;

    public void setRetryStep(RetryStep retryStep) {
        this.retryStep = retryStep;
    }
}
