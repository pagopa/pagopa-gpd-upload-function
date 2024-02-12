package it.gov.pagopa.gpd.upload.functions.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.gov.pagopa.gpd.upload.model.pd.PaymentOption;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;
import it.gov.pagopa.gpd.upload.model.pd.Transfer;
import it.gov.pagopa.gpd.upload.model.pd.enumeration.Type;

import java.io.FileWriter;
import java.io.IOException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class PaymentPositionsGenerator {
    public static void main(String[] args) throws IOException {
        int N = 1000; // debt position number target
        String fiscalCode = "77777777777";
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
            		                        .nav("NAV_UPLOAD_" + ID)
                                            .iuv("IUV_UPLOAD_" + ID)
                                            .description("desc_" + UUID.randomUUID().toString().substring(0, 4))
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
                                              .officeName(UUID.randomUUID().toString().substring(0, 4))
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

        String jsonPP = objectMapper.writeValueAsString(paymentPositions);
        String extender = UUID.randomUUID().toString().substring(0, 4);
        String filename = "77777777777" + extender + ".json";
        FileWriter fileWriter = new FileWriter(filename);
        fileWriter.write(jsonPP);
        fileWriter.close();

//        Upload upload = Upload.builder()
//                                .current(0)
//                                .total(0)
//                                .start(LocalDateTime.now())
//                                .end(LocalDateTime.now())
//                                .responses(null)
//                                .build();
//        Status status = Status.builder()
//                                .id("id")
//                                .brokerID("id")
//                                .fiscalCode("fc")
//                                .upload(upload)
//                                .build();
//        String jsonUploadReport = objectMapper.writeValueAsString(MapUtils.convert(status));
//        System.out.println(jsonUploadReport);
    }
}
