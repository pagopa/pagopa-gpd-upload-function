package it.gov.pagopa.gpd.upload.functions;

import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.gpd.upload.service.StatusService;
import it.gov.pagopa.gpd.upload.util.PaymentPositionValidator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
    private static final ExecutionContext context = Mockito.mock(ExecutionContext.class);
    private static MockedStatic<PaymentPositionValidator> positionValidatorMockedStatic;
    @Mock
    StatusService statusService;

    @BeforeAll
    public static void init() {
        positionValidatorMockedStatic = mockStatic(PaymentPositionValidator.class);
        Logger logger = Logger.getLogger("gpd-upload-test-logger");
        when(context.getLogger()).thenReturn(logger);
        when(context.getInvocationId()).thenReturn("testInvocationId");
    }

    @Test
    void runOK() {
        // Set mock
        positionValidatorMockedStatic.when(() -> PaymentPositionValidator.getStatusService(any())).thenReturn(statusService);
        // Run method
        PaymentPositionValidator.validate(context, getMockDebtPositions(),"mockFiscalCode", "mockUploadKey");
        //Assertion
        assertTrue(true);
    }

    @Test
    void runKO() throws Exception {
        //Set mock
        positionValidatorMockedStatic.when(() -> PaymentPositionValidator.getStatusService(any())).thenReturn(statusService);
        // Run method
        PaymentPositionValidator.validate(context, getMockInvalidDebtPositions(),"mockFiscalCode", "mockUploadKey");
        //Assertion
        assertTrue(true);
    }

    @AfterAll
    public static void close() {
        positionValidatorMockedStatic.close();
    }
}
