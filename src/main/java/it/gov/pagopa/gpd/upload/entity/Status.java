package it.gov.pagopa.gpd.upload.entity;

import lombok.*;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Status {
    public String id;
    public String brokerID;
    public String fiscalCode;
    public Upload upload;
}
