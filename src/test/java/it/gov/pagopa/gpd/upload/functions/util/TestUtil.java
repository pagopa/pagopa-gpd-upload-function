package it.gov.pagopa.gpd.upload.functions.util;

import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.entity.PaymentPositionsMessage;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.entity.Upload;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.model.RetryStep;
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

    public static PaymentPositions getMockInvalidDebtPositions() {
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

    public static PaymentPosition getMockInvalidDebtPosition() {
        return PaymentPosition.builder()
                       .iupd("IUPD_77777777777_92bd6")
                       .type(Type.F)
                       .fiscalCode("77777777777")
                       .fullName("Mario Rossi")
                       .email("invalid-email")
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

    public static String getMockBlobCreatedEvent() {
        String event = "{\"topic\":\"topic\"," +
                      "\"subject\":\"/blobServices/default/containers/broker0001/blobs/ec0001/input/77777777777f3d1.json\"," +
                      "\"eventType\":\"Microsoft.Storage.BlobCreated\",\"id\":\"id-test\"," +
                      "\"data\":{\"api\":\"PutBlob\",\"clientRequestId\":\"client-request-id-test\"," +
                      "\"requestId\":\"request-id-test\",\"eTag\":\"0x0\"," +
                      "\"contentType\":\"application/json\",\"contentLength\":1," +
                      "\"blobType\":\"BlockBlob\",\"blobUrl\":\"blob-url-test\"," +
                      "\"url\":\"url-test\"," +
                      "\"sequencer\":\"sequencer-test\",\"identity\":\"identity-test\"," +
                      "\"storageDiagnostics\":{\"batchId\":\"batch-id-test\"}},\"dataVersion\":\"\"," +
                      "\"metadataVersion\":\"1\",\"eventTime\":\"2024-02-07T14:11:36.0505464Z\"}";
        return event;
    }

    public static Status getMockStatus() {
        return Status.builder()
                       .id("id")
                       .fiscalCode("fiscalCode")
                       .brokerID("brokerId")
                       .upload(Upload.builder().build())
                       .build();
    }

    public static ResponseGPD getMockResponseGPD() {
        ResponseGPD responseGPD = ResponseGPD.builder()
                                         .retryStep(RetryStep.DONE)
                                         .detail("detail")
                                         .status(HttpStatus.OK.value())
                                         .build();
        return responseGPD;
    }

    public static PaymentPositionsMessage getMockPaymentPositionsMessage() {
        PaymentPositionsMessage message = PaymentPositionsMessage.builder()
                                                  .uploadKey("uploadKey")
                                                  .brokerCode("brokerCode")
                                                  .organizationFiscalCode("organizationFiscalCode")
                                                  .retryCounter(0)
                                                  .paymentPositions(TestUtil.getMockDebtPositions())
                                                  .build();
        return message;
    }
}
