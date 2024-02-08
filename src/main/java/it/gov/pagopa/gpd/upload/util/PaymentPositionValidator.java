package it.gov.pagopa.gpd.upload.util;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.entity.ResponseEntry;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositions;
import it.gov.pagopa.gpd.upload.service.StatusService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PaymentPositionValidator {

    public static boolean validate(ExecutionContext ctx, PaymentPositions paymentPositions, String fiscalCode, String uploadKey) {
        ValidatorFactory factory = jakarta.validation.Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<PaymentPosition>> violations;
        List<ResponseEntry> entries = new ArrayList<>();

        Iterator<PaymentPosition> iterator = paymentPositions.getPaymentPositions().iterator();
        while (iterator.hasNext()) {
            PaymentPosition paymentPosition = iterator.next();
            violations =  validator.validate(paymentPosition);

            if (!violations.isEmpty()) {
                entries.add(createResponseEntry(ctx.getLogger(), paymentPosition, violations));
                iterator.remove();
            }
        }

        try {
            getStatusService(ctx.getLogger()).updateStatus(ctx.getInvocationId(), fiscalCode, uploadKey, entries);
        } catch (AppException e) {
            ctx.getLogger().log(Level.SEVERE, () -> String.format("[id=%s][ValidationFunction] No match found in the input string.", ctx.getInvocationId()));
            return false;
        }
        return true;
    }

    private static ResponseEntry createResponseEntry(Logger logger, PaymentPosition paymentPosition, Set<ConstraintViolation<PaymentPosition>> violations) {
        ConstraintViolation<PaymentPosition> violation = violations.stream().findFirst().orElse(null);
        String details = (violation != null ? violation.getMessage() : "");

        ResponseEntry responseEntry = ResponseEntry.builder()
                                              .statusCode(HttpStatus.BAD_REQUEST.value())
                                              .statusMessage(details)
                                              .requestIDs(List.of(paymentPosition.getIupd()))
                                              .build();

        for(ConstraintViolation<PaymentPosition> v : violations) {
            logger.log(Level.INFO, "Payment position " + paymentPosition.getIupd() + " is not valid, violation: " + v.getMessage());
        }

        return responseEntry;
    }

    public static StatusService getStatusService(Logger logger) {
        return StatusService.getInstance(logger);
    }
}
