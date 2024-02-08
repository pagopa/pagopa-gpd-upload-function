package it.gov.pagopa.gpd.upload.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.gpd.upload.ServiceFunction;
import it.gov.pagopa.gpd.upload.client.GPDClient;
import it.gov.pagopa.gpd.upload.service.StatusService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static it.gov.pagopa.gpd.upload.functions.util.TestUtil.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceFunctionTest {

    @Spy
    ServiceFunction serviceFunction;

    @Mock
    GPDClient gpdClient;

    @Mock
    StatusService statusService;

    private final ExecutionContext context = Mockito.mock(ExecutionContext.class);

    @Test
    void runOk() throws Exception {
        // Prepare all mock response
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getInvocationId()).thenReturn("testInvocationId");
        // Set mock event
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.INDENT_OUTPUT);
        doReturn(statusService).when(serviceFunction).getStatusService(any());
        doReturn(gpdClient).when(serviceFunction).getGPDClient();
        doReturn(getMockResponseGPD()).when(gpdClient).createBulkDebtPositions(any(), any(), any(), any());
        doReturn(getMockResponseGPD()).when(gpdClient).createDebtPosition(any(), any(), any(), any());
        doNothing().when(statusService).appendResponses(any(), any(), any(), any());
        doReturn(getMockStatus()).when(statusService).updateStatusEndTime(any(), any(), any(), any());
        doReturn(getMockStatus()).when(statusService).getStatus(any(), any(), any());
        String event = objectMapper.writeValueAsString(getMockPaymentPositionsMessage());
        // Run function
        serviceFunction.run(event, context);
        //Assertion
        assertTrue(true);
    }
}
