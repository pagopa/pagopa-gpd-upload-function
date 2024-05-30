package it.gov.pagopa.gpd.upload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.gpd.upload.entity.DebtPositionMessage;
import it.gov.pagopa.gpd.upload.entity.ResponseEntry;
import it.gov.pagopa.gpd.upload.model.QueueMessage;
import it.gov.pagopa.gpd.upload.model.*;
import it.gov.pagopa.gpd.upload.repository.StatusRepository;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class CRUDService {
    private static final Integer MAX_RETRY =
            System.getenv("MAX_RETRY") != null ? Integer.parseInt(System.getenv("MAX_RETRY")) : 2;
    private static final Integer RETRY_DELAY =
            System.getenv("RETRY_DELAY_IN_SECONDS") != null ? Integer.parseInt(System.getenv("RETRY_DELAY_IN_SECONDS")) : 300;
    private static final String LOG_ID = "[id=%s][upload-key=%s][OperationService] ";
    private final int MAX_DETAILS_LENGTH = 112;

    private final ObjectMapper om;
    private final DebtPositionMessage debtPositionMessage;
    private final Function<RequestGPD, ResponseGPD> method;
    private final ExecutionContext ctx;

    public CRUDService(ExecutionContext context, Function<RequestGPD, ResponseGPD> method, DebtPositionMessage message) {
        this.debtPositionMessage = message;
        this.method = method;
        this.ctx = context;
        om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
    }

    private ResponseGPD applyRequest(RequestGPD requestGPD) {
        return method.apply(requestGPD);
    }

    // constraint: paymentPositions size less than max bulk item per call -> compliant by design(max queue message = 64KB = ~30 PaymentPosition)
    public void processRequestInBulk() throws JsonProcessingException {
        ctx.getLogger().log(Level.INFO, () -> String.format(LOG_ID + "Process request in BULK", ctx.getInvocationId(), debtPositionMessage.getUploadKey()));

        RequestGPD requestGPD = debtPositionMessage.getRequest(RequestTranslator.getInstance(), RequestGPD.Mode.BULK, Optional.empty());
        List<String> IUPDList = debtPositionMessage.getIUPDList();
        ResponseGPD response = applyRequest(requestGPD);

        if(!response.is2xxSuccessful()) {
            // if BULK creation wasn't successful, switch to single debt position creation
            Map<String, ResponseGPD> responseByIUPD = processRequestOneByOne(IUPDList);

            ctx.getLogger().log(Level.INFO, () -> String.format(LOG_ID + "Call Status update for %s IUPDs",
                    ctx.getInvocationId(), debtPositionMessage.getUploadKey(), responseByIUPD.keySet().size()));
            List<ResponseEntry> entries = get(responseByIUPD);
            entries.forEach(entry -> StatusRepository.getInstance(ctx.getLogger()).increment(debtPositionMessage.getUploadKey(), debtPositionMessage.getOrganizationFiscalCode(), entry));
        } else {
            // if BULK creation was successful
            ResponseEntry entry = get(response, IUPDList);
            StatusRepository.getInstance(ctx.getLogger()).increment(debtPositionMessage.getUploadKey(), debtPositionMessage.getOrganizationFiscalCode(), entry);
        }
    }

    private Map<String, ResponseGPD> processRequestOneByOne(List<String> IUPDList) throws JsonProcessingException {
        ctx.getLogger().log(Level.INFO, () -> String.format(LOG_ID + "Process request one-by-one", ctx.getInvocationId(), debtPositionMessage.getUploadKey()));
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
        ctx.getLogger().log(Level.INFO, () -> String.format(LOG_ID + "Retry message %s",
                ctx.getInvocationId(), debtPositionMessage.getUploadKey(), queueMessage.getUploadKey()));
        return QueueService.getInstance(ctx.getLogger()).enqueue(ctx.getInvocationId(), om.writeValueAsString(queueMessage), RETRY_DELAY);
    }

    private ResponseEntry get(ResponseGPD response, List<String> IUPDList) {
        String detail = Optional.ofNullable(response.getDetail()).orElse("");
        return ResponseEntry.builder()
                .statusCode(response.getStatus())
                .statusMessage(detail.substring(0, Math.min(detail.length(), MAX_DETAILS_LENGTH)))
                .requestIDs(IUPDList)
                .build();
    }

    private List<ResponseEntry> get(Map<String, ResponseGPD> responses) {
        List<ResponseEntry> entries = new ArrayList<>();
        for (String iUPD : responses.keySet()) {
            ResponseGPD response = responses.get(iUPD);
            List<String> IUPDList = List.of(iUPD);
            String detail = Optional.ofNullable(response.getDetail()).orElse("");
            ResponseEntry responseEntry = ResponseEntry.builder()
                    .statusCode(response.getStatus())
                    .statusMessage(detail.substring(0, Math.min(detail.length(), MAX_DETAILS_LENGTH)))
                    .requestIDs(IUPDList)
                    .build();
            entries.add(responseEntry);
        }
        return entries;
    }
}
