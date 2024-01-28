package it.gov.pagopa.gpd.upload.service;

import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.client.GPDClient;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;
import it.gov.pagopa.gpd.upload.repository.StatusRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class BlockService {
    private final String BLOCK_SIZE = System.getenv("BLOCK_SIZE");

    public void createPaymentPositionBlocks(Logger logger, String invocationId, String fc, String key, PaymentPositions pps, Status status) throws Exception {
        long t1 = System.currentTimeMillis();
        StatusService statusService = StatusService.getInstance(logger);
        GPDClient gpdClient = GPDClient.getInstance();

        int blockSize = Integer.parseInt(BLOCK_SIZE);
        int index = 0;
        int totalPosition = pps.getPaymentPositions().size();
        PaymentPositions block;
        while(index + blockSize < totalPosition) {
            logger.log(Level.INFO,
                    "Process block for payment positions from index " + index + ", block size: " + blockSize + ", total size: " + totalPosition);
            block = new PaymentPositions(pps.getPaymentPositions().subList(index, index+blockSize));
            ResponseGPD response = gpdClient.createBulkDebtPositions(fc, block, logger, invocationId);

            if(response.getStatus() != HttpStatus.CREATED.value()) {
                // if BULK creation wasn't successful, switch to single debt position creation
                for(PaymentPosition pp : block.getPaymentPositions()) {
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
            block = new PaymentPositions(pps.getPaymentPositions().subList(index, index+remainingPosition));
            ResponseGPD response = GPDClient.getInstance().createBulkDebtPositions(fc, block, logger, invocationId);

            if(response.getStatus() != HttpStatus.CREATED.value()) {
                // if BULK creation wasn't successful, switch to single debt position creation
                for(PaymentPosition pp : block.getPaymentPositions()) {
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
}
