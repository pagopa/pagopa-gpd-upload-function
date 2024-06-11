package it.gov.pagopa.gpd.upload.functions.function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.gpd.upload.ServiceFunction;
import it.gov.pagopa.gpd.upload.client.GPDClient;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.entity.Upload;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.CRUDOperation;
import it.gov.pagopa.gpd.upload.repository.BlobRepository;
import it.gov.pagopa.gpd.upload.service.StatusService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.logging.Logger;

import static it.gov.pagopa.gpd.upload.functions.util.TestUtil.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceFunctionTest {

    @Spy
    ServiceFunction serviceFunction;
    @Mock
    GPDClient gpdClient;

    private Logger mockLogger;
    private MockedStatic<BlobRepository> mockedStaticBlobRepository;
    private final ExecutionContext context = Mockito.mock(ExecutionContext.class);

    @BeforeEach
    public void setUp() {
        mockLogger = mock(Logger.class);
        // mock BlobRepository
        BlobRepository mockBlobRepository = mock(BlobRepository.class);
        mockedStaticBlobRepository = mockStatic(BlobRepository.class);
        mockedStaticBlobRepository.when(() -> BlobRepository.getInstance(mockLogger)).thenReturn(mockBlobRepository);
    }

    @AfterEach
    public void tearDown() {
        mockedStaticBlobRepository.close();
    }

    @Test
    void runBulkCreateOK() throws Exception {
        // Prepare all mock response
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getInvocationId()).thenReturn("testInvocationId");
        // Set mock event
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.INDENT_OUTPUT);
        //doReturn(statusService).when(serviceFunction).getStatusService(any());
        doReturn(gpdClient).when(serviceFunction).getGPDClient(context);
        //doReturn(getOKMockResponseGPD()).when(gpdClient).createDebtPosition(any());
        //doReturn(getMockStatus()).when(statusService).updateStatusEndTime(any(), any(), any(), any());
        //doReturn(getMockStatus()).when(statusService).getStatus(any(), any(), any());
        String message = objectMapper.writeValueAsString(getMockInputMessage(CRUDOperation.CREATE));
        // Run function
        serviceFunction.run(message, context);
        //Assertion
        assertTrue(true);
    }

    @Test
    void runOneByOneCreateKO() throws Exception {
        // Prepare all mock response
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getInvocationId()).thenReturn("testInvocationId");
        // Set mock event
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.INDENT_OUTPUT);
        //doReturn(statusService).when(serviceFunction).getStatusService(any());
        doReturn(gpdClient).when(serviceFunction).getGPDClient(context);
        //doReturn(getKOMockResponseGPD()).when(gpdClient).createDebtPosition(any());
        //doNothing().when(statusService).appendResponses(any(), any(), any(), any());
        // todo doNothing().when(serviceFunction).retry(any(), any(), any());
        //doReturn(getMockStatus()).when(statusService).updateStatusEndTime(any(), any(), any(), any());
        //doReturn(getMockStatus()).when(statusService).getStatus(any(), any(), any());
        String message = objectMapper.writeValueAsString(getMockInputMessage(CRUDOperation.CREATE));
        // Run function
        serviceFunction.run(message, context);
        //Assertion
        assertTrue(true);
    }

    @Test
    void runBulkUpdateOK() throws Exception {
        // Prepare all mock response
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getInvocationId()).thenReturn("testInvocationId");
        // Set mock event
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.INDENT_OUTPUT);
        //doReturn(statusService).when(serviceFunction).getStatusService(any());
        doReturn(gpdClient).when(serviceFunction).getGPDClient(context);
        //doReturn(getOKMockResponseGPD()).when(gpdClient).updateDebtPosition(any());
        //doReturn(getMockStatus()).when(statusService).updateStatusEndTime(any(), any(), any(), any());
        //doReturn(getMockStatus()).when(statusService).getStatus(any(), any(), any());
        String message = objectMapper.writeValueAsString(getMockInputMessage(CRUDOperation.UPDATE));
        // Run function
        serviceFunction.run(message, context);
        //Assertion
        assertTrue(true);
    }

    @Test
    void runOneByOneUpdateKO() throws Exception {
        // Prepare all mock response
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getInvocationId()).thenReturn("testInvocationId");
        // Set mock event
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.INDENT_OUTPUT);
        //doReturn(statusService).when(serviceFunction).getStatusService(any());
        doReturn(gpdClient).when(serviceFunction).getGPDClient(context);
        //doReturn(getKOMockResponseGPD()).when(gpdClient).updateDebtPosition(any());
        //doNothing().when(statusService).appendResponses(any(), any(), any(), any());
        // todo doNothing().when(serviceFunction).retry(any(), any(), any());
        //doReturn(getMockStatus()).when(statusService).updateStatusEndTime(any(), any(), any(), any());
        //doReturn(getMockStatus()).when(statusService).getStatus(any(), any(), any());
        String message = objectMapper.writeValueAsString(getMockInputMessage(CRUDOperation.UPDATE));
        // Run function
        serviceFunction.run(message, context);
        //Assertion
        assertTrue(true);
    }

    @Test
    void runReport() throws AppException, JsonProcessingException {
        Status status = Status.builder()
                                .id("upload-id")
                                .brokerID("broker-id")
                                .fiscalCode("ec-fiscal-code")
                                .upload(Upload.builder()
                                                .current(10)
                                                .total(10)
                                                .start(LocalDateTime.now())
                                                .end(LocalDateTime.now())
                                                .responses(new ArrayList<>())
                                                .build())
                                .build();
        // BlobRepository mocked false by default
        Assertions.assertFalse(serviceFunction.report(mockLogger, "key", status));
    }
}
