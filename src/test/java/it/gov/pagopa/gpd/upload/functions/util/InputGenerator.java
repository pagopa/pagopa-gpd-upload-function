package it.gov.pagopa.gpd.upload.functions.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.gov.pagopa.gpd.upload.model.CRUDOperation;
import it.gov.pagopa.gpd.upload.model.UploadInput;
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

public class InputGenerator {
    public static void main(String[] args) throws IOException {
        int N = 30; // debt position number target
        String fiscalCode = "77777777777";
        String testType = "TEST_TYPE";
        List<PaymentPosition> paymentPositionList = new ArrayList<>();
        for(int i = 0; i < N; i++) {
            String ID = fiscalCode + "_" + UUID.randomUUID().toString().substring(0,10);
            Transfer tf = Transfer.builder()
                                       .idTransfer("1")
                                       .amount(100L)
                                       .remittanceInformation(UUID.randomUUID().toString().substring(0, 10))
                                       .category("category_" + testType)
                                       .iban("IT0000000000000000000000000")
                                       .transferMetadata(new ArrayList<>())
                                       .build();
            List<Transfer> transferList = new ArrayList<>();
            transferList.add(tf);
            ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(Instant.now().plus(1, ChronoUnit.DAYS), ZoneId.of("UTC"));
            PaymentOption po = PaymentOption.builder()
                                        .iuv("IUV_" + ID + "_" + testType)
                                        .amount(100L)
                                        .isPartialPayment(false)
                                        .description("description")
                                        .dueDate(zonedDateTime.toLocalDateTime())
                                        .transfer(transferList)
                                        .paymentOptionMetadata(new ArrayList<>())
                                        .build();
            List<PaymentOption> paymentOptionList = new ArrayList<>();
            paymentOptionList.add(po);
            PaymentPosition pp = PaymentPosition.builder()
                                        .iupd("IUPD_" + ID + "_" + testType)
                                        .type(Type.F)
                                        .fiscalCode(fiscalCode)
                                        .officeName("office-name")
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

        UploadInput input = UploadInput.builder()
                                    .operation(CRUDOperation.CREATE)
                                    .paymentPositions(paymentPositions.getPaymentPositions())
                                    .build();

        ObjectMapper objectMapper = new ObjectMapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.registerModule(javaTimeModule);

        String extender = UUID.randomUUID().toString().substring(0, 4);
        String positionsJSON = objectMapper.writeValueAsString(paymentPositionList);
        String positionsFilename = "payment-positions" + extender + ".json";
        FileWriter fileWriter = new FileWriter(positionsFilename);
        fileWriter.write(positionsJSON);
        fileWriter.close();
        String inputJSON = objectMapper.writeValueAsString(input);
        String filename = "input-positions" + extender + ".json";
        fileWriter = new FileWriter(filename);
        fileWriter.write(inputJSON);
        fileWriter.close();
    }
}
