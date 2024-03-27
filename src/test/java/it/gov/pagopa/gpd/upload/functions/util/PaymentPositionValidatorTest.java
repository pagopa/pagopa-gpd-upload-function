package it.gov.pagopa.gpd.upload.functions.util;

import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.service.StatusService;
import it.gov.pagopa.gpd.upload.util.PaymentPositionValidator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static it.gov.pagopa.gpd.upload.functions.utils.TestUtil.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentPositionValidatorTest {
    private static final ExecutionContext context = Mockito.mock(ExecutionContext.class);

    @Spy
    static StatusService statusService;

    @BeforeAll
    public static void init() {
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getInvocationId()).thenReturn("testInvocationId");
    }

    @Test
    void runOK() throws AppException {
        doNothing().when(statusService).updateStatus(any(), any(), any(), any());
        // Run method
        PaymentPositionValidator.validate(context, getMockDebtPositions().getPaymentPositions(),"mockFiscalCode", "mockUploadKey");
        //Assertion
        assertTrue(true);
    }

    @Test
    void runKO() throws AppException {
        doNothing().when(statusService).updateStatus(any(), any(), any(), any());
        // Run method
        PaymentPositionValidator.validate(context, getMockInvalidDebtPositions().getPaymentPositions(),"mockFiscalCode", "mockUploadKey");
        //Assertion
        assertTrue(true);
    }

    @AfterAll
    public static void close() { }
}
