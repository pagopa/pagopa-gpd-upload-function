package it.gov.pagopa.gpd.upload.util;

import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.entity.ResponseEntry;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.exception.AppException;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositionModel;
import it.gov.pagopa.gpd.upload.model.pd.PaymentPositionsModel;
import it.gov.pagopa.gpd.upload.repository.StatusRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PaymentPositionValidator {

    public static void validate(Logger logger, PaymentPositionsModel paymentPositionsModel, Status status) throws AppException {
        ValidatorFactory factory = jakarta.validation.Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<PaymentPositionModel>> violations;
        int invalidPosition = 0;

        Iterator<PaymentPositionModel> iterator = paymentPositionsModel.getPaymentPositions().iterator();
        while (iterator.hasNext()) {
            PaymentPositionModel pp = iterator.next();
            violations =  validator.validate(pp);

            if (!violations.isEmpty()) {
                ConstraintViolation<PaymentPositionModel> violation = violations.stream().findFirst().orElse(null);
                String details = (violation != null ? violation.getMessage() : "");

                ResponseEntry responseEntry = ResponseEntry.builder()
                                                      .statusCode(HttpStatus.BAD_REQUEST.value())
                                                      .statusMessage(details)
                                                      .requestIDs(List.of(pp.getIupd()))
                                                      .build();
                status.upload.addResponse(responseEntry);
                invalidPosition++;
                iterator.remove();

                for(ConstraintViolation<PaymentPositionModel> v : violations) {
                    logger.log(Level.INFO, "Payment position " + pp.getIupd() + " is not valid, violation: " + v.getMessage());
                }
            }
        }

        status.upload.setCurrent(status.upload.getCurrent() + invalidPosition);
        logger.log(Level.INFO, status.toString());
        StatusRepository.getInstance(logger).upsertStatus(status.id, status);
    }
}
