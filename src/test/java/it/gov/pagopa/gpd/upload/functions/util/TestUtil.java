package it.gov.pagopa.gpd.upload.functions.util;

import it.gov.pagopa.gpd.upload.model.pd.PaymentOptionModel;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositionModel;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositionsModel;
import it.gov.pagopa.gpd.upload.model.pd.TransferModel;
import it.gov.pagopa.gpd.upload.model.pd.enumeration.Type;
import lombok.experimental.UtilityClass;

import java.time.LocalDateTime;
import java.util.List;

@UtilityClass
public class TestUtil {

    public static PaymentPositionsModel getMockDebtPositions() {
        return PaymentPositionsModel.builder()
                .paymentPositions(List.of(getMockDebtPosition()))
                .build();
    }

    public static PaymentPositionModel getMockDebtPosition() {
        return PaymentPositionModel.builder()
                .iupd("IUPD_77777777777_92bd6")
                .type(Type.F)
                .fiscalCode("77777777777")
                .fullName("Mario Rossi")
                .paymentOption(List.of(getMockPaymentOption()))
                .build();
    }

    public static PaymentOptionModel getMockPaymentOption() {
        return PaymentOptionModel.builder()
                .iuv("IUV_77777777777_92bd6")
                .amount(100L)
                .isPartialPayment(false)
                .dueDate(LocalDateTime.now().plusYears(1L))
                .fee(0L)
                .notificationFee(0L)
                .transfer(List.of(getMockTransfer()))
                .build();
    }

    public static TransferModel getMockTransfer() {
        return TransferModel.builder()
                .idTransfer("1")
                .amount(100L)
                .remittanceInformation("remittanceInformation")
                .category("categoryXZ")
                .iban("IT0000000000000000000000000")
                .build();
    }
}
