package it.gov.pagopa.gpd.upload;

import com.azure.core.implementation.serializer.DefaultJsonSerializer;
import com.azure.core.util.BinaryData;
import com.azure.messaging.eventgrid.EventGridEvent;
import com.azure.messaging.eventgrid.systemevents.StorageBlobCreatedEventData;
import com.azure.messaging.eventgrid.systemevents.SubscriptionValidationEventData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;
import it.gov.pagopa.gpd.upload.repository.BlobStorageRepository;
import it.gov.pagopa.gpd.upload.service.BlockService;
import it.gov.pagopa.gpd.upload.service.StatusService;
import it.gov.pagopa.gpd.upload.util.MapUtils;
import it.gov.pagopa.gpd.upload.util.PaymentPositionValidator;

import java.util.*;
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
        List<EventGridEvent> eventGridEvents = EventGridEvent.fromString(request.getBody());

        if(eventGridEvents == null) {
            return request.createResponseBuilder(HttpStatus.NOT_FOUND).build();
        }

        for (EventGridEvent event : eventGridEvents) {
            if(event.getEventType().equals("Microsoft.EventGrid.SubscriptionValidationEvent")) {
                logger.log(Level.INFO, () -> "Microsoft.EventGrid.SubscriptionValidationEvent: " + event);
                SubscriptionValidationEventData validationData =
                        event.getData().toObject(SubscriptionValidationEventData.class, new DefaultJsonSerializer());

                return request.createResponseBuilder(HttpStatus.OK)
                                               .body("{\"validationResponse\":"+validationData.getValidationCode() + "}")
                                               .build();
            } else if(event.getEventType().equals("Microsoft.Storage.BlobCreated")){
                logger.log(Level.INFO, () -> "Microsoft.Storage.BlobCreated");

                StorageBlobCreatedEventData blobData = event.getData().toObject(StorageBlobCreatedEventData.class, new DefaultJsonSerializer());
                if(blobData.getContentLength() > 1e+8) { // if file greater than 100 MB
                    logger.log(Level.INFO, () -> "File size too large");
                    return request.createResponseBuilder(HttpStatus.OK).build(); // skip request
                }

                logger.log(Level.INFO, () -> "Subject: " + event.getSubject());
                Pattern pattern = Pattern.compile("/containers/(\\w+)/blobs/(\\w+)/input/(\\w+\\.json)");
                Matcher matcher = pattern.matcher(event.getSubject());

                // Check if the pattern is found
                if (matcher.find()) {
                    String brokerContainer = matcher.group(1);    // broker container as broker_{broke_code}
                    String fiscalCode = matcher.group(2);   // creditor institution directory
                    String filename = matcher.group(3);     // e.g. 77777777777c8a1.json
                    logger.log(Level.INFO, () -> "brokerContainer: " + brokerContainer
                                                    + "\n fiscalCode: " + fiscalCode
                                                    + "\n filename: " + filename);

                    BinaryData content = new BlobStorageRepository().download(logger, brokerContainer, fiscalCode, filename);
                    this.processBlob(context, logger, brokerContainer, fiscalCode, filename, content.toString());
                } else {
                    logger.log(Level.INFO, () -> "No match found in the input string.");
                }

                return request.createResponseBuilder(HttpStatus.OK)
                               .build();
            }
        }

        return request.createResponseBuilder(HttpStatus.OK).build();
    }

    public void processBlob(final ExecutionContext context, Logger logger, String broker, String fc, String filename, String converted) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.registerModule(new JavaTimeModule());
        String key = filename.substring(0, filename.indexOf("."));

        try {
            // deserialize payment positions from JSON to Object
            PaymentPositions pps = objectMapper.readValue(converted, PaymentPositions.class);
            Status status = StatusService.getInstance(logger).createStatus(broker, fc, key, pps);
            if (status.getUpload().getEnd() != null) { // already exist no-retry
                return;
            }
            logger.log(Level.INFO, () -> "Payment positions size: " + pps.getPaymentPositions().size());
            // function logic: validation and block upload to GPD-Core
            PaymentPositionValidator.validate(logger, pps, status);
            new BlockService().createPaymentPositionBlocks(logger, context.getInvocationId(), fc, key, pps, status);
            // write report in output container
            new BlobStorageRepository().uploadOutput(logger, objectMapper.writeValueAsString(MapUtils.convert(status)), broker,
                    fc, "report_" + filename);
        } catch (Exception e) {
            logger.log(Level.INFO, () -> "Processing function exception: " + e.getMessage() + ", caused by: " + e.getCause());
            // describe exception in blob processing in output container
            new BlobStorageRepository().uploadOutput(logger, "The input file cannot be processed due to an exception.", broker,
                    fc, "report_" + filename);
        }
    }
}
