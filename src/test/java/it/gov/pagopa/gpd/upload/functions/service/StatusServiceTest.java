package it.gov.pagopa.gpd.upload.functions.service;

import com.microsoft.azure.functions.ExecutionContext;

import it.gov.pagopa.gpd.upload.entity.ResponseEntry;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.enumeration.ServiceType;
import it.gov.pagopa.gpd.upload.repository.StatusRepository;
import it.gov.pagopa.gpd.upload.service.StatusService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import static it.gov.pagopa.gpd.upload.functions.util.TestUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusServiceTest {
    private static final ExecutionContext ctx = mock(ExecutionContext.class);

    @Spy
    static StatusService statusService;

    @Mock
    StatusRepository statusRepository;


    @BeforeAll
    static void init() {
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(ctx.getLogger()).thenReturn(logger);
        when(ctx.getInvocationId()).thenReturn("testInvocationId");
    }

    @Test
    void createStatusOK() throws AppException {
        doReturn(statusRepository).when(statusService).getStatusRepository();
        doReturn(getMockStatus()).when(statusRepository).createIfNotExist(any(), any(), any(), any());
        //Assertion
        assertNotNull(statusService.createStatus(ctx.getInvocationId(), "broker", "fiscalCode", "key", 10, ServiceType.GPD));
    }

    @Test
    void getStatusOK() throws AppException {
        doReturn(statusRepository).when(statusService).getStatusRepository();
        doReturn(getMockStatus()).when(statusRepository).getStatus(any(), any(), any());
        //Assertion
        assertNotNull(statusService.getStatus(ctx.getInvocationId(), "fiscalCode", "key"));
    }

    @Test
    void updateStatusEndTimeOK() {
        doReturn(statusRepository).when(statusService).getStatusRepository();
        //Assertion
        assertNotNull(statusService.updateStatusEndTime("fiscalCode", "key", LocalDateTime.now()));
    }
    
    @Test
    void failStatusOK() throws AppException {
        doReturn(statusRepository).when(statusService).getStatusRepository();

        Status status = getMockStatus();
        status.getUpload().setCurrent(0);
        status.getUpload().setTotal(59636);
        status.getUpload().setResponses(null);

        doReturn(status).when(statusRepository).getStatus(any(), any(), any());
        doNothing().when(statusRepository).upsertStatus(any(), any(), any());

        boolean result = statusService.failStatus(
                ctx.getInvocationId(),
                "broker",
                "fiscalCode",
                "key",
                "Input blob size 106037515 bytes exceeds the maximum allowed threshold of 100000000 bytes"
        );

        assertTrue(result);

        verify(statusRepository, times(1))
                .getStatus(ctx.getInvocationId(), "key", "fiscalCode");

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        verify(statusRepository, times(1))
                .upsertStatus(any(), any(), statusCaptor.capture());

        Status updatedStatus = statusCaptor.getValue();

        assertEquals(0, updatedStatus.getUpload().getCurrent());
        assertEquals(59636, updatedStatus.getUpload().getTotal());
        assertNotNull(updatedStatus.getUpload().getEnd());
        assertNotNull(updatedStatus.getUpload().getResponses());
        assertEquals(1, updatedStatus.getUpload().getResponses().size());
        assertEquals(413, updatedStatus.getUpload().getResponses().get(0).getStatusCode());
        assertEquals(
        		updatedStatus.getUpload().getResponses().get(0).getStatusCode() 
        		+ "-" 
        		+ "Input blob size 106037515 bytes exceeds the maximum allowed threshold of 100000000 bytes",
                updatedStatus.getUpload().getResponses().get(0).getStatusMessage()
        );
        assertTrue(updatedStatus.getUpload().getResponses().get(0).getRequestIDs().isEmpty());
    }
    
    @Test
    void failStatusStatusNotFoundCreatesFallbackStatusOK() throws AppException {
        doReturn(statusRepository).when(statusService).getStatusRepository();
        doReturn(null).when(statusRepository).getStatus(any(), any(), any());
        doNothing().when(statusRepository).upsertStatus(any(), any(), any());

        boolean result = statusService.failStatus(
                ctx.getInvocationId(),
                "broker",
                "fiscalCode",
                "key",
                "Input blob size exceeds the maximum allowed threshold"
        );

        assertTrue(result);

        verify(statusRepository, times(1))
                .getStatus(ctx.getInvocationId(), "key", "fiscalCode");

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        verify(statusRepository, times(1))
                .upsertStatus(eq(ctx.getInvocationId()), eq("key"), statusCaptor.capture());

        Status fallbackStatus = statusCaptor.getValue();

        assertEquals("key", fallbackStatus.getId());
        assertEquals("fiscalCode", fallbackStatus.getFiscalCode());
        assertEquals("broker", fallbackStatus.getBrokerID());
        assertEquals(ServiceType.GPD, fallbackStatus.getServiceType());

        assertNotNull(fallbackStatus.getUpload());
        assertEquals(0, fallbackStatus.getUpload().getCurrent());
        assertEquals(0, fallbackStatus.getUpload().getTotal());
        assertNotNull(fallbackStatus.getUpload().getStart());
        assertNotNull(fallbackStatus.getUpload().getEnd());

        assertNotNull(fallbackStatus.getUpload().getResponses());
        assertEquals(1, fallbackStatus.getUpload().getResponses().size());

        ResponseEntry responseEntry = fallbackStatus.getUpload().getResponses().get(0);

        assertEquals(413, responseEntry.getStatusCode());
        assertEquals(
        		responseEntry.getStatusCode() + "-" +
                "Input blob size exceeds the maximum allowed threshold",
                responseEntry.getStatusMessage()
        );
        assertTrue(responseEntry.getRequestIDs().isEmpty());
    }
    
    @Test
    void failStatusRepositoryExceptionKO() throws AppException {
        doReturn(statusRepository).when(statusService).getStatusRepository();
        doThrow(new AppException("Repository error"))
                .when(statusRepository).getStatus(any(), any(), any());

        boolean result = statusService.failStatus(
                ctx.getInvocationId(),
                "broker",
                "fiscalCode",
                "key",
                "Input blob size exceeds the maximum allowed threshold"
        );

        assertFalse(result);
        verify(statusRepository, never()).upsertStatus(any(), any(), any());
    }

    @Test
    void appendResponseOK() throws AppException {
        doReturn(statusRepository).when(statusService).getStatusRepository();
        doReturn(getMockStatus()).when(statusRepository).getStatus(any(), any(), any());
        doNothing().when(statusRepository).upsertStatus(any(), any(), any());
        statusService.appendResponse(ctx.getInvocationId(), "fiscalCode", "key", List.of("IUPD1"), getOKMockResponseGPD());
        //Assertion
        assertTrue(true);
    }

    @Test
    void appendResponsesOK() throws AppException {
        doReturn(statusRepository).when(statusService).getStatusRepository();
        doReturn(getMockStatus()).when(statusRepository).getStatus(any(), any(), any());
        doNothing().when(statusRepository).upsertStatus(any(), any(), any());
        statusService.appendResponses(ctx.getInvocationId(), "fiscalCode", "key", new HashMap<>());
        //Assertion
        assertTrue(true);
    }

    @Test
    void updateStatusOK() throws AppException {
        doReturn(statusRepository).when(statusService).getStatusRepository();
        doReturn(getMockStatus()).when(statusRepository).getStatus(any(), any(), any());
        doNothing().when(statusRepository).upsertStatus(any(), any(), any());
        statusService.updateStatus(ctx.getInvocationId(), "fiscalCode", "key", getMockResponseEntries());
        //Assertion
        assertTrue(true);
    }
}
