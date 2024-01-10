package it.gov.pagopa.gpd.upload.functions;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;

import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositionsModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import static it.gov.pagopa.gpd.upload.functions.util.TestUtil.getMockDebtPositions;
import static it.gov.pagopa.gpd.upload.functions.util.TestUtil.getMockOutputBinding;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadFunctionTest {

    @Spy
    UploadFunction uploadFunction;

    private final ExecutionContext context = Mockito.mock(ExecutionContext.class);

    @Test
    void runOk() throws Exception {
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getInvocationId()).thenReturn("testInvocationId");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        String mockDebtPositions = objectMapper.writeValueAsString(getMockDebtPositions());
        doNothing().when(uploadFunction).createPaymentPositionBlocks(any(Logger.class), anyString(), anyString(), anyString(), any(PaymentPositionsModel.class), any(Status.class));

        OutputBinding<String> mockOutputBinding = getMockOutputBinding();
        //Function execution
        uploadFunction.run(mockDebtPositions.getBytes(StandardCharsets.UTF_8), "testFiscalCode", "testFilename.json", mockOutputBinding, context);

        //Assertion
        assertTrue(true);
    }

    @Test
    void runKo() throws Exception {
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getInvocationId()).thenReturn("testInvocationId");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        String mockDebtPositions = objectMapper.writeValueAsString(getMockDebtPositions());
        doThrow(new Exception()).when(uploadFunction).createPaymentPositionBlocks(any(Logger.class), anyString(), anyString(), anyString(), any(PaymentPositionsModel.class), any(Status.class));

        OutputBinding<String> mockOutputBinding = getMockOutputBinding();
        //Function execution
        uploadFunction.run(mockDebtPositions.getBytes(StandardCharsets.UTF_8), "testFiscalCode", "testFilename.json", mockOutputBinding, context);

        //Assertion
        assertTrue(true);
    }
}
