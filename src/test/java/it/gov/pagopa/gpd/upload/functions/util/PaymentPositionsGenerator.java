package it.gov.pagopa.gpd.upload.functions.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.gov.pagopa.gpd.upload.model.pd.PaymentOptionModel;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositionModel;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositionsModel;
import it.gov.pagopa.gpd.upload.model.pd.TransferModel;
import it.gov.pagopa.gpd.upload.model.pd.enumeration.Type;

import java.io.FileWriter;
import java.io.IOException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class PaymentPositionsGenerator {
    public static void main(String[] args) throws IOException {
        int N = 10000;
        String fiscalCode = "77777777777";
        List<PaymentPositionModel> paymentPositionList = new ArrayList<>();
        for(int i = 0; i < N; i++) {
            String ID = fiscalCode + "_" + UUID.randomUUID().toString().substring(0,5);
            TransferModel tf = TransferModel.builder()
                                       .idTransfer("1")
                                       .amount(100L)
                                       .remittanceInformation("remittance information")
                                       .category("categoryXZ")
                                       .iban("IT0000000000000000000000000")
                                       .transferMetadata(new ArrayList<>())
                                       .build();
            List<TransferModel> transferList = new ArrayList<TransferModel>();
            transferList.add(tf);
            ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(Instant.now().plus(1, ChronoUnit.DAYS), ZoneId.of("UTC"));
            PaymentOptionModel po = PaymentOptionModel.builder()
                                            .iuv("IUV_" + ID)
                                            .amount(100L)
                                            .isPartialPayment(false)
                                            .dueDate(zonedDateTime.toLocalDateTime())
                                            .transfer(transferList)
                                            .paymentOptionMetadata(new ArrayList<>())
                                            .build();
            List<PaymentOptionModel> paymentOptionList = new ArrayList<PaymentOptionModel>();
            paymentOptionList.add(po);
            PaymentPositionModel pp = PaymentPositionModel.builder()
                                              .iupd("IUPD_" + ID)
                                              .type(Type.F)
                                              .fiscalCode(fiscalCode)
                                              .fullName("Mario Rossi")
                                              .companyName("Company Name")
                                              .paymentOption(paymentOptionList)
                                              .switchToExpired(false)
                                              .build();
            paymentPositionList.add(pp);
        }

        PaymentPositionsModel paymentPositions = PaymentPositionsModel.builder()
                                                         .paymentPositions(paymentPositionList)
                                                         .build();

        ObjectMapper objectMapper = new ObjectMapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        objectMapper.registerModule(javaTimeModule);

        String jsonPP = objectMapper.writeValueAsString(paymentPositions);
        FileWriter fileWriter = new FileWriter("77777777777_1291b9d816e942e89c329bce04d17c55.json");
        fileWriter.write(jsonPP);
        fileWriter.close();
    }
}
