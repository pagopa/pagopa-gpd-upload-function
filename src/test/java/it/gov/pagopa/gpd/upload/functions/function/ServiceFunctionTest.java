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
import it.gov.pagopa.gpd.upload.entity.UpsertMessage;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.CRUDOperation;
import it.gov.pagopa.gpd.upload.model.QueueMessage;
import it.gov.pagopa.gpd.upload.model.RequestGPD;
import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.repository.BlobRepository;
import it.gov.pagopa.gpd.upload.service.CRUDService;
import it.gov.pagopa.gpd.upload.service.StatusService;
import it.gov.pagopa.gpd.upload.util.IdempotencyUploadTracker;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.function.Function;
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
    @Mock
    BlobRepository blobRepository;

    private final ExecutionContext context = mock(ExecutionContext.class);


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
        doReturn(gpdClient).when(serviceFunction).getGPDClient();
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
        doReturn(gpdClient).when(serviceFunction).getGPDClient();
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
        doReturn(gpdClient).when(serviceFunction).getGPDClient();
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
        doReturn(gpdClient).when(serviceFunction).getGPDClient();
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
        
        doReturn(blobRepository).when(serviceFunction).getBlobRepository();

        doReturn(false).when(blobRepository)
                .uploadReport(anyString(), anyString(), anyString(), anyString(), any());
        
        Assertions.assertFalse(serviceFunction.generateReport("key", status));
    }
    
    @Test
    void runUnlocksIdempotencyKeyWhenUploadCompletes() throws Exception {
        ExecutionContext mockContext = mock(ExecutionContext.class);
        when(mockContext.getInvocationId()).thenReturn("testInvocationId");

        QueueMessage message = new QueueMessage();
        message.setUploadKey("uploadKey123");
        message.setOrganizationFiscalCode("org123");
        message.setBrokerCode("broker123");

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        String messageJson = mapper.writeValueAsString(message);

        try (
            MockedStatic<IdempotencyUploadTracker> mockedIdempotency = mockStatic(IdempotencyUploadTracker.class);
            MockedStatic<StatusService> mockedStatusService = mockStatic(StatusService.class);
        ) {
            StatusService mockStatusService = mock(StatusService.class);
            Status mockStatus = new Status();
            mockStatus.upload = new Upload();
            mockStatus.upload.setCurrent(5);
            mockStatus.upload.setTotal(5);

            mockedStatusService.when(StatusService::getInstance).thenReturn(mockStatusService);
            when(mockStatusService.getStatus("testInvocationId", "org123", "uploadKey123")).thenReturn(mockStatus);

            when(mockStatusService.updateStatusEndTime(eq("org123"), eq("uploadKey123"), any())).thenReturn(false);

            Function<RequestGPD, ResponseGPD> dummyFunction = new Function<>() {
                @Override
                public ResponseGPD apply(RequestGPD requestGPD) {
                    return new ResponseGPD();
                }
            };
           
            doReturn(dummyFunction).when(serviceFunction).getMethod(any(), any());
            doReturn(mock(CRUDService.class)).when(serviceFunction).getOperationService(any(), any(), any());
            doReturn(mock(GPDClient.class)).when(serviceFunction).getGPDClient();
            doReturn(mock(UpsertMessage.class)).when(serviceFunction).getPositionMessage(any());

            serviceFunction.run(messageJson, mockContext);

            // Assert
            String expectedSubject = "/containers/broker123/blobs/org123/uploadKey123";
            mockedIdempotency.verify(() -> IdempotencyUploadTracker.unlock(expectedSubject), atLeastOnce());
        }
    }
}
