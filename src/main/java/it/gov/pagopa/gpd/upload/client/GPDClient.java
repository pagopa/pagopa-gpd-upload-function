package it.gov.pagopa.gpd.upload.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.model.RetryStep;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;
import lombok.SneakyThrows;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GPDClient {

    static final String HEADER_REQUEST_ID = "X-Request-Id";
    static final String HEADER_SUBSCRIPTION_KEY = "Ocp-Apim-Subscription-Key";
    static final String GPD_DEBT_POSITIONS_PATH_V1 = "/v1/organizations/%s/debtpositions";
    static final String GPD_DEBT_POSITIONS_PATH_V2 = "/v2/organizations/%s/debtpositions";
    private static GPDClient instance = null;
    final String GPD_HOST = System.getenv("GPD_HOST");
    public final String GPD_SUBSCRIPTION_KEY = System.getenv("GPD_SUBSCRIPTION_KEY");

    public GPDClient() {
    }

    public static GPDClient getInstance() {
        if (instance == null) {
            instance = new GPDClient();
        }
        return instance;
    }

    @SneakyThrows
    public ResponseGPD createBulkDebtPositions(String fiscalCode, PaymentPositions paymentPositionModel, Logger logger, String invocationId) {
        String requestId = UUID.randomUUID().toString();

        logger.log(Level.INFO, () -> String.format(
                "[id=%s][requestId=%s][GPD CALL][createDebtPositionsBulk]", invocationId, requestId));

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        String paymentPositions = objectMapper.writeValueAsString(paymentPositionModel);
        String path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V2, fiscalCode);
        Response response = callCreateDebtPositions(path, paymentPositions, logger, requestId);
        int status = response.getStatus();

        ResponseGPD responseGPD;

        if (status >= 200 && status < 300) {
            responseGPD = ResponseGPD.builder()
                    .retryStep(RetryStep.DONE)
                    .status(status)
                    .detail(String.valueOf(response.getStatus()))
                    .build();
        }
        else if (status >= 400 && status < 500) {
            // skip retry if the status is 4xx
            responseGPD = objectMapper.readValue(response.readEntity(String.class), ResponseGPD.class);
            responseGPD.setRetryStep(RetryStep.ERROR);
        }
        else {
            responseGPD = ResponseGPD.builder()
                                  .status(response.getStatus())
                                  .retryStep(RetryStep.RETRY)
                                  .detail(HttpStatus.INTERNAL_SERVER_ERROR.name()).build();
        }

        logger.log(Level.WARNING, () -> String.format(
                "[id=%s][requestId=%s][GPD CALL][createDebtPositionsBulk] HTTP status %s", invocationId, requestId, status));

        return responseGPD;
    }

    @SneakyThrows
    public ResponseGPD createDebtPosition( String invocationId, Logger logger, String fiscalCode, PaymentPosition paymentPosition) {
        String requestId = UUID.randomUUID().toString();

        logger.log(Level.INFO, () -> String.format(
                "[id=%s][requestId=%s][GPD CALL][createDebtPosition]", invocationId, requestId));

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        String paymentPositions = objectMapper.writeValueAsString(paymentPosition);
        String path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V1, fiscalCode);
        Response response = callCreateDebtPositions(path, paymentPositions, logger, requestId);
        int status = response.getStatus();

        ResponseGPD responseGPD;

        if (status >= 200 && status < 300) {
            responseGPD = ResponseGPD.builder()
                                  .retryStep(RetryStep.DONE)
                                  .status(status)
                                  .detail(String.valueOf(response.getStatus()))
                                  .build();
        }
        else if (status >= 400 && status < 500) {
            // skip retry if the status is 4xx
            responseGPD = objectMapper.readValue(response.readEntity(String.class), ResponseGPD.class);
            responseGPD.setRetryStep(RetryStep.ERROR);
        } else {
            responseGPD = ResponseGPD.builder()
                              .status(response.getStatus())
                              .retryStep(RetryStep.RETRY)
                              .detail(HttpStatus.INTERNAL_SERVER_ERROR.name()).build();
        }

        logger.log(Level.WARNING, () -> String.format(
                "[id=%s][requestId=%s][GPD CALL][createDebtPosition] HTTP status %s", invocationId, requestId, status));

        return responseGPD;
    }

    public Response callCreateDebtPositions(String path, String paymentPositions, Logger logger, String requestId) {
        try {
            Response response = ClientBuilder.newClient()
                    .target(path)
                    .request()
                    .header(HEADER_SUBSCRIPTION_KEY, GPD_SUBSCRIPTION_KEY)
                    .header(HEADER_REQUEST_ID, requestId)
                    .post(Entity.json(paymentPositions));

            logger.log(Level.INFO, () -> String.format(
                    "[requestId=%s][createDebtPositions] Response: %s", requestId, response.getStatus()));

            return response;
        } catch (Exception e) {
            logger.log(Level.WARNING, () -> String.format(
                    "[requestId=%s][createDebtPositions] Exception: %s", requestId, e.getMessage()));
            return Response.serverError().build();
        }
    }
}
