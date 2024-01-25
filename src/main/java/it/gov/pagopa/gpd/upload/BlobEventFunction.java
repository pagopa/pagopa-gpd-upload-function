package it.gov.pagopa.gpd.upload;

import com.azure.core.util.BinaryData;
import com.azure.messaging.eventgrid.EventGridEvent;
import com.azure.messaging.eventgrid.systemevents.SubscriptionValidationEventData;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BlobEventFunction {

    @FunctionName("BlobCreatedSubscriber")
    public HttpResponseMessage run (
            @HttpTrigger(name = "BlobCreatedSubscriber",
                    methods = {HttpMethod.POST, HttpMethod.GET},
                    route = "upload",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage request,
            final ExecutionContext context) {
        BinaryData events = BinaryData.fromString(String.valueOf(request.getBody()));
        Logger logger = context.getLogger();
        logger.log(Level.INFO, () -> "Request body: " + request.getBody());
        logger.log(Level.INFO, () -> "Events: " + events);

        List<EventGridEvent> eventGridEvents = EventGridEvent.fromString(events.toString());

        for (EventGridEvent eventGridEvent : eventGridEvents) {
            if(eventGridEvent.getEventType().equals("SubscriptionValidationEventData")){
                SubscriptionValidationEventData s = SubscriptionValidationEventData.class.cast(eventGridEvent);
                return request.createResponseBuilder(HttpStatus.OK)
                               .header("Content-Type", "application/json")
                               .body(s.getValidationCode())
                               .build();
            }
        }
        return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                       .build();
    }
}
