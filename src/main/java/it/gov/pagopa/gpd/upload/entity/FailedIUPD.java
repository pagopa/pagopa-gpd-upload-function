package it.gov.pagopa.gpd.upload.entity;

import lombok.*;

import java.util.List;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FailedIUPD {
    private int errorCode;
    private String details;
    private List<String> skippedIUPDs;
}