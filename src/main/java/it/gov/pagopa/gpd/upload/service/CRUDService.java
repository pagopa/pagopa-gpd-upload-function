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
import java.util.stream.Collectors;

public class CRUDService {
    private static final Integer MAX_RETRY =
            System.getenv("MAX_RETRY") != null ? Integer.parseInt(System.getenv("MAX_RETRY")) : 2;
    private static final Integer RETRY_DELAY =
            System.getenv("RETRY_DELAY_IN_SECONDS") != null ? Integer.parseInt(System.getenv("RETRY_DELAY_IN_SECONDS")) : 300;

    private final ObjectMapper om;
    private final DebtPositionMessage debtPositionMessage;
    private final Function<RequestGPD, ResponseGPD> method;
    private final StatusService statusService;
    private final ExecutionContext ctx;

    public CRUDService(ExecutionContext context, Function<RequestGPD, ResponseGPD> method, DebtPositionMessage message, StatusService statusService) {
        this.debtPositionMessage = message;
        this.method = method;
        this.ctx = context;
        this.statusService = statusService;
        om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
    }

    private ResponseGPD applyRequest(RequestGPD requestGPD) {
        ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][OperationService] Call GPD-Client", ctx.getInvocationId()));
        return method.apply(requestGPD);
    }

    // constraint: paymentPositions size less than max bulk item per call -> compliant by design(max queue message = 64KB = ~30 PaymentPosition)
    public void processRequestInBulk() throws AppException, JsonProcessingException {
        ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][OperationService] Process request in BULK", ctx.getInvocationId()));

        RequestGPD requestGPD = debtPositionMessage.getRequest(RequestTranslator.getInstance(), RequestGPD.Mode.BULK, Optional.empty());
        List<String> IUPDList = debtPositionMessage.getIUPDList();
        ResponseGPD response = applyRequest(requestGPD);

        if(!response.is2xxSuccessful()) {
            // if BULK creation wasn't successful, switch to single debt position creation
            Map<String, ResponseGPD> responseByIUPD = processRequestOneByOne(IUPDList);
            ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][OperationService] Call Status update for %s IUPDs", ctx.getInvocationId(), responseByIUPD.keySet().size()));
            statusService.appendResponses(ctx.getInvocationId(), debtPositionMessage.getOrganizationFiscalCode(), debtPositionMessage.getUploadKey(), responseByIUPD);
        } else {
            // if BULK creation was successful
            statusService.appendResponse(ctx.getInvocationId(), debtPositionMessage.getOrganizationFiscalCode(), debtPositionMessage.getUploadKey(), IUPDList, response);
        }
    }

    private Map<String, ResponseGPD> processRequestOneByOne(List<String> IUPDList) throws JsonProcessingException {
        ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][OperationService] Single mode processing", ctx.getInvocationId()));
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
        ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][OperationService] Retry message %s", ctx.getInvocationId(), queueMessage.getUploadKey()));
        return QueueService.getInstance(ctx.getLogger()).enqueue(ctx.getInvocationId(), om.writeValueAsString(queueMessage), RETRY_DELAY);
    }
}
