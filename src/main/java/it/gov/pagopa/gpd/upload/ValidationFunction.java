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
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.model.QueueMessage;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.UploadInput;
import it.gov.pagopa.gpd.upload.model.CRUDOperation;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import it.gov.pagopa.gpd.upload.repository.BlobRepository;
import it.gov.pagopa.gpd.upload.service.QueueService;
import it.gov.pagopa.gpd.upload.service.StatusService;
import it.gov.pagopa.gpd.upload.util.GPDValidator;

import java.time.LocalDateTime;
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
    private static final String LOG_PREFIX = "[id=%s][upload=%s][ValidationFunction]:";

    @FunctionName("BlobQueueEventFunction")
    public void run(
            @QueueTrigger(name = "BlobCreatedEventTrigger", queueName = "%BLOB_EVENTS_QUEUE%", connection = "GPD_SA_CONNECTION_STRING") String events,
            final ExecutionContext context) {

        Logger logger = context.getLogger();

        List<EventGridEvent> eventGridEvents = EventGridEvent.fromString(events);

        if (eventGridEvents.isEmpty()) {
            logger.log(Level.SEVERE, () -> String.format(LOG_PREFIX + "Empty event list", context.getInvocationId(), "-"));
            return; // skip event
        }

        for (EventGridEvent event : eventGridEvents) {
            if (event.getEventType().equals("Microsoft.Storage.BlobCreated")) {
                logger.log(Level.INFO, () -> String.format(LOG_PREFIX + "Call event type %s handler.", context.getInvocationId(), "-", event.getEventType()));

                StorageBlobCreatedEventData blobData = event.getData().toObject(StorageBlobCreatedEventData.class, new DefaultJsonSerializer());
                if (blobData.getContentLength() > 1e+8) { // if file greater than 100 MB
                    logger.log(Level.INFO, () -> "File size too large");
                    return; // skip event
                }
                if (blobData.getContentLength() == 0) {
                    logger.log(Level.INFO, () -> "File size equal to zero");
                    return; // skip event
                }

                logger.log(Level.INFO, () -> String.format(LOG_PREFIX + "Blob event subject: %s", context.getInvocationId(), "-", event.getSubject()));

                Pattern pattern = Pattern.compile("/containers/(\\w+)/blobs/(\\w+)/input/([\\w\\-\\h]+\\.[Jj][Ss][Oo][Nn])");
                Matcher matcher = pattern.matcher(event.getSubject());

                // Check if the pattern is found
                if (matcher.find()) {
                    String broker = matcher.group(1);    // broker container as broke_code
                    String fiscalCode = matcher.group(2);   // creditor institution directory
                    String filename = matcher.group(3);     // e.g. 77777777777c8a1.json

                    BinaryData content = this.downloadBlob(context, broker, fiscalCode, filename);
                    String key = filename.substring(0, filename.indexOf("."));

                    logger.log(Level.INFO, () -> String.format(LOG_PREFIX + "broker: %s, fiscalCode: %s, filename: %s",
                            context.getInvocationId(), key, broker, fiscalCode, filename));
                    try {
                        if(!this.validateBlob(context, broker, fiscalCode, key, content))
                            throw new AppException("Invalid blob");
                    } catch (AppException e) {
                        logger.log(Level.SEVERE, () -> String.format("[id=%s][ValidationFunction] Exception %s", context.getInvocationId(), e.getMessage()));
                    }

                    Runtime.getRuntime().gc();
                } else {
                    logger.log(Level.SEVERE, () -> String.format("[id=%s][ValidationFunction] No match found in the input string.", context.getInvocationId()));
                }
            }
        }
    }

    public boolean validateBlob(ExecutionContext ctx, String broker, String fiscalCode, String uploadKey, BinaryData content) throws AppException {
        int size = 0;
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.INDENT_OUTPUT); // remove useless whitespaces from message

        try {
            // deserialize UploadInput from JSON to Object
            UploadInput input = om.readValue(content.toString(), UploadInput.class);

            if(!input.validOneOf()) {
                return false;
            }

            List<PaymentPosition> pps = input.getPaymentPositions();
            List<String> iupds = input.getPaymentPositionIUPDs();

            if(pps != null)
                size = pps.size();
            else if(iupds != null)
                size = iupds.size();

            Status status = this.createStatus(ctx, broker, fiscalCode, uploadKey, size);
            if(pps != null) GPDValidator.validate(ctx, pps, fiscalCode, uploadKey);

            if (status.getUpload().getEnd() != null) { // already exist and upload is completed, so no-retry
                return false;
            }

            // enqueue chunk and other input to form message
            return enqueue(ctx, om, input.getOperation(), pps, iupds, uploadKey, fiscalCode, broker);
        } catch (JsonMappingException e) {
            StatusService.getInstance(ctx.getLogger()).updateStatusEndTime(ctx.getInvocationId(), fiscalCode, uploadKey, LocalDateTime.now());
            ctx.getLogger().log(Level.SEVERE, () -> String.format(LOG_PREFIX + "Processing function JsonMappingException: %s, caused by: %s",
                    ctx.getInvocationId(), uploadKey, e.getMessage(), e.getCause()));
            return false;
        } catch (AppException | JsonProcessingException e) {
            ctx.getLogger().log(Level.SEVERE, () -> String.format(LOG_PREFIX + "Processing function exception: %s, caused by: %s",
                    ctx.getInvocationId(), uploadKey, e.getMessage(), e.getCause()));
            return false;
        }
    }

    public BinaryData downloadBlob(ExecutionContext ctx, String broker, String fiscalCode, String filename) {
        return BlobRepository.getInstance(ctx.getLogger()).download(broker, fiscalCode, filename);
    }

    public Status createStatus(ExecutionContext ctx, String broker, String orgFiscalCode, String uploadKey, int size) throws AppException {
        return StatusService.getInstance(ctx.getLogger())
                                .createStatus(ctx.getInvocationId(), broker, orgFiscalCode, uploadKey, size);
    }

    public boolean enqueue(ExecutionContext ctx, ObjectMapper om, CRUDOperation operation, List<PaymentPosition> paymentPositions, List<String> IUPDList, String uploadKey, String fiscalCode, String broker) {
        QueueService queueService = QueueService.getInstance(ctx.getLogger());
        QueueMessage.QueueMessageBuilder builder = queueService.generateMessageBuilder(operation, uploadKey, fiscalCode, broker);
        return switch (operation) {
            case CREATE, UPDATE -> queueService.enqueueUpsertMessage(ctx, om, paymentPositions, builder, 0, QueueService.CHUNK_SIZE);
            case DELETE -> queueService.enqueueDeleteMessage(ctx, om, IUPDList, builder, 0);
        };
    }
}
