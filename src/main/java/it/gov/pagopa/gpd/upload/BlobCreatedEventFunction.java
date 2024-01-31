package it.gov.pagopa.gpd.upload;

import com.azure.core.implementation.serializer.DefaultJsonSerializer;
import com.azure.core.util.BinaryData;
import com.azure.messaging.eventgrid.EventGridEvent;
import com.azure.messaging.eventgrid.systemevents.StorageBlobCreatedEventData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;
import it.gov.pagopa.gpd.upload.repository.BlobStorageRepository;
import it.gov.pagopa.gpd.upload.service.BlockService;
import it.gov.pagopa.gpd.upload.service.StatusService;
import it.gov.pagopa.gpd.upload.util.MapUtils;
import it.gov.pagopa.gpd.upload.util.PaymentPositionValidator;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BlobCreatedEventFunction {

    @FunctionName("BlobQueueEventFunction")
    public void run(
            @QueueTrigger(name = "BlobCreatedEventTrigger", queueName = "%BLOB_EVENTS_QUEUE%", connection = "GPD_SA_CONNECTION_STRING") String events,
            final ExecutionContext context) {
        Logger logger = context.getLogger();
        logger.log(Level.INFO, () -> "Event: " + events);

        List<EventGridEvent> eventGridEvents = EventGridEvent.fromString(events);

        if (eventGridEvents.isEmpty()) {
            logger.log(Level.INFO, () -> "EventGrid List is empty");
            return; // skip event
        }

        for (EventGridEvent event : eventGridEvents) {
            if (event.getEventType().equals("Microsoft.Storage.BlobCreated")) {
                logger.log(Level.INFO, () -> "Microsoft.Storage.BlobCreated");

                StorageBlobCreatedEventData blobData = event.getData().toObject(StorageBlobCreatedEventData.class, new DefaultJsonSerializer());
                if (blobData.getContentLength() > 1e+8) { // if file greater than 100 MB
                    logger.log(Level.INFO, () -> "File size too large");
                    return; // skip event
                }
                if (blobData.getContentLength() == 0) {
                    logger.log(Level.INFO, () -> "File size equal to zero");
                    return; // skip event
                }

                logger.log(Level.INFO, () -> "Subject: " + event.getSubject());
                Pattern pattern = Pattern.compile("/containers/(\\w+)/blobs/(\\w+)/input/(\\w+\\.json)");
                Matcher matcher = pattern.matcher(event.getSubject());

                // Check if the pattern is found
                if (matcher.find()) {
                    String brokerContainer = matcher.group(1);    // broker container as broke_code
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
            }
        }
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
