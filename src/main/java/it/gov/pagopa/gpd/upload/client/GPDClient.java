package it.gov.pagopa.gpd.upload.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.model.RequestGPD;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.model.RetryStep;
import it.gov.pagopa.gpd.upload.util.MapUtils;

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
    private Logger logger;

    public GPDClient(Logger logger) {
        this.logger = logger;
    }

    public static GPDClient getInstance(Logger logger) {
        if (instance == null) {
            instance = new GPDClient(logger);
        }
        return instance;
    }

    public ResponseGPD createDebtPosition(RequestGPD req) {
        String path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V2, req.getOrgFiscalCode());
        return CRUD_GPD(HttpMethod.POST, path, req);
    }

    public ResponseGPD updateDebtPosition(RequestGPD req) {
        String path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V2, req.getOrgFiscalCode());
        return CRUD_GPD(HttpMethod.PUT, path, req);
    }

    public ResponseGPD deleteDebtPosition(RequestGPD req) {
        String path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V2, req.getOrgFiscalCode());
        return CRUD_GPD(HttpMethod.DELETE, path, req);
    }

    private ResponseGPD CRUD_GPD(HttpMethod method, String path, RequestGPD req) {
        try {
            Response response = callGPD(method.name(), path, req.getBody());
            return mapResponse(method, response);
        } catch (JsonProcessingException e) {
            return ResponseGPD.builder()
                           .retryStep(RetryStep.RETRY)
                           .detail(HttpStatus.INTERNAL_SERVER_ERROR.name())
                           .build();
        }
    }

    private Response callGPD(String httpMethod, String url, String body) {
        Client client = ClientBuilder.newClient();
        String requestId = UUID.randomUUID().toString();
        try {
            Invocation.Builder builder = client.target(url)
                                                 .queryParam(TO_PUBLISH_QUERY_PARAM, TO_PUBLISH_QUERY_VALUE)
                                                 .request(MediaType.APPLICATION_JSON)
                                                 .header(HEADER_SUBSCRIPTION_KEY, GPD_SUBSCRIPTION_KEY)
                                                 .header(HEADER_REQUEST_ID, requestId);

            return builder.method(httpMethod, Entity.json(body));
        } catch (Exception e) {
            logger.log(Level.WARNING, () -> String.format("[requestId=%s][%sDebtPositions] Exception: %s", requestId, httpMethod, e.getMessage()));
            return Response.serverError().build();
        }
    }

    private ResponseGPD mapResponse(HttpMethod httpMethod, Response response) throws JsonProcessingException {
        ResponseGPD responseGPD;
        int status = response.getStatus();
        String responseDetail = String.valueOf(status);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        if (status >= 200 && status < 300) {
            responseGPD = ResponseGPD.builder()
                                  .retryStep(RetryStep.DONE)
                                  .status(status)
                                  .build();
        } else if (status >= 400 && status < 500) {
            // skip retry if the status is 4xx
            responseGPD = objectMapper.readValue(response.readEntity(String.class), ResponseGPD.class);
            responseGPD.setRetryStep(RetryStep.ERROR);
            responseDetail = responseGPD.getDetail();
        } else {
            responseGPD = ResponseGPD.builder()
                                  .status(status)
                                  .retryStep(RetryStep.RETRY)
                                  .detail(HttpStatus.INTERNAL_SERVER_ERROR.name()).build();
        }

        responseGPD.setDetail(MapUtils.getDetail(httpMethod, HttpStatus.valueOf(status), responseDetail));
        return responseGPD;
    }
}
