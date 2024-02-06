package it.gov.pagopa.gpd.upload;

import com.azure.core.implementation.serializer.DefaultJsonSerializer;
import com.azure.core.util.BinaryData;
import com.azure.messaging.eventgrid.EventGridEvent;
import com.azure.messaging.eventgrid.systemevents.StorageBlobCreatedEventData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import it.gov.pagopa.gpd.upload.entity.PaymentPositionsMessage;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;
import it.gov.pagopa.gpd.upload.repository.BlobRepository;
import it.gov.pagopa.gpd.upload.service.QueueService;
import it.gov.pagopa.gpd.upload.service.StatusService;
import it.gov.pagopa.gpd.upload.util.PaymentPositionValidator;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Following function load blob, validate it and create a request message to enqueue
 * Validation step act as a filter and is followed by the queuing step
 */
public class ValidationFunction {

    private final Integer CHUNK_SIZE = Integer.valueOf(System.getenv("CHUNK_SIZE"));

    @FunctionName("BlobQueueEventFunction")
    public void run(
            @QueueTrigger(name = "BlobCreatedEventTrigger", queueName = "%BLOB_EVENTS_QUEUE%", connection = "GPD_SA_CONNECTION_STRING") String events,
            final ExecutionContext context) {

        Logger logger = context.getLogger();

        List<EventGridEvent> eventGridEvents = EventGridEvent.fromString(events);

        if (eventGridEvents.isEmpty()) {
            logger.log(Level.SEVERE, () -> String.format("[id=%s][ValidationFunction] Empty event list.", context.getInvocationId()));
            return; // skip event
        }

        for (EventGridEvent event : eventGridEvents) {
            if (event.getEventType().equals("Microsoft.Storage.BlobCreated")) {
                logger.log(Level.INFO, () -> String.format("[id=%s][ValidationFunction] Call event type %s handler.", context.getInvocationId(), event.getEventType()));


                StorageBlobCreatedEventData blobData = event.getData().toObject(StorageBlobCreatedEventData.class, new DefaultJsonSerializer());
                if (blobData.getContentLength() > 1e+8) { // if file greater than 100 MB
                    logger.log(Level.INFO, () -> "File size too large");
                    return; // skip event
                }
                if (blobData.getContentLength() == 0) {
                    logger.log(Level.INFO, () -> "File size equal to zero");
                    return; // skip event
                }

                logger.log(Level.INFO, () -> String.format("[id=%s][ValidationFunction] Blob event subject: %s", context.getInvocationId(), event.getSubject()));


                Pattern pattern = Pattern.compile("/containers/(\\w+)/blobs/(\\w+)/input/(\\w+\\.json)");
                Matcher matcher = pattern.matcher(event.getSubject());

                // Check if the pattern is found
                if (matcher.find()) {
                    String broker = matcher.group(1);    // broker container as broke_code
                    String fiscalCode = matcher.group(2);   // creditor institution directory
                    String filename = matcher.group(3);     // e.g. 77777777777c8a1.json

                    logger.log(Level.INFO, () -> String.format("[id=%s][ValidationFunction] broker: %s, fiscalCode: %s, filename: %s", context.getInvocationId(), broker, fiscalCode, filename));

                    BinaryData content = BlobRepository.getInstance(logger).download(broker, fiscalCode, filename);
                    String key = filename.substring(0, filename.indexOf("."));
                    this.validateBlob(context.getInvocationId(), logger, broker, fiscalCode, key, content);

                    Runtime.getRuntime().gc();
                } else {
                    logger.log(Level.SEVERE, () -> String.format("[id=%s][ValidationFunction] No match found in the input string.", context.getInvocationId()));
                }
            }
        }
    }

    public boolean validateBlob(String invocationId, Logger logger, String broker, String fiscalCode, String uploadKey, BinaryData content) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        try {
            // deserialize payment positions from JSON to Object
            PaymentPositions paymentPositions = objectMapper.readValue(content.toString(), PaymentPositions.class);
            Status status = StatusService.getInstance(logger).createStatus(invocationId, broker, fiscalCode, uploadKey, paymentPositions.getPaymentPositions().size());
            if (status.getUpload().getEnd() != null) { // already exist and upload is completed, so no-retry
                return false;
            }
            // call payment position object validation logic
            PaymentPositionValidator.validate(invocationId, logger, paymentPositions, fiscalCode, uploadKey);

            // create queue message
            List<PaymentPosition> list = paymentPositions.getPaymentPositions();
            for (int i = 0; i < list.size(); i += CHUNK_SIZE) {
                int endIndex = Math.min(i + CHUNK_SIZE, list.size());
                List<PaymentPosition> subList = list.subList(i, endIndex);

                PaymentPositionsMessage message = PaymentPositionsMessage.builder()
                        .uploadKey(uploadKey)
                        .organizationFiscalCode(fiscalCode)
                        .brokerCode(broker)
                        .retryCounter(0)
                        .paymentPositions(PaymentPositions.builder().paymentPositions(subList).build())
                        .build();
                objectMapper.disable(SerializationFeature.INDENT_OUTPUT); // remove useless whitespaces from message
                QueueService.enqueue(invocationId, logger, objectMapper.writeValueAsString(message), 0);
            }

            return true;
        } catch (JsonMappingException e) {
            logger.log(Level.SEVERE, () -> String.format("[id=%s][ValidationFunction] Processing function exception: %s, caused by: %s", invocationId, e.getMessage(), e.getCause()));
            return false;
        } catch (AppException | JsonProcessingException e) {
            logger.log(Level.SEVERE, () -> String.format("[id=%s][ValidationFunction] Processing function exception: %s, caused by: %s", invocationId, e.getMessage(), e.getCause()));
            return false;
        }
    }
}