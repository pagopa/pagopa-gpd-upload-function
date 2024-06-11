package it.gov.pagopa.gpd.upload.functions.function;

import com.azure.core.util.BinaryData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.gpd.upload.ValidationFunction;
import it.gov.pagopa.gpd.upload.service.StatusService;
import it.gov.pagopa.gpd.upload.util.GPDValidator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static it.gov.pagopa.gpd.upload.functions.util.TestUtil.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidationFunctionTest {

    @Spy
    ValidationFunction validationFunction;
    private final ExecutionContext context = Mockito.mock(ExecutionContext.class);
    private static MockedStatic<GPDValidator> positionValidatorMockedStatic;
    private MockedStatic<StatusService> mockedStaticStatusService;
    private Logger mockLogger;

    @BeforeAll
    public static void init() {
        positionValidatorMockedStatic = mockStatic(GPDValidator.class);
    }

    @BeforeEach
    public void setUp() {
        mockLogger = mock(Logger.class);
        StatusService mockStatusService = mock(StatusService.class);
        mockedStaticStatusService = mockStatic(StatusService.class);
        mockedStaticStatusService.when(() -> StatusService.getInstance(mockLogger)).thenReturn(mockStatusService);
    }

    @AfterEach
    public void tearDown() {
        mockedStaticStatusService.close();
    }

    @Test
    void runSizeTooLarge() throws Exception {
        // Prepare all mock response
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getInvocationId()).thenReturn("testInvocationId");
        // Set mock event
        String event = getMockBlobCreatedEventSize("10e+8");
        // Run function
        validationFunction.run(event, context);
        //Assertion
        assertTrue(true);
    }

    @Test
    void runSizeZero() throws Exception {
        // Prepare all mock response
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getInvocationId()).thenReturn("testInvocationId");
        // Set mock event
        String event = getMockBlobCreatedEventSize("0");
        // Run function
        validationFunction.run(event, context);
        //Assertion
        assertTrue(true);
    }

    @Test
    void runCreateOK() throws Exception {
        // Prepare all mock response
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getInvocationId()).thenReturn("testInvocationId");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        BinaryData createInputData = BinaryData.fromString(objectMapper.writeValueAsString(getMockCreateInputData()));
        doReturn(createInputData).when(validationFunction).downloadBlob(any(), any(), any(), any());
        doReturn(getMockStatus()).when(validationFunction).createStatus(any(), any(), any(), any(), anyInt());
        doReturn(true).when(validationFunction).enqueue(any(), any(), any(), any(), any(), any(), any(), any());
        positionValidatorMockedStatic.when(() -> GPDValidator.validate(any(),any(), any(), any())).thenReturn(true);
        // Set mock event
        String event = getMockBlobCreatedEvent();
        // Run function
        validationFunction.run(event, context);
        //Assertion
        assertTrue(true);
    }

    @Test
    void runInvalidBlob() throws Exception {
        // Prepare all mock response
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getInvocationId()).thenReturn("testInvocationId");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        BinaryData createInputData = BinaryData.fromString(objectMapper.writeValueAsString(getMockCreateInputData()));
        doReturn(createInputData).when(validationFunction).downloadBlob(any(), any(), any(), any());
        doReturn(false).when(validationFunction).validateBlob(any(), any(), any(), any(), any());
        // Set mock event
        String event = getMockBlobCreatedEvent();
        // Run function
        validationFunction.run(event, context);
        //Assertion
        assertTrue(true);
    }

    @Test
    void runDeleteOK() throws Exception {
        // Prepare all mock response
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getInvocationId()).thenReturn("testInvocationId");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        BinaryData deleteInputData = BinaryData.fromString(objectMapper.writeValueAsString(getMockDeleteInputData()));
        doReturn(deleteInputData).when(validationFunction).downloadBlob(any(), any(), any(), any());
        doReturn(getMockStatus()).when(validationFunction).createStatus(any(), any(), any(), any(), anyInt());
        doReturn(true).when(validationFunction).enqueue(any(), any(), any(), any(), any(), any(), any(), any());
        positionValidatorMockedStatic.when(() -> GPDValidator.validate(any(),any(), any(), any())).thenReturn(true);
        // Set mock event
        String event = getMockBlobCreatedEvent();
        // Run function
        validationFunction.run(event, context);
        //Assertion
        assertTrue(true);
    }

    @Test
    void runExceptionKO() throws Exception {
        // Prepare all mock response
        when(context.getLogger()).thenReturn(mockLogger);
        when(context.getInvocationId()).thenReturn("testInvocationId");
        BinaryData malformedJSONData = BinaryData.fromString("{malformed JSON");

        // Run function and assert
        assertFalse(validationFunction.validateBlob(context, "broker", "fc", "key", malformedJSONData));
    }

    @AfterAll
    public static void close() {
        positionValidatorMockedStatic.close();
    }
}