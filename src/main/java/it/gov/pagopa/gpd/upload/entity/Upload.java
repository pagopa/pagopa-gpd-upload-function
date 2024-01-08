package it.gov.pagopa.gpd.upload.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Upload {
    private int current;
    private int total;
    private ArrayList<String> successIUPD;
    private ArrayList<FailedIUPD> failedIUPDs;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime start;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime end;

    public void addFailures(FailedIUPD failedIUPD) {
        this.failedIUPDs.add(failedIUPD);
    }
}
