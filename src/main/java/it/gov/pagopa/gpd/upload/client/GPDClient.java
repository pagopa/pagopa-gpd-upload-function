package it.gov.pagopa.gpd.upload.client;

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
    private static final String GPD_DEBT_POSITIONS_PATH_V1 = "/v1/organizations/%s/debtpositions?serviceType=%s";
    private static final String GPD_DEBT_POSITIONS_PATH_V2 = "/v2/organizations/%s/debtpositions?serviceType=%s";
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
        String path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V2, req.getOrgFiscalCode(), req.getServiceType());
        return CRUD_GPD(HttpMethod.POST, path, req);
    }

    public ResponseGPD updateDebtPosition(RequestGPD req) {
        String path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V2, req.getOrgFiscalCode(), req.getServiceType());
        return CRUD_GPD(HttpMethod.PUT, path, req);
    }

    public ResponseGPD deleteDebtPosition(RequestGPD req) {
        String path = GPD_HOST + String.format(GPD_DEBT_POSITIONS_PATH_V2, req.getOrgFiscalCode(), req.getServiceType());
        return CRUD_GPD(HttpMethod.DELETE, path, req);
    }
    
    private ResponseGPD CRUD_GPD(HttpMethod method, String path, RequestGPD req) {
        Response response = null;
        try {
            response = callGPD(method.name(), path, req.getBody());
            return mapResponse(response);
        } catch (RuntimeException e) {
            // Log and prudential fallback: RETRY with 500 + standard message
            logger.log(Level.WARNING, String.format("[GPDClient][%s] Unexpected runtime error: %s",
                    method.name(), e.getMessage()), e);

            return ResponseGPD.builder()
                    .retryStep(RetryStep.RETRY)
                    .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .detail(formatStatusAndMessage(
                            HttpStatus.INTERNAL_SERVER_ERROR.value(),
                            MapUtils.getDetail(HttpStatus.INTERNAL_SERVER_ERROR)))
                    .build();
        } finally {
            if (response != null) {
                try { response.close(); } catch (Exception ignore) { /* no-op */ }
            }
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

    
    private ResponseGPD mapResponse(Response response) {
        ResponseGPD responseGPD;
        int status = response.getStatus();
        // read the one-shot body
        String rawBody = safeReadBody(response);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        if (status >= 200 && status < 300) {
            responseGPD = ResponseGPD.builder()
                    .retryStep(RetryStep.DONE)
                    .status(status)
                    .build();
        } else if (status >= 400 && status < 500) {
        	// skip retry if the status is 4xx
            responseGPD = mapClientError(objectMapper, rawBody, status);
        } else {
            responseGPD = ResponseGPD.builder()
                    .status(status)
                    .retryStep(RetryStep.RETRY)
                    .detail(HttpStatus.INTERNAL_SERVER_ERROR.name())
                    .build();
        }

        HttpStatus httpStatus = HttpStatus.valueOf(status);
        String finalMessage;
        if (httpStatus == HttpStatus.OK || httpStatus == HttpStatus.CREATED) {
            // for 200/201 ALWAYS use the standard message
            finalMessage = MapUtils.getDetail(httpStatus);
        } else {
            // for other statuses, if it exists, use GPD message, otherwise standard message
            String gpdMsg = extractGpdMessage(rawBody);
            finalMessage = (gpdMsg != null) ? gpdMsg : MapUtils.getDetail(httpStatus);
        }
        responseGPD.setDetail(formatStatusAndMessage(status, finalMessage));

        return responseGPD;
    }
    
    private ResponseGPD mapClientError(ObjectMapper objectMapper, String rawBody, int status) {
        ResponseGPD mapped;
        try {
            mapped = objectMapper.readValue(rawBody, ResponseGPD.class);
        } catch (Exception ignore) {
            mapped = null;
        }
        ResponseGPD responseGPD;
        if (mapped != null) {
            responseGPD = mapped;
            if (responseGPD.getStatus() == 0) {
                responseGPD.setStatus(status);
            }
        } else {
            responseGPD = ResponseGPD.builder().status(status).build();
        }
        responseGPD.setRetryStep(RetryStep.ERROR);
        return responseGPD;
    }

    
    private String safeReadBody(Response response) {
        try {
            return response.hasEntity() ? response.readEntity(String.class) : "";
        } catch (Exception e) {
            return "";
        }
    }

    // Combine "status - message" (if message present).
    private String formatStatusAndMessage(int status, String message) {
        String msg = (message == null) ? "" : message.trim();
        return msg.isEmpty() ? String.valueOf(status) : (status + " - " + msg);
    }

    private String extractGpdMessage(String rawBody) {
    	// 1) Try reading detail from problem+json
    	// 2) Fallback: Use the raw body as a string
    	// 3) Otherwise, null
        if (!isNotBlank(rawBody)) {
            return null;
        }
        try {
            ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
            var node = om.readTree(rawBody);
            if (node != null && node.hasNonNull("detail")) {
                String detail = node.get("detail").asText();
                if (isNotBlank(detail)) {
                    return detail.trim();
                }
            }
        } catch (Exception ignore) {
            // If it's not JSON or parsing fails, fallback to raw.
        }
        // fallback: truncated text body
        String s = rawBody.trim();
        return s.length() > 500 ? (s.substring(0, 500) + "...") : s;
    }
    
    private boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

}
