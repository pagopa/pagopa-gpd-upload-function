package it.gov.pagopa.gpd.upload.entity;

import it.gov.pagopa.gpd.upload.model.RetryStep;
import lombok.*;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResponseGPD {
    private RetryStep retryStep;
    private String message;
    private int status;
}
