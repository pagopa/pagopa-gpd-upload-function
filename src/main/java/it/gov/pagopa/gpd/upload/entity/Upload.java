package it.gov.pagopa.gpd.upload.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Upload {
    private int current;
    private int total;
    private ArrayList<String> successIUPD;
    private ArrayList<String> failedIUPD;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime start;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime end;
}
