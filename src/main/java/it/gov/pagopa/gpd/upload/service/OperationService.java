package it.gov.pagopa.gpd.upload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.gpd.upload.entity.PositionMessage;
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

public class OperationService {
    private static final Integer MAX_RETRY =
            System.getenv("MAX_RETRY") != null ? Integer.parseInt(System.getenv("MAX_RETRY")) : 1;
    private static final Integer RETRY_DELAY =
            System.getenv("RETRY_DELAY_IN_SECONDS") != null ? Integer.parseInt(System.getenv("RETRY_DELAY_IN_SECONDS")) : 300;

    private ObjectMapper om;
    private PositionMessage positionMessage;
    private Function<RequestGPD, ResponseGPD> method;
    private ExecutionContext ctx;

    public OperationService(ExecutionContext context, Function<RequestGPD, ResponseGPD> method, PositionMessage message) {
        this.positionMessage = message;
        this.method = method;
        this.ctx = context;
        om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
    }

    private ResponseGPD applyRequest(RequestGPD requestGPD) {
        ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][OperationService] Call GPD-Client", ctx.getInvocationId()));
        return method.apply(requestGPD);
    }

    public void processBulkRequest() throws AppException, JsonProcessingException {
        ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][OperationService] Process request in BULK", ctx.getInvocationId()));

        // constraint: paymentPositions size less than max bulk item per call -> compliant by design(max queue message = 64KB = ~30 PaymentPosition)
        StatusService statusService = StatusService.getInstance(ctx.getLogger());

        RequestGPD requestGPD = positionMessage.getRequest(RequestTranslator.getInstance(), RequestGPD.Mode.BULK, Optional.empty());
        List<String> IUPDList = positionMessage.getIUPDList();
        ResponseGPD response = applyRequest(requestGPD);

        if(!response.is2xxSuccessful()) {
            // if BULK creation wasn't successful, switch to single debt position creation
            Map<String, ResponseGPD> responseByIUPD = processSingleRequest(IUPDList);
            ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][OperationService] Call Status update for %s IUPDs", ctx.getInvocationId(), responseByIUPD.keySet().size()));
            statusService.appendResponses(ctx.getInvocationId(), positionMessage.getOrganizationFiscalCode(), positionMessage.getUploadKey(), responseByIUPD);
        } else {
            // if BULK creation was successful
            statusService.appendResponse(ctx.getInvocationId(), positionMessage.getOrganizationFiscalCode(), positionMessage.getUploadKey(), IUPDList, response);
        }
    }

    private Map<String, ResponseGPD> processSingleRequest(List<String> IUPDList) throws JsonProcessingException {
        ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][OperationService] Single mode processing", ctx.getInvocationId()));
        Map<String, ResponseGPD> responseByIUPD = new HashMap<>();

        for(String IUPD: IUPDList) {
            RequestGPD requestGPD = positionMessage.getRequest(RequestTranslator.getInstance(), RequestGPD.Mode.SINGLE, Optional.of(IUPD));
            ResponseGPD response = applyRequest(requestGPD);
            responseByIUPD.put(IUPD, response);
        }

        // Selecting responses where retry == true
        Map<String, ResponseGPD> retryResponses = responseByIUPD.entrySet().stream()
                                                          .filter(entry -> entry.getValue().getRetryStep().equals(RetryStep.RETRY))
                                                          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (!retryResponses.isEmpty() && positionMessage.getRetryCounter() < MAX_RETRY) {
            // Remove retry-responses from response-map and enqueue retry-responses
            responseByIUPD.entrySet().removeAll(retryResponses.entrySet());
            this.retry(updateMessageForRetry(retryResponses));
        }

        return responseByIUPD;
    }

    private QueueMessage updateMessageForRetry(Map<String, ResponseGPD> retryResponse) {
        positionMessage.setRetryCounter(positionMessage.getRetryCounter()+1);
        List<String> retryIUPD = retryResponse.keySet().stream().toList();
        return positionMessage.getQueueMessage(MessageTranslator.getInstance(), retryIUPD);
    }

    public boolean retry(QueueMessage msg) throws JsonProcessingException {
        ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][OperationService] Retry!", ctx.getInvocationId()));
        return QueueService.enqueue(ctx.getInvocationId(), ctx.getLogger(), om.writeValueAsString(msg), RETRY_DELAY);
    }
}
