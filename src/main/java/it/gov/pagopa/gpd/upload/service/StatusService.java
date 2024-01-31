package it.gov.pagopa.gpd.upload.service;

import it.gov.pagopa.gpd.upload.entity.ResponseEntry;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.entity.Upload;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;
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

    public Status createStatus(String broker, String fiscalCode, String key, PaymentPositions paymentPositions) throws AppException {
        Status statusIfNotExist = Status.builder()
                                          .id(key)
                                          .brokerID(broker)
                                          .fiscalCode(fiscalCode)
                                          .upload(Upload.builder()
                                                          .current(0)
                                                          .total(paymentPositions.getPaymentPositions().size())
                                                          .responses(new ArrayList<>())
                                                          .start(LocalDateTime.now()).build())
                                          .build();
        Status status = StatusRepository.getInstance(logger).createIfNotExist(key, fiscalCode, statusIfNotExist);
        if(status.upload.getEnd() != null) {
            logger.log(Level.INFO, () -> "Upload already processed. Upload finished at " + status.upload.getEnd());
            return status;
        }

        return status;
    }

    public Status updateStatus(Status status, List<String> IUPDs, ResponseGPD response) throws AppException {
        ResponseEntry responseEntry = ResponseEntry.builder()
                                              .statusCode(response.getStatus())
                                              .statusMessage(response.getDetail())
                                              .requestIDs(IUPDs)
                                              .build();
        status.upload.addResponse(responseEntry);
        status.upload.setCurrent(status.upload.getCurrent() + IUPDs.size());

        try {
            StatusRepository.getInstance(logger).upsertStatus(status.id, status);
        } catch (AppException e) {
            throw new AppException("Error while update upload Status");
        }

        return status;
    }
}
