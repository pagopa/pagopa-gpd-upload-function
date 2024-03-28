package it.gov.pagopa.gpd.upload.functions.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.entity.PositionMessage;
import it.gov.pagopa.gpd.upload.entity.UpsertMessage;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.functions.utils.TestUtil;
import it.gov.pagopa.gpd.upload.model.RequestGPD;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.model.RetryStep;
import it.gov.pagopa.gpd.upload.repository.StatusRepository;
import it.gov.pagopa.gpd.upload.service.OperationService;
import it.gov.pagopa.gpd.upload.service.StatusService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OperationServiceTest {

    public enum ResultType {SUCCESS, FAIL, NOT_FOUND}

    private final ExecutionContext context = Mockito.mock(ExecutionContext.class);

    @Test
    void test1() throws AppException, JsonProcessingException {
        when(context.getLogger()).thenReturn(Logger.getLogger("gpd-upload-test-logger"));
        Function<RequestGPD, ResponseGPD> method = getMethod(ResultType.SUCCESS);
        PositionMessage positionMessage = new UpsertMessage(TestUtil.getCreateQueueMessage());
        OperationService operationService = new OperationService(context, method, positionMessage, new EStatusService());
        operationService.processBulkRequest();
    }

    @Test
    void test2() throws AppException, JsonProcessingException {
        when(context.getLogger()).thenReturn(Logger.getLogger("gpd-upload-test-logger"));
        Function<RequestGPD, ResponseGPD> method = getMethod(ResultType.NOT_FOUND);
        PositionMessage positionMessage = new UpsertMessage(TestUtil.getCreateQueueMessage());
        OperationService operationService = new OperationService(context, method, positionMessage, new EStatusService());
        operationService.processBulkRequest();
    }

    @Test
    void test3() {
        when(context.getLogger()).thenReturn(Logger.getLogger("gpd-upload-test-logger"));
        Function<RequestGPD, ResponseGPD> method = getMethod(ResultType.FAIL);
        PositionMessage positionMessage = new UpsertMessage(TestUtil.getCreateQueueMessage());
        OperationService operationService = new OperationService(context, method, positionMessage, new EStatusService());
        assertThrows(IllegalArgumentException.class, operationService::processBulkRequest);
    }

    private Function<RequestGPD, ResponseGPD> getMethod(ResultType resultType) {
        return switch (resultType) {
            case FAIL -> this::doMockedThingsFail;
            case SUCCESS -> this::doMockedThingsSuccess;
            case NOT_FOUND -> this::doMockedThingsNotFound;
        };
    }

    private ResponseGPD doMockedThingsSuccess(RequestGPD requestGPD) {
        return ResponseGPD.builder()
                       .detail("detail")
                       .retryStep(RetryStep.DONE)
                       .status(HttpStatus.OK.value())
                       .build();
    }

    private ResponseGPD doMockedThingsNotFound(RequestGPD requestGPD) {
        return ResponseGPD.builder()
                       .detail("not found item")
                       .retryStep(RetryStep.DONE)
                       .status(HttpStatus.NOT_FOUND.value())
                       .build();
    }

    private ResponseGPD doMockedThingsFail(RequestGPD requestGPD) {
        return ResponseGPD.builder()
                       .detail("failed request")
                       .retryStep(RetryStep.RETRY)
                       .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                       .build();
    }

    public static class EStatusService extends StatusService {
        @Override
        public void appendResponses(String invocationId, String fiscalCode, String key, Map<String, ResponseGPD> responses) throws AppException {
        }

        public void appendResponse(String invocationId, String fiscalCode, String key, List<String> iUPDs, ResponseGPD response) throws AppException {
        }
    }
}
