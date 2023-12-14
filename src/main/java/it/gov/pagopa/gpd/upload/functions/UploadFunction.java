package it.gov.pagopa.gpd.upload.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositionsModel;
import it.gov.pagopa.gpd.upload.client.GpdClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Azure Functions with Azure Blob trigger to create debt positions in bulk when a new file has been uploaded.
 */
public class UploadFunction {

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
        int blockSize = 2;
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


            int index = 0;
            int totalPosition = paymentPositionsModel.getPaymentPositions().size();
            PaymentPositionsModel block;
            while(index + blockSize < totalPosition) {
                logger.log(Level.INFO,
                        "Process block for payment positions from index " + index + ", block size: " + blockSize + ", total size: " + totalPosition);
                block = new PaymentPositionsModel(paymentPositionsModel.getPaymentPositions().subList(index, index+blockSize));
                GpdClient.getInstance().createDebtPositions(fiscalCode, block, logger, context.getInvocationId());
                index += blockSize;
            }
            // process last block if remaining position size is greater than zero
            int remainingPosition = totalPosition - index;
            if(remainingPosition > 0) {
                logger.log(Level.INFO,
                        "Process last block for payment positions from index " + index + ", remaining position: " + remainingPosition + ", total size: " + totalPosition);
                block = new PaymentPositionsModel(paymentPositionsModel.getPaymentPositions().subList(index, index+remainingPosition));
                GpdClient.getInstance().createDebtPositions(fiscalCode, block, logger, context.getInvocationId());
            }
        } catch (JsonProcessingException e) {
            logger.log(Level.INFO, () -> "Processing blob exception: " + e.getMessage());
        }
    }
}
