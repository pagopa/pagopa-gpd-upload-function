package it.gov.pagopa.gpd.upload.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.gov.pagopa.gpd.upload.entity.ResponseGPD;
import it.gov.pagopa.gpd.upload.model.RetryStep;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositionsModel;
import lombok.SneakyThrows;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GpdClient {

    static final String HEADER_REQUEST_ID = "X-Request-Id";
    static final String HEADER_SUBSCRIPTION_KEY = "Ocp-Apim-Subscription-Key";
    static final String GPD_DEBT_POSITIONS_PATH = "/organizations/%s/debtpositions";
    private static GpdClient instance = null;
    final String GPD_HOST = System.getenv("GPD_HOST");
    public final String GPD_SUBSCRIPTION_KEY = System.getenv("GPD_SUBSCRIPTION_KEY");

    private GpdClient() {
    }

    public static GpdClient getInstance() {
        if (instance == null) {
            instance = new GpdClient();
        }
        return instance;
    }

    @SneakyThrows
    public ResponseGPD createDebtPositions(String fiscalCode, PaymentPositionsModel paymentPositionModel, Logger logger, String invocationId) {
        String requestId = UUID.randomUUID().toString();

        logger.log(Level.INFO, () -> String.format(
                "[id=%s][requestId=%s][GPD CALL][createDebtPositions]", invocationId, requestId));

        ResponseGPD responseGPD = callCreateDebtPositions(fiscalCode, paymentPositionModel, logger, requestId);
        int status = responseGPD.getStatus();

        logger.log(Level.INFO, () -> String.format(
                "[requestId=%s][createDebtPositions] Response message: %s", requestId, responseGPD.getDetail()));

        if (status >= 200 && status < 300) {
            responseGPD.setRetryStep(RetryStep.DONE);
        }
        if (status >= 400 && status < 500) {
            // skip retry if the status is 4xx
            responseGPD.setRetryStep(RetryStep.ERROR);
        }
        else {
            responseGPD.setRetryStep(RetryStep.RETRY);
        }

        logger.log(Level.WARNING, () -> String.format(
                "[id=%s][requestId=%s][GPD CALL][createDebtPositions] HTTP status %s", invocationId, requestId, status));

        return responseGPD;
    }

    private ResponseGPD callCreateDebtPositions(String idPA, PaymentPositionsModel paymentPositions, Logger logger, String requestId) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            String paymentPositionsJSON = objectMapper.writeValueAsString(paymentPositions);
            Response response = ClientBuilder.newClient()
                    .target(GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH, idPA))
                    .request()
                    .header(HEADER_SUBSCRIPTION_KEY, GPD_SUBSCRIPTION_KEY)
                    .header(HEADER_REQUEST_ID, requestId)
                    .post(Entity.json(paymentPositionsJSON));

            logger.log(Level.INFO, () -> String.format(
                    "[requestId=%s][createDebtPositions] Response: %s", requestId, response.getStatus()));
            logger.log(Level.INFO, () -> String.format(
                    "[requestId=%s][createDebtPositions] Response: %s", requestId, response.readEntity(String.class)));

            return objectMapper.readValue(response.readEntity(String.class), ResponseGPD.class);
        } catch (Exception e) {
            logger.log(Level.WARNING, () -> String.format(
                    "[requestId=%s][createDebtPositions] Exception: %s", requestId, e.getMessage()));
            return ResponseGPD.builder()
                    .status(-1)
                    .detail(e.getMessage().substring(0, 150))
                    .build();
        }
    }
}
