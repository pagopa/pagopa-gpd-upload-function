package it.gov.pagopa.gpd.upload.service;

import it.gov.pagopa.gpd.upload.entity.ResponseEntry;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.entity.Upload;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.repository.StatusRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StatusService {
    private static volatile StatusService instance;
    private Logger logger;
    private final int MAX_DETAILS_LENGTH = 112;

    public static StatusService getInstance(Logger logger) {
        if (instance == null) {
            synchronized (StatusService.class) {
                if (instance == null) {
                    instance = new StatusService(logger);
                }
            }
        }
        return instance;
    }

    public StatusService() {}

    public StatusService(Logger logger) {
        this.logger = logger;
    }

    public Status createStatus(String invocationId, String broker, String fiscalCode, String key, int totalPosition) throws AppException {
        Status statusIfNotExist = Status.builder()
                                          .id(key)
                                          .brokerID(broker)
                                          .fiscalCode(fiscalCode)
                                          .upload(Upload.builder()
                                                          .current(0)
                                                          .total(totalPosition)
                                                          .responses(new ArrayList<>())
                                                          .start(LocalDateTime.now()).build())
                                          .build();
        Status status = getStatusRepository().createIfNotExist(invocationId, key, fiscalCode, statusIfNotExist);
        if (status.upload.getEnd() != null) {
            logger.log(Level.SEVERE, () -> String.format("[id=%s][StatusService]Upload already processed. Upload finished at: %s", invocationId, status.upload.getEnd()));
            return status;
        }

        return status;
    }

    // not thread safe could get an intermediate state
    public Status getStatus(String invocationId, String fiscalCode, String key) throws AppException {
        return getStatusRepository().getStatus(invocationId, key, fiscalCode);
    }

    // update only end-time of a status entry
    public synchronized Status updateStatusEndTime(String invocationId, String fiscalCode, String key, LocalDateTime endTime) throws AppException {
        Status status = getStatusRepository().getStatus(invocationId, key, fiscalCode);
        status.upload.setEnd(endTime);

        try {
            getStatusRepository().upsertStatus(invocationId, status.id, status);
        } catch (AppException e) {
            logger.log(Level.SEVERE, () -> String.format("[id=%s][StatusService] Error while update upload Status", invocationId));
            throw new AppException("Error while update upload Status");
        }

        return status;
    }

    // method overloading: handle a list of IUPDs and related response -> all IUPDs must be associated to the same response
    public void appendResponse(String invocationId, String fiscalCode, String key, List<String> iUPDs, ResponseGPD response) throws AppException {
        String detail = Optional.ofNullable(response.getDetail()).orElse("");
        ResponseEntry entry = ResponseEntry.builder()
                                              .statusCode(response.getStatus())
                                              .statusMessage(detail.substring(0, Math.min(detail.length(), MAX_DETAILS_LENGTH)))
                                              .requestIDs(iUPDs)
                                              .build();
        this.updateStatus(invocationId, fiscalCode, key, List.of(entry));
    }

    // method overloading: handle a map of GPD response
    public void appendResponses(String invocationId, String fiscalCode, String key, Map<String, ResponseGPD> responses) throws AppException {
        List<ResponseEntry> entries = new ArrayList<>();
        for (String iUPD : responses.keySet()) {
            ResponseGPD response = responses.get(iUPD);
            List<String> iUPDs = List.of(iUPD);
            String detail = Optional.ofNullable(response.getDetail()).orElse("");;
            ResponseEntry responseEntry = ResponseEntry.builder()
                                                  .statusCode(response.getStatus())
                                                  .statusMessage(detail.substring(0, Math.min(detail.length(), MAX_DETAILS_LENGTH)))
                                                  .requestIDs(iUPDs)
                                                  .build();
            entries.add(responseEntry);
        }

        this.updateStatus(invocationId, fiscalCode, key, entries);
    }


    public synchronized void updateStatus(String invocationId, String fiscalCode, String key, List<ResponseEntry> entries) throws AppException {
        try {
            Status status = getStatusRepository().getStatus(invocationId, key, fiscalCode);
            for(ResponseEntry entry : entries) {
                status.upload.addResponse(entry);
            }
            getStatusRepository().upsertStatus("invocationId", status.id, status);
        } catch (AppException e) {
            logger.log(Level.SEVERE, () -> String.format("[id=%s][StatusService] Error while update upload Status", "invocationId"));
            throw new AppException("Error while update upload Status");
        }
    }

    public StatusRepository getStatusRepository() {
        return StatusRepository.getInstance(logger);
    }
}
