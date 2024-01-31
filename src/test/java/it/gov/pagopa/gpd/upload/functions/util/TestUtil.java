package it.gov.pagopa.gpd.upload.functions.util;

import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.gpd.upload.model.pd.PaymentOption;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;
import it.gov.pagopa.gpd.upload.model.pd.Transfer;
import it.gov.pagopa.gpd.upload.model.pd.enumeration.Type;
import lombok.experimental.UtilityClass;

import java.time.LocalDateTime;
import java.util.List;

@UtilityClass
public class TestUtil {

    public static PaymentPositions getMockDebtPositions() {
        return PaymentPositions.builder()
                .paymentPositions(List.of(getMockDebtPosition()))
                .build();
    }

    public static PaymentPosition getMockDebtPosition() {
        return PaymentPosition.builder()
                .iupd("IUPD_77777777777_92bd6")
                .type(Type.F)
                .fiscalCode("77777777777")
                .fullName("Mario Rossi")
                .paymentOption(List.of(getMockPaymentOption()))
                .build();
    }

    public static PaymentOption getMockPaymentOption() {
        return PaymentOption.builder()
                .iuv("IUV_77777777777_92bd6")
                .amount(100L)
                .isPartialPayment(false)
                .dueDate(LocalDateTime.now().plusYears(1L))
                .fee(0L)
                .notificationFee(0L)
                .transfer(List.of(getMockTransfer()))
                .build();
    }

    public static Transfer getMockTransfer() {
        return Transfer.builder()
                .idTransfer("1")
                .amount(100L)
                .remittanceInformation("remittanceInformation")
                .category("categoryXZ")
                .iban("IT0000000000000000000000000")
                .build();
    }

    public static OutputBinding<String> getMockOutputBinding() {
        return new OutputBinding<String>() {
            @Override
            public String getValue() {
                return null;
            }

            @Override
            public void setValue(String value) {

            }
        };
    }
}
