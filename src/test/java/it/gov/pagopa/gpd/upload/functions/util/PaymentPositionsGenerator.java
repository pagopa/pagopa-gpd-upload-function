package it.gov.pagopa.gpd.upload.functions.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.entity.Upload;
import it.gov.pagopa.gpd.upload.model.pd.PaymentOption;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;
import it.gov.pagopa.gpd.upload.model.pd.Transfer;
import it.gov.pagopa.gpd.upload.model.pd.enumeration.Type;
import it.gov.pagopa.gpd.upload.util.MapUtils;

import java.io.IOException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class PaymentPositionsGenerator {
    public static void main(String[] args) throws IOException {
        int N = 9; // debt position number target
        String fiscalCode = "77_UPLOAD";
        List<PaymentPosition> paymentPositionList = new ArrayList<>();
        for(int i = 0; i < N; i++) {
            String ID = fiscalCode + "_" + UUID.randomUUID().toString().substring(0,5);
            Transfer tf = Transfer.builder()
                                       .idTransfer("1")
                                       .amount(100L)
                                       .remittanceInformation(UUID.randomUUID().toString().substring(0, 10))
                                       .category("categoryXZ")
                                       .iban("IT0000000000000000000000000")
                                       .transferMetadata(new ArrayList<>())
                                       .build();
            List<Transfer> transferList = new ArrayList<Transfer>();
            transferList.add(tf);
            ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(Instant.now().plus(1, ChronoUnit.DAYS), ZoneId.of("UTC"));
            PaymentOption po = PaymentOption.builder()
                                            .iuv("IUV_UPLOAD_" + ID)
                                            .amount(100L)
                                            .isPartialPayment(false)
                                            .dueDate(zonedDateTime.toLocalDateTime())
                                            .transfer(transferList)
                                            .paymentOptionMetadata(new ArrayList<>())
                                            .build();
            List<PaymentOption> paymentOptionList = new ArrayList<PaymentOption>();
            paymentOptionList.add(po);
            PaymentPosition pp = PaymentPosition.builder()
                                              .iupd("IUPD_UPLOAD_" + ID)
                                              .type(Type.F)
                                              .fiscalCode(fiscalCode)
                                              //.email("email")
                                              .fullName(UUID.randomUUID().toString().substring(0, 4))
                                              .companyName(UUID.randomUUID().toString().substring(0, 4))
                                              .paymentOption(paymentOptionList)
                                              .switchToExpired(false)
                                              .build();
            paymentPositionList.add(pp);
        }

        PaymentPositions paymentPositions = PaymentPositions.builder()
                                                         .paymentPositions(paymentPositionList)
                                                         .build();

        ObjectMapper objectMapper = new ObjectMapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.registerModule(javaTimeModule);

//        String jsonPP = objectMapper.writeValueAsString(paymentPositions);
//        String extender = UUID.randomUUID().toString().substring(0, 4);
//        FileWriter fileWriter = new FileWriter("77777777777" + extender + ".json");
//        fileWriter.write(jsonPP);
//        fileWriter.close();

        Upload upload = Upload.builder()
                                .current(0)
                                .total(0)
                                .start(LocalDateTime.now())
                                .end(LocalDateTime.now())
                                .responses(null)
                                .build();
        Status status = Status.builder()
                                .id("id")
                                .brokerID("id")
                                .fiscalCode("fc")
                                .upload(upload)
                                .build();
        String jsonUploadReport = objectMapper.writeValueAsString(MapUtils.convert(status));
        System.out.println(jsonUploadReport);
    }
}
