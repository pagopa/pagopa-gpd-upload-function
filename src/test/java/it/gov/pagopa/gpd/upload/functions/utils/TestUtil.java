package it.gov.pagopa.gpd.upload.functions.utils;

import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.entity.UploadMessage;
import it.gov.pagopa.gpd.upload.entity.ResponseEntry;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.entity.Upload;
import it.gov.pagopa.gpd.upload.model.UploadOperation;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.model.RetryStep;
import it.gov.pagopa.gpd.upload.model.UploadInput;
import it.gov.pagopa.gpd.upload.model.pd.PaymentOption;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;
import it.gov.pagopa.gpd.upload.model.pd.Transfer;
import it.gov.pagopa.gpd.upload.model.pd.enumeration.Type;
import lombok.experimental.UtilityClass;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@UtilityClass
public class TestUtil {
    public static UploadInput getMockCreateInputData() {
        return UploadInput.builder()
                .uploadOperation(UploadOperation.CREATE)
                .paymentPositions(getMockDebtPositions().getPaymentPositions())
                .build();
    }

    public static PaymentPositions getMockDebtPositions() {
        List<PaymentPosition> paymentPositionList = new ArrayList<>();
        paymentPositionList.add(getMockDebtPosition());
        return PaymentPositions.builder()
                .paymentPositions(paymentPositionList)
                .build();
    }

    public static PaymentPositions getMockInvalidDebtPositions() {
        List<PaymentPosition> paymentPositionList = new ArrayList<>();
        paymentPositionList.add(getMockInvalidDebtPosition());
        return PaymentPositions.builder()
                       .paymentPositions(paymentPositionList)
                       .build();
    }

    public static PaymentPosition getMockDebtPosition() {
        return PaymentPosition.builder()
                .iupd("IUPD_77777777777_92bd6")
                .type(Type.F)
                .fiscalCode("77777777777")
                .fullName("Mario Rossi")
                .companyName(UUID.randomUUID().toString().substring(0, 4))
                .officeName(UUID.randomUUID().toString().substring(0, 4))
                .paymentOption(List.of(getMockPaymentOption()))
                .build();
    }

    public static PaymentPosition getMockInvalidDebtPosition() {
        return PaymentPosition.builder()
                       .iupd("IUPD_77777777777_92bd6")
                       .type(Type.F)
                       .fiscalCode("77777777777")
                       .fullName("Mario Rossi")
                       .companyName(UUID.randomUUID().toString().substring(0, 4))
                       .officeName(UUID.randomUUID().toString().substring(0, 4))
                       .email("invalid-email")
                       .paymentOption(List.of(getMockPaymentOption()))
                       .build();
    }

    public static PaymentOption getMockPaymentOption() {
        return PaymentOption.builder()
        		.nav("NAV_77777777777_92bd6")
                .iuv("IUV_77777777777_92bd6")
                .amount(100L)
                .isPartialPayment(false)
                .dueDate(LocalDateTime.now().plusYears(1L))
                .fee(0L)
                .notificationFee(0L)
                .description(UUID.randomUUID().toString().substring(0, 4))
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
                       .upload(Upload.builder()
                                       .responses(getMockResponseEntries())
                                       .build())
                       .build();
    }

    public static ResponseGPD getOKMockResponseGPD() {
        ResponseGPD responseGPD = ResponseGPD.builder()
                                         .retryStep(RetryStep.DONE)
                                         .detail(HttpStatus.OK.name())
                                         .status(HttpStatus.OK.value())
                                         .build();
        return responseGPD;
    }

    public static ResponseGPD getKOMockResponseGPD() {
        ResponseGPD responseGPD = ResponseGPD.builder()
                                          .retryStep(RetryStep.RETRY)
                                          .detail(HttpStatus.INTERNAL_SERVER_ERROR.name())
                                          .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                          .build();
        return responseGPD;
    }

    public static UploadMessage getMockInputMessage(UploadOperation uploadOperation) {
        UploadMessage message = UploadMessage.builder()
                                        .uploadOperation(uploadOperation)
                                        .uploadKey("uploadKey")
                                        .brokerCode("brokerCode")
                                        .organizationFiscalCode("organizationFiscalCode")
                                        .retryCounter(0)
                                        .paymentPositions(TestUtil.getMockDebtPositions())
                                        .build();
        return message;
    }

    public static ArrayList<ResponseEntry> getMockResponseEntries() {
        ArrayList<ResponseEntry> responseEntries = new ArrayList<ResponseEntry>();
        responseEntries.add(ResponseEntry.builder()
                                    .statusCode(HttpStatus.OK.value())
                                    .requestIDs(List.of("IUPD1"))
                                    .statusMessage("status message")
                                    .build());
        return responseEntries;
    }
}
