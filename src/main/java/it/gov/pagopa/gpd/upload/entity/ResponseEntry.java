package it.gov.pagopa.gpd.upload.entity;

import lombok.*;

import java.util.List;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResponseEntry {
    public Integer statusCode;
    public String statusMessage;
    public List<String> requestIDs; // IUPDs
}