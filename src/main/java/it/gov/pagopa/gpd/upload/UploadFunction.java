package it.gov.pagopa.gpd.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;

import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.gpd.upload.entity.FailedIUPD;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositionModel;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositionsModel;
import it.gov.pagopa.gpd.upload.client.GpdClient;
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
    private final int BAD_REQUEST = 400;

    /**
     * This function will be invoked when a new or updated blob is detected at the
     * specified path. The blob contents are provided as input to this function.
     */
    @FunctionName("upload-blob-processor")
    public void run(
            @BlobTrigger(name = "file",
                    dataType = "binary",
                    path = "broker/{organizationFiscalCode}/input/{filename}",
                    connection = "GPD_SA_CONNECTION_STRING") byte[] content,
            @BindingName("organizationFiscalCode") String organizationFiscalCode,
            @BindingName("filename") String filename,
            @BlobOutput(
                    name = "target",
                    path = "broker/output/{organizationFiscalCode}/result_{filename}",
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
            // write status in output container
            outputBlob.setValue(objectMapper.writeValueAsString(status));
        } catch (Exception e) {
            logger.log(Level.INFO, () -> "Processing function exception: " + e.getMessage());
        }
    }


    public void createPaymentPositionBlocks(Logger logger, String invocationId, String fc, String key, PaymentPositionsModel pps, Status status) throws Exception {
        long t1 = System.currentTimeMillis();

        int blockSize = Integer.parseInt(BLOCK_SIZE);
        int index = 0;
        int totalPosition = pps.getPaymentPositions().size();
        PaymentPositionsModel block;
        while(index + blockSize < totalPosition) {
            logger.log(Level.INFO,
                    "Process block for payment positions from index " + index + ", block size: " + blockSize + ", total size: " + totalPosition);
            block = new PaymentPositionsModel(pps.getPaymentPositions().subList(index, index+blockSize));
            ResponseGPD response = GpdClient.getInstance().createDebtPositions(fc, block, logger, invocationId);
            List<String> IUPDs = block.getPaymentPositions().stream().map(item -> item.getIupd()).collect(Collectors.toList());
            StatusService.getInstance(logger).updateStatus(IUPDs, status, response, blockSize);
            StatusRepository.getInstance(logger).upsertStatus(key, status);
            index += blockSize;
        }
        // process last block if remaining position size is greater than zero
        int remainingPosition = totalPosition - index;
        if(remainingPosition > 0) {
            logger.log(Level.INFO,
                    "Process last block for payment positions from index " + index + ", remaining position: " + remainingPosition + ", total size: " + totalPosition);
            block = new PaymentPositionsModel(pps.getPaymentPositions().subList(index, index+remainingPosition));
            ResponseGPD response = GpdClient.getInstance().createDebtPositions(fc, block, logger, invocationId);
            List<String> IUPDs = block.getPaymentPositions().stream().map(pp -> pp.getIupd()).collect(Collectors.toList());
            StatusService.getInstance(logger).updateStatus(IUPDs, status, response, remainingPosition);
            StatusRepository.getInstance(logger).upsertStatus(key, status);
        }
        if(status.upload.getCurrent() == status.upload.getTotal()) {
            status.upload.setEnd(LocalDateTime.now());
            StatusRepository.getInstance(logger).upsertStatus(key, status);
        }

        long uploadDuration = System.currentTimeMillis() - t1;
        logger.log(Level.INFO, "Elapsed upload blocks time: " + uploadDuration);
    }

    private void validate(Logger logger, PaymentPositionsModel paymentPositionsModel, Status status) {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<PaymentPositionModel>> violations;
        ArrayList<FailedIUPD> failedIUPDs = status.upload.getFailedIUPDs();

        List<String> skippedIUPDs;
        Iterator<PaymentPositionModel> iterator = paymentPositionsModel.getPaymentPositions().iterator();
        while (iterator.hasNext()) {
            PaymentPositionModel pp = iterator.next();
            violations =  validator.validate(pp);

            if (!violations.isEmpty()) {
                skippedIUPDs = new ArrayList<>();
                skippedIUPDs.add(pp.getIupd());
                ConstraintViolation<PaymentPositionModel> violation = violations.stream().findFirst().orElse(null);
                String details = (violation != null ? violation.getMessage() : "");
                FailedIUPD failedIUPD = FailedIUPD.builder()
                                                .errorCode(BAD_REQUEST)
                                                .details("BAD REQUEST " + details)
                                                .skippedIUPDs(skippedIUPDs).build();
                failedIUPDs.add(failedIUPD);
                iterator.remove();

                for(ConstraintViolation<PaymentPositionModel> v : violations) {
                    logger.log(Level.INFO, "Payment position " + pp.getIupd() + " is not valid, violation: " + v.getMessage());
                }
            } else {
                logger.log(Level.INFO, "Payment position list is valid");
            }
        }

        status.upload.setCurrent(status.upload.getCurrent() + failedIUPDs.size());
        status.upload.setFailedIUPDs(failedIUPDs);
    }
}
