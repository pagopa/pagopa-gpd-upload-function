package it.gov.pagopa.gpd.upload.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import it.gov.pagopa.gpd.upload.entity.ResponseEntry;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class UploadReport {
    public String uploadID;
    public int processedItem;
    public int submittedItem;
    public List<ResponseEntry> responses;
    @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS")
    public LocalDateTime startTime;
    @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS")
    public LocalDateTime endTime;
}
