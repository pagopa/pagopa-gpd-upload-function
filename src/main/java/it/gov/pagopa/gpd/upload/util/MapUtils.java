package it.gov.pagopa.gpd.upload.util;

import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.model.UploadReport;

public class MapUtils {

    public static UploadReport convert(Status status) {
        return UploadReport.builder()
                .uploadID(status.getId())
                .processedItem(status.getUpload().getCurrent())
                .submittedItem(status.getUpload().getTotal())
                .startTime(status.getUpload().getStart())
                .endTime(status.getUpload().getEnd())
                .responses(status.getUpload().getResponses())
                .build();
    }

    public static String getDetail(HttpStatus status) {
        return switch (status) {
            case CREATED -> "Debt position CREATED";
            case OK -> "Debt position operation OK";
            case NOT_FOUND -> "Debt position NOT FOUND";
            case CONFLICT -> "Debt position IUPD or NAV/IUV already exists for organization code";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case INTERNAL_SERVER_ERROR -> "Internal Server Error: operation not completed";
            case BAD_REQUEST -> "Bad request";
            default -> status.toString();
        };
    }
}
