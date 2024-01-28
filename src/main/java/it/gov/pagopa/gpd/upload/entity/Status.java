package it.gov.pagopa.gpd.upload.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.*;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonSerialize
@ToString
public class Status {
    public String id;
    public String brokerID;
    public String fiscalCode;
    public Upload upload;
}
