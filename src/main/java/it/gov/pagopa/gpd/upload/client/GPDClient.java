package it.gov.pagopa.gpd.upload.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.RequestGPD;
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
    private static final String URI_SEPARATOR = "/";
    private static final String GPD_HOST = System.getenv("GPD_HOST");
    private static final String GPD_SUBSCRIPTION_KEY = System.getenv("GPD_SUBSCRIPTION_KEY");
    private static final String TO_PUBLISH_QUERY_PARAM = "toPublish";
    private static final boolean TO_PUBLISH_QUERY_VALUE = true;

    public GPDClient() {
    }

    public static GPDClient getInstance() {
        if (instance == null) {
            instance = new GPDClient();
        }
        return instance;
    }

    public ResponseGPD createBulkDebtPositions(String fiscalCode, PaymentPositions paymentPositionModel, Logger logger, String invocationId) throws AppException {
        String path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V2, fiscalCode);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        logger.log(Level.INFO, () -> String.format( "[id=%s][GPD CALL][createDebtPositionsBulk]", invocationId));
        try {
            String paymentPositions = objectMapper.writeValueAsString(paymentPositionModel);
            Response response = postGPD(path, paymentPositions, logger);
            ResponseGPD responseGPD = this.mapResponse(response);
            return responseGPD;
        } catch (JsonProcessingException e) {
            throw new AppException("Error while GPD-Core client call bulk creation: " + e.getMessage());
        }
    }

    public ResponseGPD createDebtPosition( String invocationId, Logger logger, String orgFiscalCode, PaymentPosition paymentPosition) throws AppException {
        String path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V1, orgFiscalCode);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        logger.log(Level.INFO, () -> String.format("[id=%s][GPD CALL][createDebtPosition]", invocationId));
        try {
            String paymentPositions = objectMapper.writeValueAsString(paymentPosition);
            Response response = postGPD(path, paymentPositions, logger);
            ResponseGPD responseGPD = this.mapResponse(response);
            return responseGPD;
        } catch (JsonProcessingException e) {
            throw new AppException("Error while GPD-Core client call single creation: " + e.getMessage());
        }
    }

    public ResponseGPD updateBulkDebtPositions(String fiscalCode, PaymentPositions paymentPositions, Logger logger, String invocationId) throws AppException {
        String path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V2, fiscalCode);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        logger.log(Level.INFO, () -> String.format( "[id=%s][GPD CALL][updateDebtPositionsBulk]", invocationId));
        try {
            String paymentPositionsBody = objectMapper.writeValueAsString(paymentPositions);
            Response response = putGPD(path, paymentPositionsBody, logger);
            ResponseGPD responseGPD = this.mapResponse(response);
            return responseGPD;
        } catch (JsonProcessingException e) {
            throw new AppException("Error while calling Bulk UPDATE: " + e.getMessage());
        }
    }

    public ResponseGPD updateDebtPosition(String orgFiscalCode, PaymentPosition paymentPosition, Logger logger, String invocationId) throws AppException {
        String path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V1, orgFiscalCode) + URI_SEPARATOR + paymentPosition.getIupd();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        logger.log(Level.INFO, () -> String.format( "[id=%s][GPD CALL][updateDebtPosition]", invocationId));
        try {
            String paymentPositionBody = objectMapper.writeValueAsString(paymentPosition);
            Response response = putGPD(path, paymentPositionBody, logger);
            ResponseGPD responseGPD = this.mapResponse(response);
            return responseGPD;
        } catch (JsonProcessingException e) {
            throw new AppException("Error while calling UPDATE by IUPD");
        }
    }

    public Response postGPD(String path, String body, Logger logger) {
        String requestId = UUID.randomUUID().toString();
        try {
            Response response = ClientBuilder.newClient()
                    .target(path)
                    .queryParam(TO_PUBLISH_QUERY_PARAM, TO_PUBLISH_QUERY_VALUE)
                    .request()
                    .header(HEADER_SUBSCRIPTION_KEY, GPD_SUBSCRIPTION_KEY)
                    .header(HEADER_REQUEST_ID, requestId)
                    .post(Entity.json(body));

            logger.log(Level.INFO, () -> String.format(
                    "[requestId=%s][createDebtPositions] Response: %s", requestId, response.getStatus()));

            return response;
        } catch (Exception e) {
            logger.log(Level.WARNING, () -> String.format(
                    "[requestId=%s][createDebtPositions] Exception: %s", requestId, e.getMessage()));
            return Response.serverError().build();
        }
    }

    public Response putGPD(String path, String body, Logger logger) {
        String requestId = UUID.randomUUID().toString();
        try {
            Response response = ClientBuilder.newClient()
                                        .target(path)
                                        .queryParam(TO_PUBLISH_QUERY_PARAM, TO_PUBLISH_QUERY_VALUE)
                                        .request()
                                        .header(HEADER_SUBSCRIPTION_KEY, GPD_SUBSCRIPTION_KEY)
                                        .header(HEADER_REQUEST_ID, requestId)
                                        .put(Entity.json(body));

            logger.log(Level.INFO, () -> String.format(
                    "[requestId=%s][createDebtPositions] Response: %s", requestId, response.getStatus()));

            return response;
        } catch (Exception e) {
            logger.log(Level.WARNING, () -> String.format(
                    "[requestId=%s][createDebtPositions] Exception: %s", requestId, e.getMessage()));
            return Response.serverError().build();
        }
    }

    public ResponseGPD createDebtPosition(RequestGPD req) {
        try {
            if(req.getMode().equals(RequestGPD.Mode.BULK)) {
                return createBulkDebtPositions(req.getOrgFiscalCode(), (PaymentPositions) req.getBody(), req.getLogger(), req.getInvocationId());
            } else { // RequestGPD.Mode.SINGLE case: tertium non datur
                return createDebtPosition(req.getInvocationId(), req.getLogger(), req.getOrgFiscalCode(), (PaymentPosition) req.getBody());
            }
        } catch (AppException appException) {
            return ResponseGPD.builder().build();
        }
    }

    public ResponseGPD updateDebtPosition(RequestGPD req) {
        try {
            if(req.getMode().equals(RequestGPD.Mode.BULK)) {
                return updateBulkDebtPositions(req.getOrgFiscalCode(), (PaymentPositions) req.getBody(), req.getLogger(), req.getInvocationId());
            } else { // RequestGPD.Mode.SINGLE case: tertium non datur
                return updateDebtPosition(req.getOrgFiscalCode(), (PaymentPosition) req.getBody(), req.getLogger(), req.getInvocationId());
            }
        } catch (AppException appException) {
            return ResponseGPD.builder().build();
        }
    }

    private ResponseGPD mapResponse(Response response) throws JsonProcessingException {
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
