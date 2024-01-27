package it.gov.pagopa.gpd.upload;

import com.azure.messaging.eventgrid.EventGridEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BlobEventFunction {

    @FunctionName("BlobCreatedSubscriber")
    public HttpResponseMessage run (
            @HttpTrigger(name = "BlobCreatedSubscriber",
                    methods = {HttpMethod.POST, HttpMethod.GET, HttpMethod.PUT},
                    route = "upload",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<String> request,
            final ExecutionContext context) throws JsonProcessingException {
        Logger logger = context.getLogger();

        logger.log(Level.INFO, () -> "Request body: " + request.getBody());
        List<EventGridEvent> eventGridEvents = EventGridEvent.fromString(request.getBody());

        if(eventGridEvents == null) {
            return request.createResponseBuilder(HttpStatus.NOT_FOUND).build();
        }

        for (EventGridEvent event : eventGridEvents) {
            if(event.getEventType().equals("Microsoft.EventGrid.SubscriptionValidationEvent")) {
                logger.log(Level.INFO, () -> "Microsoft.EventGrid.SubscriptionValidationEvent: " + event);
                JsonNode jsonNode = new ObjectMapper().readTree(event.getData().toString());
                JsonNode dataNode = jsonNode.path("data");
                String validationCode = dataNode.path("validationCode").asText();
                String validationUrl = dataNode.path("validationUrl").asText();
                logger.log(Level.INFO, () -> "validationCode: " + validationCode);
                logger.log(Level.INFO, () -> "validationUrl: " + validationUrl);

                return request.createResponseBuilder(HttpStatus.OK)
                                               .body("{\"validationResponse\":"+validationCode + "}")
                                               .build();
            } else if(event.getEventType().equals("Microsoft.Storage.BlobCreated")){
                logger.log(Level.INFO, () -> "Microsoft.Storage.BlobCreated: " + event);
                return request.createResponseBuilder(HttpStatus.OK)
                               .build();
            }
        }

        return request.createResponseBuilder(HttpStatus.OK).build();
    }
}
