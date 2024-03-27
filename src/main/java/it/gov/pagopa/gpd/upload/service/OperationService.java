package it.gov.pagopa.gpd.upload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.gpd.upload.entity.UploadMessage;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.*;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import it.gov.pagopa.gpd.upload.model.pd.MultipleIUPD;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class OperationService {
    private static final Integer MAX_RETRY =
            System.getenv("MAX_RETRY") != null ? Integer.parseInt(System.getenv("MAX_RETRY")) : 1;
    private static final Integer RETRY_DELAY =
            System.getenv("RETRY_DELAY_IN_SECONDS") != null ? Integer.parseInt(System.getenv("RETRY_DELAY_IN_SECONDS")) : 300;

    private ObjectMapper om;
    private UploadMessage msg;
    private Function<RequestGPD, ResponseGPD> method;
    private ExecutionContext ctx;

    public OperationService(ExecutionContext ctx, Function<RequestGPD, ResponseGPD> method, UploadMessage message) {
        this.msg = message;
        this.method = method;
        this.ctx = ctx;
        om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
    }

    private ResponseGPD applyRequest(RequestGPD requestGPD) {
        ResponseGPD response = method.apply(requestGPD);
        ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][OperationService] Call GPD-Client", ctx.getInvocationId()));
        return  response;
    }

    private RequestGPD.RequestGPDBuilder generateRequest(RequestGPD.Mode mode, String orgFiscalCode, Object modelGPD) throws JsonProcessingException {
        return RequestGPD.builder()
                            .mode(mode)
                            .orgFiscalCode(orgFiscalCode)
                            .logger(ctx.getLogger())
                            .invocationId(ctx.getInvocationId())
                            .body(om.writeValueAsString(modelGPD));
    }

    public void processBulkRequest() throws AppException, JsonProcessingException {
        // constraint: paymentPositions size less than max bulk item per call -> compliant by design(max queue message = 64KB = ~30 PaymentPosition)
        StatusService statusService = StatusService.getInstance(ctx.getLogger());

        RequestGPD requestGPD = switch (msg.getUploadOperation()) {
            case CREATE, UPDATE -> generateRequest(RequestGPD.Mode.BULK, msg.getOrganizationFiscalCode(), new PaymentPositions(msg.getPaymentPositions())).build();
            case DELETE -> generateRequest(RequestGPD.Mode.BULK, msg.getOrganizationFiscalCode(), new MultipleIUPD(msg.getPaymentPositionIUPDs())).build();
        };

        List<String> IUPDList = switch (msg.getUploadOperation()) {
            case CREATE, UPDATE -> msg.getPaymentPositions().stream()
                                           .map(PaymentPosition::getIupd)
                                           .collect(Collectors.toList());
            case DELETE -> msg.getPaymentPositionIUPDs();
        };

        ResponseGPD response = applyRequest(requestGPD);

        if(!response.is2xxSuccessful()) {
            // if BULK creation wasn't successful, switch to single debt position creation
            Map<String, ResponseGPD> responseByIUPD = processSingleRequest(IUPDList);
            ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][OperationService] Call Status update for %s IUPDs", ctx.getInvocationId(), responseByIUPD.keySet().size()));
            statusService.appendResponses(ctx.getInvocationId(), msg.getOrganizationFiscalCode(), msg.getUploadKey(), responseByIUPD);
        } else {
            // if BULK creation was successful
            statusService.appendResponse(ctx.getInvocationId(), msg.getOrganizationFiscalCode(), msg.getUploadKey(), IUPDList, response);
        }
    }

    private Map<String, ResponseGPD> processSingleRequest(List<String> IUPDList) throws JsonProcessingException {
        ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][OperationService] Single mode processing", ctx.getInvocationId()));
        Map<String, ResponseGPD> responseByIUPD = new HashMap<>();

        for(String IUPD: IUPDList) {
            RequestGPD requestGPD = switch (msg.getUploadOperation()) {
                case CREATE, UPDATE -> generateRequest(RequestGPD.Mode.SINGLE, msg.getOrganizationFiscalCode(),
                        new PaymentPositions(msg.getPaymentPositions().stream().filter(pp -> pp.getIupd().equals(IUPD)).toList())).build();
                case DELETE -> generateRequest(RequestGPD.Mode.SINGLE, msg.getOrganizationFiscalCode(),
                        new MultipleIUPD(List.of(new String[]{IUPD}))).build();
            };
            ResponseGPD response = applyRequest(requestGPD);
            responseByIUPD.put(IUPD, response);
        }

        // Selecting responses where retry == true
        Map<String, ResponseGPD> retryResponses = responseByIUPD.entrySet().stream()
                                                          .filter(entry -> entry.getValue().getRetryStep().equals(RetryStep.RETRY))
                                                          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (!retryResponses.isEmpty() && msg.getRetryCounter() < MAX_RETRY) {
            // Remove retry-responses from response-map and enqueue retry-responses
            responseByIUPD.entrySet().removeAll(retryResponses.entrySet());
            this.retry(updateMessageForRetry(retryResponses));
        }

        return responseByIUPD;
    }

    private UploadMessage updateMessageForRetry(Map<String, ResponseGPD> retryResponse) {
        msg.setRetryCounter(msg.getRetryCounter()+1);
        List<String> retryIUPD = retryResponse.keySet().stream().toList();

        switch (msg.getUploadOperation()) {
            case CREATE, UPDATE -> msg.setPaymentPositions(msg.getPaymentPositions().stream().
                                                                   filter(pp -> retryIUPD.contains(pp.getIupd())).toList());
            case DELETE -> msg.setPaymentPositionIUPDs(retryIUPD);
        };
        return msg;
    }

    public boolean retry(UploadMessage msg) throws JsonProcessingException {
        ctx.getLogger().log(Level.INFO, () -> String.format("[id=%s][OperationService] Retry!", ctx.getInvocationId()));
        return QueueService.enqueue(ctx.getInvocationId(), ctx.getLogger(), om.writeValueAsString(msg), RETRY_DELAY);
    }
}
