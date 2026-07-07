package it.gov.pagopa.gpd.upload.util;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.entity.ResponseEntry;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPosition;
import it.gov.pagopa.gpd.upload.service.StatusService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GPDValidator {
	
	private GPDValidator() {
		super();
	}

	private static final Logger log = LoggerFactory.getLogger(GPDValidator.class);
	private static final String LOG_PREFIX = "[id={}][upload={}][GPDValidator]";

    public static boolean validate(ExecutionContext ctx, List<PaymentPosition> paymentPositions, String fiscalCode, String uploadKey) {
        ValidatorFactory factory = jakarta.validation.Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<PaymentPosition>> violations;
        List<ResponseEntry> entries = new ArrayList<>();

        Iterator<PaymentPosition> iterator = paymentPositions.iterator();
        while (iterator.hasNext()) {
            PaymentPosition paymentPosition = iterator.next();
            violations =  validator.validate(paymentPosition);

            if (!violations.isEmpty()) {
            	entries.add(createResponseEntry(ctx.getInvocationId(), uploadKey, paymentPosition, violations));
                iterator.remove();
            }
        }

        return updateStatus(ctx, fiscalCode, uploadKey, entries);
    }

    private static ResponseEntry createResponseEntry(
            String invocationId,
            String uploadKey,
            PaymentPosition paymentPosition,
            Set<ConstraintViolation<PaymentPosition>> violations
    ) {
        ConstraintViolation<PaymentPosition> violation = violations.stream().findFirst().orElse(null);
        String details = (violation != null ? violation.getMessage() : "");

        ResponseEntry responseEntry = ResponseEntry.builder()
                                              .statusCode(HttpStatus.BAD_REQUEST.value())
                                              .statusMessage(details)
                                              .requestIDs(List.of(paymentPosition.getIupd()))
                                              .build();

        for (ConstraintViolation<PaymentPosition> violationEntry : violations) {
            log.info(LOG_PREFIX + " Payment position {} is not valid, violation: {}",
                    invocationId,
                    uploadKey,
                    paymentPosition.getIupd(),
                    violationEntry.getMessage());
        }

        return responseEntry;
    }

    private static boolean updateStatus(ExecutionContext ctx, String orgFiscalCode, String key, List<ResponseEntry> entries) {
        try {
            StatusService.getInstance().updateStatus(ctx.getInvocationId(), orgFiscalCode, key, entries);
        } catch (AppException e) {
        	log.error(LOG_PREFIX + " Error while updating status with validation errors",
        	        ctx.getInvocationId(), key, e);
        	return false;
        }
        return true;
    }
}
