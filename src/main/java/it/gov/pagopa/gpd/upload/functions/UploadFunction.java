package it.gov.pagopa.gpd.upload.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.entity.Upload;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.RetryStep;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositionsModel;
import it.gov.pagopa.gpd.upload.client.GpdClient;
import it.gov.pagopa.gpd.upload.repository.StatusRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
    @FunctionName("blobprocessor")
    public void run(
            @BlobTrigger(name = "file",
                    dataType = "binary",
                    path = "gpd-upload/input/{fiscalCode}/{name}",
                    connection = "GPD_SA_CONNECTION_STRING") byte[] content,
            @BindingName("fiscalCode") String fiscalCode,
            @BindingName("name") String filename,
            final ExecutionContext context
    ) {
        Logger logger = context.getLogger();
        logger.log(Level.INFO, () -> "Blob Trigger function executed at: " + LocalDateTime.now() + " for blob"
                                             + ", filename " + filename
                                             + ", fiscal code: " + fiscalCode
                                             + ", size : " + content.length + " bytes");

        String converted = new String(content, StandardCharsets.UTF_8);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            PaymentPositionsModel paymentPositionsModel = objectMapper.readValue(converted, PaymentPositionsModel.class);
            logger.log(Level.INFO, () -> "Payment positions size: " + paymentPositionsModel.getPaymentPositions().size());
            createPaymentPositionBlocks(logger, context.getInvocationId(), fiscalCode, filename.substring(0, filename.indexOf(".")), paymentPositionsModel);
        } catch (JsonProcessingException | AppException e) {
            logger.log(Level.INFO, () -> "Processing blob exception: " + e.getMessage());
        } catch (Exception e) {
            logger.log(Level.INFO, () -> "Processing blob exception: " + e.getMessage());
        }
    }


    public void createPaymentPositionBlocks(Logger logger, String invocationId, String fiscalCode, String key, PaymentPositionsModel paymentPositionsModel) throws Exception {
        long t1 = System.currentTimeMillis();
        Status statusIfNotExist = Status.builder()
                                    .id(key)
                                    .fiscalCode(fiscalCode)
                                    .upload(Upload.builder()
                                                    .current(0)
                                                    .total(paymentPositionsModel.getPaymentPositions().size())
                                                    .successIUPD(new ArrayList<>())
                                                    .failedIUPD(new ArrayList<>())
                                                    .start(LocalDateTime.now()).build())
                                    .build();
        Status status = StatusRepository.getInstance(logger).createIfNotExist(key, fiscalCode, statusIfNotExist);
        if(status.upload.getEnd() != null) {
            logger.log(Level.INFO, () -> "Upload already processed. Upload finished at " + status.upload.getEnd());
            return;
        }
        int blockSize = Integer.parseInt(BLOCK_SIZE);
        int index = 0;
        int totalPosition = paymentPositionsModel.getPaymentPositions().size();
        PaymentPositionsModel block;
        while(index + blockSize < totalPosition) {
            logger.log(Level.INFO,
                    "Process block for payment positions from index " + index + ", block size: " + blockSize + ", total size: " + totalPosition);
            block = new PaymentPositionsModel(paymentPositionsModel.getPaymentPositions().subList(index, index+blockSize));
            RetryStep response = GpdClient.getInstance().createDebtPositions(fiscalCode, block, logger, invocationId);
            List<String> IUPDs = block.getPaymentPositions().stream().map(item -> item.getIupd()).collect(Collectors.toList());
            this.updateStatus(IUPDs, status, response, blockSize);
            StatusRepository.getInstance(logger).upsertStatus(key, status);
            index += blockSize;
        }
        // process last block if remaining position size is greater than zero
        int remainingPosition = totalPosition - index;
        if(remainingPosition > 0) {
            logger.log(Level.INFO,
                    "Process last block for payment positions from index " + index + ", remaining position: " + remainingPosition + ", total size: " + totalPosition);
            block = new PaymentPositionsModel(paymentPositionsModel.getPaymentPositions().subList(index, index+remainingPosition));
            RetryStep response = GpdClient.getInstance().createDebtPositions(fiscalCode, block, logger, invocationId);
            List<String> IUPDs = block.getPaymentPositions().stream().map(pp -> pp.getIupd()).collect(Collectors.toList());
            this.updateStatus(IUPDs, status, response, remainingPosition);
            StatusRepository.getInstance(logger).upsertStatus(key, status);
        }
        if(status.upload.getCurrent() == status.upload.getTotal()) {
            status.upload.setEnd(LocalDateTime.now());
            StatusRepository.getInstance(logger).upsertStatus(key, status);
        }

        long uploadDuration = System.currentTimeMillis() - t1;
        logger.log(Level.INFO, "Elapsed upload blocks time: " + uploadDuration);
    }

    public Status updateStatus(List<String> IUPDs, Status status, RetryStep response, int blockSize) {
        if(response.equals(RetryStep.DONE)) {
            ArrayList<String> successIUPDs = status.upload.getSuccessIUPD();
            successIUPDs.addAll(IUPDs);
            status.upload.setSuccessIUPD(successIUPDs);
        } else if(response.equals(RetryStep.ERROR) || response.equals(RetryStep.RETRY) || response.equals(RetryStep.NONE)  ) {
            ArrayList<String> failedIUPDs = status.upload.getFailedIUPD();
            failedIUPDs.addAll(IUPDs);
            status.upload.setFailedIUPD(failedIUPDs);
        }
        status.upload.setCurrent(status.upload.getCurrent() + blockSize);

        return status;
    }
}
