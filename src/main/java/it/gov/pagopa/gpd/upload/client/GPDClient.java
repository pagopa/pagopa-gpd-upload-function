package it.gov.pagopa.gpd.upload.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.model.RetryStep;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GPDClient {
    private static GPDClient instance;
    private static final String HEADER_REQUEST_ID = "X-Request-Id";
    private static final String HEADER_SUBSCRIPTION_KEY = "Ocp-Apim-Subscription-Key";
    private static final String GPD_DEBT_POSITIONS_PATH_V1 = "/v1/organizations/%s/debtpositions";
    private static final String GPD_DEBT_POSITIONS_PATH_V2 = "/v2/organizations/%s/debtpositions";
    private final String GPD_HOST = System.getenv("GPD_HOST");
    private final String GPD_SUBSCRIPTION_KEY = System.getenv("GPD_SUBSCRIPTION_KEY");
    private static final String toPublishQueryParam = "toPublish";
    private static final boolean toPublish = true;

    public GPDClient() {
    }

    public static GPDClient getInstance() {
        if (instance == null) {
            instance = new GPDClient();
        }
        return instance;
    }

    public ResponseGPD createBulkDebtPositions(String fiscalCode, PaymentPositions paymentPositionModel, Logger logger, String invocationId) throws AppException {
        String requestId = UUID.randomUUID().toString();
        String path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V2, fiscalCode);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        logger.log(Level.INFO, () -> String.format( "[id=%s][requestId=%s][GPD CALL][createDebtPositionsBulk]", invocationId, requestId));
        try {
            String paymentPositions = objectMapper.writeValueAsString(paymentPositionModel);
            Response response = callCreateDebtPositions(path, paymentPositions, logger, requestId);
            ResponseGPD responseGPD = this.createResponseGPD(response);
            return responseGPD;
        } catch (JsonProcessingException e) {
            throw new AppException("Error while GPD-Core client call bulk creation: " + e.getMessage());
        }
    }

    public ResponseGPD createDebtPosition( String invocationId, Logger logger, String fiscalCode, PaymentPosition paymentPosition) throws AppException {
        String requestId = UUID.randomUUID().toString();
        String path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V1, fiscalCode);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        logger.log(Level.INFO, () -> String.format("[id=%s][requestId=%s][GPD CALL][createDebtPosition]", invocationId, requestId));
        try {
            String paymentPositions = objectMapper.writeValueAsString(paymentPosition);
            Response response = callCreateDebtPositions(path, paymentPositions, logger, requestId);
            ResponseGPD responseGPD = this.createResponseGPD(response);
            return responseGPD;
        } catch (JsonProcessingException e) {
            throw new AppException("Error while GPD-Core client call single creation: " + e.getMessage());
        }
    }

    public Response callCreateDebtPositions(String path, String paymentPositions, Logger logger, String requestId) {
        try {
            Response response = ClientBuilder.newClient()
                    .target(path)
                    .queryParam(toPublishQueryParam, toPublish)
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

    private ResponseGPD createResponseGPD(Response response) throws JsonProcessingException {
        ResponseGPD responseGPD;
        int status = response.getStatus();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        if (status >= 200 && status < 300) {
            responseGPD = ResponseGPD.builder()
                                  .retryStep(RetryStep.DONE)
                                  .status(status)
                                  .detail(String.valueOf(status))
                                  .build();
        }
        else if (status >= 400 && status < 500) {
            // skip retry if the status is 4xx
            responseGPD = objectMapper.readValue(response.readEntity(String.class), ResponseGPD.class);
            responseGPD.setRetryStep(RetryStep.ERROR);
        }
        else {
            responseGPD = ResponseGPD.builder()
                                  .status(status)
                                  .retryStep(RetryStep.RETRY)
                                  .detail(HttpStatus.INTERNAL_SERVER_ERROR.name()).build();
        }
        return responseGPD;
    }
}
