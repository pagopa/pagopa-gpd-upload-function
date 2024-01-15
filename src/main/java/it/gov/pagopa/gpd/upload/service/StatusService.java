package it.gov.pagopa.gpd.upload.service;

import it.gov.pagopa.gpd.upload.entity.FailedIUPD;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.entity.Upload;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.model.RetryStep;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositionsModel;
import it.gov.pagopa.gpd.upload.repository.StatusRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StatusService {
    private static StatusService instance = null;
    private Logger logger;
    private final int MESSAGE_MAX_CHAR_NUMBER = 150;

    public static StatusService getInstance(Logger logger) {
        if (instance == null) {
            instance = new StatusService(logger);
        }
        return instance;
    }

    private StatusService(Logger logger) {
        this.logger = logger;
    }

    public Status createStatus(String fiscalCode, String key, PaymentPositionsModel paymentPositionsModel) throws AppException {
        Status statusIfNotExist = Status.builder()
                                          .id(key)
                                          .fiscalCode(fiscalCode)
                                          .upload(Upload.builder()
                                                          .current(0)
                                                          .total(paymentPositionsModel.getPaymentPositions().size())
                                                          .successIUPD(new ArrayList<>())
                                                          .failedIUPDs(new ArrayList<>())
                                                          .start(LocalDateTime.now()).build())
                                          .build();
        Status status = StatusRepository.getInstance(logger).createIfNotExist(key, fiscalCode, statusIfNotExist);
        if(status.upload.getEnd() != null) {
            logger.log(Level.INFO, () -> "Upload already processed. Upload finished at " + status.upload.getEnd());
            return status;
        }

        return status;
    }

    public Status updateStatus(List<String> IUPDs, Status status, ResponseGPD response, int blockSize) {
        RetryStep responseRetryStep = response.getRetryStep();
        if(responseRetryStep.equals(RetryStep.DONE)) {
            ArrayList<String> successIUPDs = status.upload.getSuccessIUPD();
            successIUPDs.addAll(IUPDs);
            status.upload.setSuccessIUPD(successIUPDs);
        } else if(responseRetryStep.equals(RetryStep.ERROR) || responseRetryStep.equals(RetryStep.RETRY) || responseRetryStep.equals(RetryStep.NONE)  ) {
            FailedIUPD failedIUPD = FailedIUPD.builder()
                                            .details(response.getDetail().substring(0, Math.min(response.getDetail().length(), MESSAGE_MAX_CHAR_NUMBER)))
                                            .errorCode(response.getStatus())
                                            .skippedIUPDs(IUPDs)
                                            .build();
            status.upload.addFailures(failedIUPD);
        }
        status.upload.setCurrent(status.upload.getCurrent() + blockSize);

        return status;
    }
}
