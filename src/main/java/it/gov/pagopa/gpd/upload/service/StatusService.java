package it.gov.pagopa.gpd.upload.service;

import it.gov.pagopa.gpd.upload.entity.ResponseEntry;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.entity.Upload;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.model.enumeration.ServiceType;
import it.gov.pagopa.gpd.upload.repository.StatusRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.azure.functions.HttpStatus;

public class StatusService {
	private static final Logger log = LoggerFactory.getLogger(StatusService.class);
    private static volatile StatusService instance;

    public static StatusService getInstance() {
        if (instance == null) {
            synchronized (StatusService.class) {
                if (instance == null) {
                    instance = new StatusService();
                }
            }
        }
        return instance;
    }

    public Status createStatus(String invocationId, String broker, String fiscalCode, String key, int totalPosition, ServiceType serviceType) throws AppException {
        Status statusIfNotExist = Status.builder()
                .id(key)
                .brokerID(broker)
                .fiscalCode(fiscalCode)
                .serviceType(serviceType)
                .upload(Upload.builder()
                        .current(0)
                        .total(totalPosition)
                        .responses(new ArrayList<>())
                        .start(LocalDateTime.now()).build())
                .build();
        Status status = getStatusRepository().createIfNotExist(invocationId, key, fiscalCode, statusIfNotExist);
        if (status.upload.getEnd() != null) {
        	log.error("[id={}][StatusService] Upload already processed. Upload finished at: {}",
        	        invocationId, status.upload.getEnd());
            return status;
        }

        return status;
    }

    // not thread safe could get an intermediate state
    public Status getStatus(String invocationId, String fiscalCode, String key) throws AppException {
        return getStatusRepository().getStatus(invocationId, key, fiscalCode);
    }

    // end-time partial update operation
    public boolean updateStatusEndTime(String fiscalCode, String key, LocalDateTime endTime) {
        return getStatusRepository().partialUpdate(key, fiscalCode, endTime);
    }
    
    public synchronized boolean failStatus(String invocationId, String broker, String fiscalCode, String key, String failureMessage) {
        try {
            // get previous status to update.
            Status status = getStatusRepository().getStatus(invocationId, key, fiscalCode);

            if (status == null) {
            	log.error("[id={}][StatusService] Upload status not found for broker={}, key={} and fiscalCode={}. " +
                        "A fallback failed status will be created.",
                invocationId, broker, key, fiscalCode);

                status = buildFallbackFailedStatus(broker, fiscalCode, key);
            }

            if (status.upload == null) {
                status.upload = Upload.builder()
                        .current(0)
                        .total(0)
                        .responses(new ArrayList<>())
                        .start(LocalDateTime.now())
                        .build();
            }

            if (status.upload.getResponses() == null) {
                status.upload.setResponses(new ArrayList<>());
            }

            status.upload.getResponses().add(ResponseEntry.builder()
            		.statusCode(HttpStatus.PAYLOAD_TOO_LARGE.value()) // HTTP 413 "Content Too Large"
                    .statusMessage(HttpStatus.PAYLOAD_TOO_LARGE.value() + "-" + failureMessage)
                    .requestIDs(List.of())
                    .build());

            status.upload.setEnd(LocalDateTime.now());

            getStatusRepository().upsertStatus(invocationId, status.id, status);
            return true;

        } catch (AppException e) {
        	log.error("[id={}][StatusService] Error while marking upload {} as failed",
        	        invocationId, key, e);
            return false;
        }
    }

    public synchronized void updateStatus(String invocationId, String fiscalCode, String key, List<ResponseEntry> entries) throws AppException {
        try {
            Status status = getStatusRepository().getStatus(invocationId, key, fiscalCode);
            for (ResponseEntry entry : entries) {
            	log.error("[id={}][StatusService] Add response {}",
            	        invocationId, entry.getStatusMessage());

                status.upload.addResponse(entry);
            }
            getStatusRepository().upsertStatus(invocationId, status.id, status);
        } catch (AppException e) {
        	log.error("[id={}][StatusService] Error while update upload Status",
        	        invocationId, e);
            throw new AppException("Error while update upload Status");
        }
    }

    // method overloading: handle a list of IUPDs and related response -> all IUPDs must be associated to the same response
    public void appendResponse(String invocationId, String fiscalCode, String key, List<String> iupds, ResponseGPD response) throws AppException {
        ResponseEntry entry = ResponseEntry.builder()
                .statusCode(response.getStatus())
                .statusMessage(Optional.ofNullable(response.getDetail()).orElse(""))
                .requestIDs(iupds)
                .build();
        this.updateStatus(invocationId, fiscalCode, key, List.of(entry));
    }

    // method overloading: handle a map of GPD response
    public void appendResponses(String invocationId, String fiscalCode, String key, Map<String, ResponseGPD> responses) throws AppException {
        List<ResponseEntry> entries = new ArrayList<>();
        for (String iupd : responses.keySet()) {
            ResponseGPD response = responses.get(iupd);
            ResponseEntry responseEntry = ResponseEntry.builder()
                    .statusCode(response.getStatus())
                    .statusMessage(Optional.ofNullable(response.getDetail()).orElse(""))
                    .requestIDs(List.of(iupd))
                    .build();
            entries.add(responseEntry);
        }

        this.updateStatus(invocationId, fiscalCode, key, entries);
    }

    public StatusRepository getStatusRepository() {
        return StatusRepository.getInstance();
    }
    
    private Status buildFallbackFailedStatus(String broker, String fiscalCode, String key) {
        return Status.builder()
                .id(key)
                .brokerID(broker)
                .fiscalCode(fiscalCode)
                .serviceType(ServiceType.GPD)
                .upload(Upload.builder()
                        .current(0)
                        .total(0)
                        .responses(new ArrayList<>())
                        .start(LocalDateTime.now())
                        .build())
                .build();
    }
}
