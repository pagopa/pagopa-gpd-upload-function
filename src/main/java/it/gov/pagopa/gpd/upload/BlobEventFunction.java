package it.gov.pagopa.gpd.upload;

import com.azure.core.util.BinaryData;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.HashMap;
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
            final ExecutionContext context) {
        Logger logger = context.getLogger();

        logger.log(Level.INFO, () -> "Request body: " + request.getBody());
        BinaryData events = BinaryData.fromString(String.valueOf(request.getBody()));
        logger.log(Level.INFO, () -> "Request body: " + events.toString());
        Map<String, Object> eventsDictionary = convertStringToDictionary(String.valueOf(events));
        logger.log(Level.INFO, () -> "Events: " + eventsDictionary);

        HttpResponseMessage httpResponseMessage;
        if(eventsDictionary.get("eventType").equals("Microsoft.EventGrid.SubscriptionValidationEvent")) {
            httpResponseMessage =  request.createResponseBuilder(HttpStatus.OK)
                                                               .body("{\"validationResponse\":"+eventsDictionary.get("validationCode") + "}")
                                                               .build();
            logger.log(Level.INFO, () -> "Response: " + httpResponseMessage.getBody());
        } else {
            httpResponseMessage =  request.createResponseBuilder(HttpStatus.OK)
                                                               .build();
        }

        return httpResponseMessage;
    }

    private static Map<String, Object> convertStringToDictionary(String inputString) {
        Map<String, Object> dictionary = new HashMap<>();

        // Extract key-value pairs using regex
        Pattern pattern = Pattern.compile("(\\w+)=([^,}]+)");
        Matcher matcher = pattern.matcher(inputString);

        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2).trim();

            // If the value is an object, convert it to a nested dictionary
            if (value.startsWith("{") && value.endsWith("}")) {
                value = value.substring(1, value.length() - 1);
                Map<String, Object> nestedDictionary = convertStringToDictionary(value);
                dictionary.put(key, nestedDictionary);
            } else if (key.equals("data")) {
                // Handle special case for the 'data' field
                Map<String, Object> nestedData = convertStringToDictionary(value);
                dictionary.putAll(nestedData);
            } else {
                dictionary.put(key, value);
            }
        }

        return dictionary;
    }
}
