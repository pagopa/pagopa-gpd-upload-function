package it.gov.pagopa.gpd.upload.model;

import it.gov.pagopa.gpd.upload.model.enumeration.ServiceType;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import lombok.*;

import java.util.List;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QueueMessage {
    private CRUDOperation crudOperation;
    private String uploadKey;
    private String organizationFiscalCode;
    private String brokerCode;
    private Integer retryCounter;
    private List<PaymentPosition> paymentPositions;
    private List<String> paymentPositionIUPDs;
    private ServiceType serviceType;
}
