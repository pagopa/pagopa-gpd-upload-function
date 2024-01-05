package it.gov.pagopa.gpd.upload.entity;

import lombok.*;

import java.util.ArrayList;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FailedIUPD {
    private String IUPD;
    private int errorCode;
    private String details;
    private ArrayList<String> skippedIUPDs;
}