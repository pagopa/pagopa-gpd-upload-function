package it.gov.pagopa.gpd.upload.util;

import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.entity.ResponseEntry;
import it.gov.pagopa.gpd.upload.entity.Status;
import it.gov.pagopa.gpd.upload.entity.Upload;
import it.gov.pagopa.gpd.upload.model.UploadReport;

import java.util.ArrayList;
import java.util.List;

public class MapUtils {

    public static UploadReport convert(Status status) {
        if(status == null)
            return UploadReport.builder().build();

        Upload upload = status.getUpload();
        List<ResponseEntry> responses = getResponseEntries(upload);

        return UploadReport.builder()
                       .uploadID(status.getId())
                       .processedItem(upload.getCurrent())
                       .submittedItem(upload.getTotal())
                       .startTime(upload.getStart())
                       .endTime(upload.getEnd())
                       .responses(responses)
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

    public static String getKey(HttpStatus status) {
        return switch (status) {
            case CREATED -> "created";
            case OK -> "ok";
            case NOT_FOUND -> "notFound";
            case CONFLICT -> "conflict";
            case UNAUTHORIZED, BAD_REQUEST, FORBIDDEN -> "badRequest";
            case INTERNAL_SERVER_ERROR -> "serverError";
            default -> status.name();
        };
    }

    private static List<ResponseEntry> getResponseEntries(Upload upload) {
        ResponseEntry ok = upload.getOk();
        ResponseEntry created = upload.getCreated();
        ResponseEntry badRequest = upload.getBadRequest();
        ResponseEntry notFound = upload.getNotFound();
        ResponseEntry conflict = upload.getConflict();
        List<ResponseEntry> responses = new ArrayList<>();

        if(!ok.getRequestIDs().isEmpty())
            responses.add(ok);
        if(!created.getRequestIDs().isEmpty())
            responses.add(created);
        if(!badRequest.getRequestIDs().isEmpty())
            responses.add(badRequest);
        if(!notFound.getRequestIDs().isEmpty())
            responses.add(notFound);
        if(!conflict.getRequestIDs().isEmpty())
            responses.add(conflict);
        return responses;
    }
}
