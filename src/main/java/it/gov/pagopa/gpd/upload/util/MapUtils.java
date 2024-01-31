package it.gov.pagopa.gpd.upload.util;

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
}
