package it.gov.pagopa.gpd.upload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.gpd.upload.entity.DebtPositionMessage;
import it.gov.pagopa.gpd.upload.model.QueueMessage;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;

public class CRUDService {
	private static final Logger log = LoggerFactory.getLogger(CRUDService.class);
    private static final Integer MAX_RETRY =
            System.getenv("MAX_RETRY") != null ? Integer.parseInt(System.getenv("MAX_RETRY")) : 2;
    private static final Integer RETRY_DELAY =
            System.getenv("RETRY_DELAY_IN_SECONDS") != null ? Integer.parseInt(System.getenv("RETRY_DELAY_IN_SECONDS")) : 300;
    private static final String LOG_ID = "[id={}][upload={}][CrudService] ";

    private final ObjectMapper om;
    private final DebtPositionMessage debtPositionMessage;
    private final Function<RequestGPD, ResponseGPD> method;
    private final StatusService statusService;
    private final ExecutionContext ctx;
    private String id;

    public CRUDService(ExecutionContext context, Function<RequestGPD, ResponseGPD> method, DebtPositionMessage message, StatusService statusService) {
        this.debtPositionMessage = message;
        this.method = method;
        this.ctx = context;
        this.statusService = statusService;
        this.id = ctx.getInvocationId();
        om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
    }

    private ResponseGPD applyRequest(RequestGPD requestGPD) {
    	log.info("[id={}][CrudService] Call GPD-Client", id);
        return method.apply(requestGPD);
    }

    // constraint: paymentPositions size less than max bulk item per call -> compliant by design(max queue message = 64KB = ~30 PaymentPosition)
    public void processRequestInBulk() throws AppException, JsonProcessingException {
        String uploadKey = debtPositionMessage.getUploadKey();
        String orgFiscalCode = debtPositionMessage.getOrganizationFiscalCode();

        log.info(LOG_ID + "Process request in BULK", id, uploadKey);

        RequestGPD requestGPD = debtPositionMessage.getRequest(RequestTranslator.getInstance(), RequestGPD.Mode.BULK, Optional.empty());
        List<String> IUPDList = debtPositionMessage.getIUPDList();
        ResponseGPD response = applyRequest(requestGPD);

        if(!response.is2xxSuccessful()) {
        	log.warn(LOG_ID + "Bulk request failed with status {}. Switching to one-by-one processing",
                    id, uploadKey, response.getStatus());
            // if BULK creation wasn't successful, switch to single debt position creation
            Map<String, ResponseGPD> responseByIUPD = processRequestOneByOne(IUPDList);
            log.info(LOG_ID + "Call Status update for {} IUPDs",
                    id, uploadKey, responseByIUPD.keySet().size());
            statusService.appendResponses(id, orgFiscalCode, uploadKey, responseByIUPD);
        } else {
        	log.info(LOG_ID + "Bulk request completed successfully with status {} for {} IUPDs",
                    id, uploadKey, response.getStatus(), IUPDList.size());
            // if BULK creation was successful
            statusService.appendResponse(id, orgFiscalCode, uploadKey, IUPDList, response);
        }
    }

    private Map<String, ResponseGPD> processRequestOneByOne(List<String> IUPDList) throws JsonProcessingException {
    	log.info(LOG_ID + "Processing {} IUPDs one-by-one",
    	        id, debtPositionMessage.getUploadKey(), IUPDList.size());
        Map<String, ResponseGPD> responseByIUPD = new HashMap<>();

        for(String IUPD: IUPDList) {
            RequestGPD requestGPD = debtPositionMessage.getRequest(RequestTranslator.getInstance(), RequestGPD.Mode.SINGLE, Optional.of(IUPD));
            ResponseGPD response = applyRequest(requestGPD);
            responseByIUPD.put(IUPD, response);
        }

        // Selecting responses where retry == true
        Map<String, ResponseGPD> retryResponses = responseByIUPD.entrySet().stream()
                                                          .filter(entry -> entry.getValue().getRetryStep().equals(RetryStep.RETRY))
                                                          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (!retryResponses.isEmpty() && debtPositionMessage.getRetryCounter() < MAX_RETRY) {
        	log.warn(LOG_ID + "{} IUPDs require retry. Current retryCounter={}, maxRetry={}",
                    id,
                    debtPositionMessage.getUploadKey(),
                    retryResponses.size(),
                    debtPositionMessage.getRetryCounter(),
                    MAX_RETRY);
            // Remove retry-responses from response-map and enqueue retry-responses
            responseByIUPD.entrySet().removeAll(retryResponses.entrySet());
            this.retry(retryResponses);
        }

        return responseByIUPD;
    }

    public boolean retry(Map<String, ResponseGPD> retryResponse) throws JsonProcessingException {
        debtPositionMessage.setRetryCounter(debtPositionMessage.getRetryCounter()+1);
        List<String> retryIUPD = retryResponse.keySet().stream().toList();
        QueueMessage queueMessage = debtPositionMessage.getQueueMessage(MessageTranslator.getInstance(), retryIUPD);

        log.info(LOG_ID + "Retry message {} with retryCounter={} and delay={} seconds",
                id,
                debtPositionMessage.getUploadKey(),
                queueMessage.getUploadKey(),
                debtPositionMessage.getRetryCounter(),
                RETRY_DELAY);
        return QueueService.getInstance().enqueue(id, om.writeValueAsString(queueMessage), RETRY_DELAY);
    }
}
