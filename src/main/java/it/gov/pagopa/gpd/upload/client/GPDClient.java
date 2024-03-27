package it.gov.pagopa.gpd.upload.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.model.ModelGPD;
import it.gov.pagopa.gpd.upload.model.RequestGPD;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.model.RetryStep;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
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

    public ResponseGPD createDebtPosition(RequestGPD<ModelGPD> req) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        String path;
        if(req.getMode().equals(RequestGPD.Mode.BULK)) {
            path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V2, req.getOrgFiscalCode());
        } else { // RequestGPD.Mode.SINGLE case
            path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V1, req.getOrgFiscalCode());
        }
        try {
            String body = objectMapper.writeValueAsString(req.getBody());
            Response response = postGPD(path, body, req.getLogger());
            return mapResponse(response);
        } catch (JsonProcessingException jsonProcessingException) {
            return ResponseGPD.builder()
                           .retryStep(RetryStep.RETRY)
                           .detail(HttpStatus.INTERNAL_SERVER_ERROR.name())
                           .build();
        }
    }

    public ResponseGPD updateDebtPosition(RequestGPD<ModelGPD> req) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        String path;
        try {
            if(req.getMode().equals(RequestGPD.Mode.BULK)) {
                path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V2, req.getOrgFiscalCode());
            } else { // RequestGPD.Mode.SINGLE case
                PaymentPosition paymentPosition = (PaymentPosition) req.getBody();
                path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V1, req.getOrgFiscalCode()) + URI_SEPARATOR + paymentPosition.getIupd();
            }

            String body = objectMapper.writeValueAsString(req.getBody());
            Response response = putGPD(path, body, req.getLogger());
            return mapResponse(response);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public ResponseGPD deleteDebtPosition(RequestGPD<ModelGPD> req) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        String path;
        try {
            if(req.getMode().equals(RequestGPD.Mode.BULK)) {
                path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V2, req.getOrgFiscalCode());
            } else { // RequestGPD.Mode.SINGLE case
                PaymentPosition paymentPosition = (PaymentPosition) req.getBody();
                path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V1, req.getOrgFiscalCode()) + URI_SEPARATOR + paymentPosition.getIupd();
            }

            String body = objectMapper.writeValueAsString(req.getBody());
            Response response = callGPD("DELETE", path, body, req.getLogger());
            return mapResponse(response);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
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
                    "[requestId=%s][updateDebtPositions] Response: %s", requestId, response.getStatus()));

            return response;
        } catch (Exception e) {
            logger.log(Level.WARNING, () -> String.format(
                    "[requestId=%s][updateDebtPositions] Exception: %s", requestId, e.getMessage()));
            return Response.serverError().build();
        }
    }

    public Response callGPD(String httpMethod, String url, String body, Logger logger) {
        Client client = ClientBuilder.newClient();
        String requestId = UUID.randomUUID().toString();
        try {
            Invocation.Builder builder = client.target(url)
                                                 .queryParam(TO_PUBLISH_QUERY_PARAM, TO_PUBLISH_QUERY_VALUE)
                                                 .request(MediaType.APPLICATION_JSON)                                        .header(HEADER_SUBSCRIPTION_KEY, GPD_SUBSCRIPTION_KEY)
                                                 .header(HEADER_SUBSCRIPTION_KEY, GPD_SUBSCRIPTION_KEY)
                                                 .header(HEADER_REQUEST_ID, requestId);

            Response response = builder.method(httpMethod, Entity.json(body));
            logger.log(Level.INFO, () -> String.format(
                    "[requestId=%s][%sDebtPositions] Response: %s", httpMethod, requestId, response.getStatus()));
            client.close();
            return response;
        } catch (Exception e) {
            logger.log(Level.WARNING, () -> String.format(
                    "[requestId=%s][%sDebtPositions] Exception: %s", httpMethod, requestId, e.getMessage()));
            return Response.serverError().build();
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
