package it.gov.pagopa.gpd.upload.functions.function;

import com.azure.core.util.BinaryData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.gpd.upload.ValidationFunction;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.CRUDOperation;
import it.gov.pagopa.gpd.upload.model.enumeration.ServiceType;
import it.gov.pagopa.gpd.upload.service.QueueService;
import it.gov.pagopa.gpd.upload.service.StatusService;
import it.gov.pagopa.gpd.upload.util.GPDValidator;
import it.gov.pagopa.gpd.upload.util.IdempotencyUploadTracker;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Logger;

import static it.gov.pagopa.gpd.upload.functions.util.TestUtil.*;
import static it.gov.pagopa.gpd.upload.util.Constants.BLOB_KEY;
import static it.gov.pagopa.gpd.upload.util.Constants.SERVICE_TYPE_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidationFunctionTest {

    @Spy
    ValidationFunction validationFunction;
    private final ExecutionContext context = mock(ExecutionContext.class);
    private static MockedStatic<GPDValidator> positionValidatorMockedStatic;
    private MockedStatic<StatusService> mockedStaticStatusService;
    private MockedStatic<QueueService> mockedStaticQueueService;
    private Logger mockLogger;
    private static final String DEFAULT_LOCK_SUBJECT =
            "/containers/broker0001/blobs/ec0001/77777777777f3d1";

    @BeforeAll
    static void init() {
        positionValidatorMockedStatic = mockStatic(GPDValidator.class);
    }

    @BeforeEach
    void setUp() {
        IdempotencyUploadTracker.unlock(DEFAULT_LOCK_SUBJECT);

        mockLogger = mock(Logger.class);
        StatusService mockStatusService = mock(StatusService.class);
        mockedStaticStatusService = mockStatic(StatusService.class);
        mockedStaticStatusService.when(() -> StatusService.getInstance()).thenReturn(mockStatusService);
        QueueService mockQueueService = mock(QueueService.class);
        mockedStaticQueueService = mockStatic(QueueService.class);
        mockedStaticQueueService.when(() -> QueueService.getInstance()).thenReturn(mockQueueService);
    }

    @AfterEach
    void tearDown() {
        IdempotencyUploadTracker.unlock(DEFAULT_LOCK_SUBJECT);

        mockedStaticStatusService.close();
        mockedStaticQueueService.close();
    }

    @Test
    void runSizeTooLarge() throws AppException {
    	when(context.getLogger()).thenReturn(mockLogger);
        when(context.getInvocationId()).thenReturn("testInvocationId");

        String event = getMockBlobCreatedEventSize("10e+8");

        doReturn(true).when(validationFunction)
                .failUpload(eq(context), anyString(), anyString(), anyString(), contains("exceeds the maximum allowed threshold"));

        validationFunction.run(event, context);

        verify(validationFunction, times(1))
        .failUpload(
                eq(context),
                eq("broker0001"),
                eq("ec0001"),
                eq("77777777777f3d1"),
                contains("exceeds the maximum allowed threshold")
        );

        verify(validationFunction, never())
                .downloadBlob(any(), any(), any(), any());

        verify(validationFunction, never())
                .validateBlob(any(), any(), any(), any(), any(), any());
    }

    @Test
    void runSizeZero() throws AppException  {
    	when(context.getLogger()).thenReturn(mockLogger);
        when(context.getInvocationId()).thenReturn("testInvocationId");

        String event = getMockBlobCreatedEventSize("0");

        doReturn(true).when(validationFunction)
                .failUpload(eq(context), anyString(), anyString(), anyString(), contains("content length is zero"));

        validationFunction.run(event, context);

        verify(validationFunction, times(1))
        .failUpload(
                eq(context),
                eq("broker0001"),
                eq("ec0001"),
                eq("77777777777f3d1"),
                contains("content length is zero")
        );

        verify(validationFunction, never())
                .downloadBlob(any(), any(), any(), any());

        verify(validationFunction, never())
                .validateBlob(any(), any(), any(), any(), any(), any());
    }

    @Test
    void runCreateOK() throws Exception {
        // Prepare all mock response
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getInvocationId()).thenReturn("testInvocationId");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        Map<String, Object> response = Map.of(BLOB_KEY, BinaryData.fromString(objectMapper.writeValueAsString(getMockCreateInputData())), SERVICE_TYPE_KEY, ServiceType.GPD);
        lenient().doReturn(response).when(validationFunction).downloadBlob(any(), any(), any(), any());
        lenient().doReturn(getMockStatus()).when(validationFunction).createStatus(any(), any(), any(), any(), anyInt(), any(ServiceType.class));
        lenient().doReturn(true).when(validationFunction).enqueue(any(), any(), any(), any(), any(), any(), any(), any(), any());
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
        Map<String, Object> response = Map.of(BLOB_KEY, BinaryData.fromString(objectMapper.writeValueAsString(getMockCreateInputData())), SERVICE_TYPE_KEY, ServiceType.GPD);
        lenient().doReturn(response).when(validationFunction).downloadBlob(any(), any(), any(), any());
        lenient().doReturn(false).when(validationFunction).validateBlob(any(), any(), any(), any(), any(), any(ServiceType.class));
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
        Map<String, Object> response = Map.of(BLOB_KEY, BinaryData.fromString(objectMapper.writeValueAsString(getMockDeleteInputData())), SERVICE_TYPE_KEY, ServiceType.GPD);
        doReturn(response).when(validationFunction).downloadBlob(any(), any(), any(), any());
        doReturn(getMockStatus()).when(validationFunction).createStatus(any(), any(), any(), any(), anyInt(), any(ServiceType.class));
        doReturn(true).when(validationFunction).enqueue(any(), any(), any(), any(), any(), any(), any(), any(), any());
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
        assertFalse(validationFunction.validateBlob(context, "broker", "fc", "key", malformedJSONData, ServiceType.GPD));
    }

    @Test
    void runEnqueueCreateMessageTest() {
        // Prepare all mock response
        when(context.getLogger()).thenReturn(mockLogger);
        when(context.getInvocationId()).thenReturn("testInvocationId");

        // Run function method and assert
        Assertions.assertFalse(
                validationFunction.enqueue(context, new ObjectMapper(), CRUDOperation.CREATE, new ArrayList<>(), null, "key", "code", "broker-id", ServiceType.GPD)
        );
    }

    @Test
    void runEnqueueDeleteMessageTest() {
        // Prepare all mock response
        when(context.getLogger()).thenReturn(mockLogger);
        when(context.getInvocationId()).thenReturn("testInvocationId");

        // Run function method and assert
        Assertions.assertFalse(
                validationFunction.enqueue(context, new ObjectMapper(), CRUDOperation.DELETE, null, new ArrayList<>(), "key", "code", "broker-id", ServiceType.GPD)
        );
    }
    
    @Test
    void runSkipDuplicateEventSubject() {
        // Prepare all mock response
        when(context.getLogger()).thenReturn(mockLogger);
        when(context.getInvocationId()).thenReturn("testInvocationId");

        String broker = "demo";
        String fiscalCode = "demo";
        String filename = "demo.json";
        String eventSubject = String.format("/containers/%s/blobs/%s/input/%s", broker, fiscalCode, filename);

        String events = "[{" +
                "\"id\":\"1\"," +
                "\"eventType\":\"Microsoft.Storage.BlobCreated\"," +
                "\"subject\":\"" + eventSubject + "\"," +
                "\"data\":{\"contentLength\":1024}," +
                "\"eventTime\":\"2023-01-01T00:00:00Z\"," +
                "\"dataVersion\":\"1.0\"" +
                "}]";
       
        String lockSubject = String.format("/containers/%s/blobs/%s/%s", broker, fiscalCode, "demo");
        assertTrue(IdempotencyUploadTracker.tryLock(lockSubject));

        // Run
        validationFunction.run(events, context);

        verify(validationFunction, never()).downloadBlob(any(), any(), any(), any());

        // Cleanup
        IdempotencyUploadTracker.unlock(lockSubject);
    }

    @AfterAll
    static void close() {
        positionValidatorMockedStatic.close();
    }
}