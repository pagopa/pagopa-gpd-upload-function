package it.gov.pagopa.gpd.upload.functions.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.entity.DebtPositionMessage;
import it.gov.pagopa.gpd.upload.entity.UpsertMessage;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.functions.util.TestUtil;
import it.gov.pagopa.gpd.upload.model.RequestGPD;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.model.RetryStep;
import it.gov.pagopa.gpd.upload.service.CRUDService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.function.Function;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class CRUDServiceTest {

    public enum ResultType {SUCCESS, FAIL, NOT_FOUND}

    private final ExecutionContext context = Mockito.mock(ExecutionContext.class);

    @Test
    void test1() throws JsonProcessingException {
        when(context.getLogger()).thenReturn(Logger.getLogger("gpd-upload-test-logger"));
        Function<RequestGPD, ResponseGPD> method = getMethod(ResultType.SUCCESS);
        DebtPositionMessage debtPositionMessage = new UpsertMessage(TestUtil.getCreateQueueMessage());
        CRUDService operationService = new CRUDService(context, method, debtPositionMessage);
        operationService.processRequestInBulk();
    }

    @Test
    void test2() throws AppException, JsonProcessingException {
        when(context.getLogger()).thenReturn(Logger.getLogger("gpd-upload-test-logger"));
        Function<RequestGPD, ResponseGPD> method = getMethod(ResultType.NOT_FOUND);
        DebtPositionMessage debtPositionMessage = new UpsertMessage(TestUtil.getCreateQueueMessage());
        CRUDService operationService = new CRUDService(context, method, debtPositionMessage);
        operationService.processRequestInBulk();
    }

    @Test
    void test3() {
        when(context.getLogger()).thenReturn(Logger.getLogger("gpd-upload-test-logger"));
        Function<RequestGPD, ResponseGPD> method = getMethod(ResultType.FAIL);
        DebtPositionMessage debtPositionMessage = new UpsertMessage(TestUtil.getCreateQueueMessage());
        CRUDService operationService = new CRUDService(context, method, debtPositionMessage);
        // the test is performed without instantiation of a real storage and valid CONNECTION_STRING is not present at JUnit test time
        assertThrows(IllegalArgumentException.class, operationService::processRequestInBulk);
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
}
