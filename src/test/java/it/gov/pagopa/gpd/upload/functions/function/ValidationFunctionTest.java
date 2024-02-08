package it.gov.pagopa.gpd.upload.functions.function;

import com.azure.core.util.BinaryData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.gpd.upload.ValidationFunction;
import it.gov.pagopa.gpd.upload.util.PaymentPositionValidator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static it.gov.pagopa.gpd.upload.functions.utils.TestUtil.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidationFunctionTest {

    @Spy
    ValidationFunction validationFunction;

    private final ExecutionContext context = Mockito.mock(ExecutionContext.class);

    private static MockedStatic<PaymentPositionValidator> positionValidatorMockedStatic;

    @BeforeAll
    public static void init() {
        positionValidatorMockedStatic = mockStatic(PaymentPositionValidator.class);
    }

    @Test
    void runOK() throws Exception {
        // Prepare all mock response
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getInvocationId()).thenReturn("testInvocationId");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        BinaryData debtPositionData = BinaryData.fromString(objectMapper.writeValueAsString(getMockDebtPositions()));
        doReturn(debtPositionData).when(validationFunction).downloadBlob(any(), any(), any(), any());
        doReturn(getMockStatus()).when(validationFunction).createStatus(any(), any(), any(), any(), anyInt());
        doReturn(true).when(validationFunction).enqueue(any(), any(), any(), any(), any());
        positionValidatorMockedStatic.when(() -> PaymentPositionValidator.validate(any(),any(), any(), any(), any())).thenReturn(true);
        // Set mock event
        String event = getMockBlobCreatedEvent();
        // Run function
        validationFunction.run(event, context);
        //Assertion
        assertTrue(true);
    }

    @AfterAll
    public static void close() {
        positionValidatorMockedStatic.close();
    }
}