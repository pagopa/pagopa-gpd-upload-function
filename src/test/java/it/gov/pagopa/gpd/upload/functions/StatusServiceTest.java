package it.gov.pagopa.gpd.upload.functions;

import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.repository.StatusRepository;
import it.gov.pagopa.gpd.upload.service.StatusService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import static it.gov.pagopa.gpd.upload.functions.util.TestUtil.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StatusServiceTest {
    private static final ExecutionContext ctx = Mockito.mock(ExecutionContext.class);

    @Spy
    static StatusService statusService;

    @Mock
    StatusRepository statusRepository;


    @BeforeAll
    public static void init() {
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(ctx.getLogger()).thenReturn(logger);
        when(ctx.getInvocationId()).thenReturn("testInvocationId");
    }

    @Test
    void createStatusOK() throws AppException {
        doReturn(statusRepository).when(statusService).getStatusRepository();
        doReturn(getMockStatus()).when(statusRepository).createIfNotExist(any(), any(), any(), any());
        //Assertion
        assertNotNull(statusService.createStatus(ctx.getInvocationId(), "broker", "fiscalCode", "key", 10));
    }

    @Test
    void getStatusOK() throws AppException {
        doReturn(statusRepository).when(statusService).getStatusRepository();
        doReturn(getMockStatus()).when(statusRepository).getStatus(any(), any(), any());
        //Assertion
        assertNotNull(statusService.getStatus(ctx.getInvocationId(), "fiscalCode", "key"));
    }

    @Test
    void updateStatusEndTimeOK() throws AppException {
        doReturn(statusRepository).when(statusService).getStatusRepository();
        doReturn(getMockStatus()).when(statusRepository).getStatus(any(), any(), any());
        doNothing().when(statusRepository).upsertStatus(any(), any(), any());
        //Assertion
        assertNotNull(statusService.updateStatusEndTime(ctx.getInvocationId(), "fiscalCode", "key", LocalDateTime.now()));
    }

    @Test
    void appendResponseOK() throws AppException {
        doReturn(statusRepository).when(statusService).getStatusRepository();
        doReturn(getMockStatus()).when(statusRepository).getStatus(any(), any(), any());
        doNothing().when(statusRepository).upsertStatus(any(), any(), any());
        statusService.appendResponse(ctx.getInvocationId(), "fiscalCode", "key", List.of("IUPD1"), getMockResponseGPD());
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
