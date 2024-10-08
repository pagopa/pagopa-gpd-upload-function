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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CRUDService {
    private static final Integer MAX_RETRY =
            System.getenv("MAX_RETRY") != null ? Integer.parseInt(System.getenv("MAX_RETRY")) : 2;
    private static final Integer RETRY_DELAY =
            System.getenv("RETRY_DELAY_IN_SECONDS") != null ? Integer.parseInt(System.getenv("RETRY_DELAY_IN_SECONDS")) : 300;
    private static final String LOG_ID = "[id=%s][upload=%s][CrudService] ";

    private final ObjectMapper om;
    private final DebtPositionMessage debtPositionMessage;
    private final Function<RequestGPD, ResponseGPD> method;
    private final StatusService statusService;
    private final ExecutionContext ctx;
    private Logger logger;
    private String id;

    public CRUDService(ExecutionContext context, Function<RequestGPD, ResponseGPD> method, DebtPositionMessage message, StatusService statusService) {
        this.debtPositionMessage = message;
        this.method = method;
        this.ctx = context;
        this.statusService = statusService;
        this.logger = ctx.getLogger();
        this.id = ctx.getInvocationId();
        om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
    }

    private ResponseGPD applyRequest(RequestGPD requestGPD) {
        logger.log(Level.INFO, () -> String.format("[id=%s][CrudService] Call GPD-Client", id));
        return method.apply(requestGPD);
    }

    // constraint: paymentPositions size less than max bulk item per call -> compliant by design(max queue message = 64KB = ~30 PaymentPosition)
    public void processRequestInBulk() throws AppException, JsonProcessingException {
        String uploadKey = debtPositionMessage.getUploadKey();
        String orgFiscalCode = debtPositionMessage.getOrganizationFiscalCode();

        logger.log(Level.INFO, () -> String.format(LOG_ID + "Process request in BULK", id, uploadKey));

        RequestGPD requestGPD = debtPositionMessage.getRequest(RequestTranslator.getInstance(), RequestGPD.Mode.BULK, Optional.empty());
        List<String> IUPDList = debtPositionMessage.getIUPDList();
        ResponseGPD response = applyRequest(requestGPD);

        if(!response.is2xxSuccessful()) {
            // if BULK creation wasn't successful, switch to single debt position creation
            Map<String, ResponseGPD> responseByIUPD = processRequestOneByOne(IUPDList);
            logger.log(Level.INFO, () -> String.format(LOG_ID + "Call Status update for %s IUPDs", id, uploadKey,responseByIUPD.keySet().size()));
            statusService.appendResponses(id, orgFiscalCode, uploadKey, responseByIUPD);
        } else {
            // if BULK creation was successful
            statusService.appendResponse(id, orgFiscalCode, uploadKey, IUPDList, response);
        }
    }

    private Map<String, ResponseGPD> processRequestOneByOne(List<String> IUPDList) throws JsonProcessingException {
        logger.log(Level.INFO, () -> String.format(LOG_ID + "Process request one-by-one", id, debtPositionMessage.getUploadKey()));
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

        logger.log(Level.INFO, () -> String.format(LOG_ID + "Retry message %s", id, debtPositionMessage.getUploadKey(), queueMessage.getUploadKey()));
        return QueueService.getInstance(logger).enqueue(id, om.writeValueAsString(queueMessage), RETRY_DELAY);
    }
}
