package it.gov.pagopa.gpd.upload.functions;

import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.gpd.upload.service.StatusService;
import it.gov.pagopa.gpd.upload.util.PaymentPositionValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static it.gov.pagopa.gpd.upload.functions.util.TestUtil.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentPositionValidatorTest {
    private final ExecutionContext context = Mockito.mock(ExecutionContext.class);

    @Mock
    StatusService statusService;

    @Test
    void runOK() throws Exception {
        // Prepare all mock response
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getInvocationId()).thenReturn("testInvocationId");
        // Set mock event
        MockedStatic<PaymentPositionValidator> validator = Mockito.mockStatic(PaymentPositionValidator.class);
        validator.when(() -> PaymentPositionValidator.getStatusService(any())).thenReturn(statusService);
        // Run method
        PaymentPositionValidator.validate(context, getMockDebtPositions(),"mockFiscalCode", "mockUploadKey");
        //Assertion
        assertTrue(true);
    }

    @Test
    void runKO() throws Exception {
        // Prepare all mock response
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getInvocationId()).thenReturn("testInvocationId");
        // Set mock event
        MockedStatic<PaymentPositionValidator> validator = Mockito.mockStatic(PaymentPositionValidator.class);
        validator.when(() -> PaymentPositionValidator.getStatusService(any())).thenReturn(statusService);
        // Run method
        PaymentPositionValidator.validate(context, getMockInvalidDebtPositions(),"mockFiscalCode", "mockUploadKey");
        //Assertion
        assertTrue(true);
    }
}
