package it.gov.pagopa.gpd.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;

import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.gpd.upload.entity.ResponseEntry;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositionModel;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositionsModel;
import it.gov.pagopa.gpd.upload.client.GPDClient;
import it.gov.pagopa.gpd.upload.repository.StatusRepository;
import it.gov.pagopa.gpd.upload.service.StatusService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Azure Functions with Azure Blob trigger to create debt positions in bulk when a new file has been uploaded.
 */
public class UploadFunction {

    private final String BLOCK_SIZE = System.getenv("BLOCK_SIZE");

    /**
     * This function will be invoked when a new or updated blob is detected at the
     * specified path. The blob contents are provided as input to this function.
     */
    @FunctionName("uploadBlobProcessor")
    public void run(
            @BlobTrigger(name = "file",
                    dataType = "binary",
                    path = "broker/{organizationFiscalCode}/input/{filename}",
                    connection = "GPD_SA_CONNECTION_STRING") byte[] content,
            @BindingName("organizationFiscalCode") String organizationFiscalCode,
            @BindingName("filename") String filename,
            @BlobOutput(
                    name = "target",
                    path = "broker/{organizationFiscalCode}/output/report_{filename}",
                    connection = "GPD_SA_CONNECTION_STRING")
            OutputBinding<String> outputBlob,
            final ExecutionContext context
    ) {
        Logger logger = context.getLogger();
        logger.log(Level.INFO, () -> "Blob Trigger function executed at: " + LocalDateTime.now() + " for blob"
                                             + ", filename " + filename
                                             + ", fiscal code: " + organizationFiscalCode
                                             + ", size : " + content.length + " bytes");

        String converted = new String(content, StandardCharsets.UTF_8);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.registerModule(new JavaTimeModule());
        String key = filename.substring(0, filename.indexOf("."));

        try {
            // deserialize payment positions from JSON to Object
            PaymentPositionsModel pps = objectMapper.readValue(converted, PaymentPositionsModel.class);
            Status status = StatusService.getInstance(logger).createStatus(organizationFiscalCode, key, pps);
            logger.log(Level.INFO, () -> "Payment positions size: " + pps.getPaymentPositions().size());
            // function logic: validation and block upload to GPD-Core
            validate(logger, pps, status);
            createPaymentPositionBlocks(logger, context.getInvocationId(), organizationFiscalCode, key, pps, status);
            // write report in output container
            outputBlob.setValue(objectMapper.writeValueAsString(status));
        } catch (Exception e) {
            logger.log(Level.INFO, () -> "Processing function exception: " + e.getMessage() + ", caused by: " + e.getCause());
            // describe exception in blob processing in output container
            outputBlob.setValue("The input file cannot be processed due to an exception.");
        }
    }


    public void createPaymentPositionBlocks(Logger logger, String invocationId, String fc, String key, PaymentPositionsModel pps, Status status) throws Exception {
        long t1 = System.currentTimeMillis();
        StatusService statusService = StatusService.getInstance(logger);
        GPDClient gpdClient = GPDClient.getInstance();

        int blockSize = Integer.parseInt(BLOCK_SIZE);
        int index = 0;
        int totalPosition = pps.getPaymentPositions().size();
        PaymentPositionsModel block;
        while(index + blockSize < totalPosition) {
            logger.log(Level.INFO,
                    "Process block for payment positions from index " + index + ", block size: " + blockSize + ", total size: " + totalPosition);
            block = new PaymentPositionsModel(pps.getPaymentPositions().subList(index, index+blockSize));
            ResponseGPD response = gpdClient.createBulkDebtPositions(fc, block, logger, invocationId);

            if(response.getStatus() != HttpStatus.CREATED.value()) {
                // if BULK creation wasn't successful, switch to single debt position creation
                for(PaymentPositionModel pp : block.getPaymentPositions()) {
                    response = gpdClient.createDebtPosition(fc, pp, logger, invocationId);
                    statusService.updateStatus(status, List.of(pp.getIupd()), response);
                }
            } else {
                // if BULK creation was successful
                List<String> IUPDs = block.getPaymentPositions().stream().map(pp -> pp.getIupd()).collect(Collectors.toList());
                statusService.updateStatus(status, IUPDs, response);
            }
            index += blockSize;
        }
        // process last block if remaining position size is greater than zero
        int remainingPosition = totalPosition - index;
        if(remainingPosition > 0) {
            logger.log(Level.INFO,
                    "Process last block for payment positions from index " + index + ", remaining position: " + remainingPosition + ", total size: " + totalPosition);
            block = new PaymentPositionsModel(pps.getPaymentPositions().subList(index, index+remainingPosition));
            ResponseGPD response = GPDClient.getInstance().createBulkDebtPositions(fc, block, logger, invocationId);

            if(response.getStatus() != HttpStatus.CREATED.value()) {
                // if BULK creation wasn't successful, switch to single debt position creation
                for(PaymentPositionModel pp : block.getPaymentPositions()) {
                    response = gpdClient.createDebtPosition(fc, pp, logger, invocationId);
                    statusService.updateStatus(status, List.of(pp.getIupd()), response);
                }
            } else {
                // if BULK creation was successful
                List<String> IUPDs = block.getPaymentPositions().stream().map(pp -> pp.getIupd()).collect(Collectors.toList());
                statusService.updateStatus(status, IUPDs, response);
            }
            index += remainingPosition;
        }
        if(status.upload.getCurrent() == status.upload.getTotal()) {
            status.upload.setEnd(LocalDateTime.now());
            StatusRepository.getInstance(logger).upsertStatus(key, status);
        }

        long uploadDuration = System.currentTimeMillis() - t1;
        logger.log(Level.INFO, "Elapsed upload blocks time: " + uploadDuration);
    }

    private void validate(Logger logger, PaymentPositionsModel paymentPositionsModel, Status status) throws AppException {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<PaymentPositionModel>> violations;
        int invalidPosition = 0;

        Iterator<PaymentPositionModel> iterator = paymentPositionsModel.getPaymentPositions().iterator();
        while (iterator.hasNext()) {
            PaymentPositionModel pp = iterator.next();
            violations =  validator.validate(pp);

            if (!violations.isEmpty()) {
                ConstraintViolation<PaymentPositionModel> violation = violations.stream().findFirst().orElse(null);
                String details = (violation != null ? violation.getMessage() : "");

                ResponseEntry responseEntry = ResponseEntry.builder()
                                                      .statusCode(HttpStatus.BAD_REQUEST.value())
                                                      .statusMessage(details)
                                                      .requestIDs(List.of(pp.getIupd()))
                                                      .build();
                status.upload.addResponse(responseEntry);
                invalidPosition++;
                iterator.remove();

                for(ConstraintViolation<PaymentPositionModel> v : violations) {
                    logger.log(Level.INFO, "Payment position " + pp.getIupd() + " is not valid, violation: " + v.getMessage());
                }
            }
        }

        status.upload.setCurrent(status.upload.getCurrent() + invalidPosition);
        logger.log(Level.INFO, status.toString());
        StatusRepository.getInstance(logger).upsertStatus(status.id, status);
    }
}
