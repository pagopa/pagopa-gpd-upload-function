package it.gov.pagopa.gpd.upload.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.util.MapUtils;
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
    @Builder.Default
    private ResponseEntry ok = new ResponseEntry(HttpStatus.OK.value(), MapUtils.getDetail(HttpStatus.OK), new ArrayList<>());
    @Builder.Default
    private ResponseEntry created = new ResponseEntry(HttpStatus.CREATED.value(), MapUtils.getDetail(HttpStatus.CREATED), new ArrayList<>());
    @Builder.Default
    private ResponseEntry badRequest = new ResponseEntry(HttpStatus.BAD_REQUEST.value(), MapUtils.getDetail(HttpStatus.BAD_REQUEST), new ArrayList<>());
    @Builder.Default
    private ResponseEntry notFound = new ResponseEntry(HttpStatus.NOT_FOUND.value(), MapUtils.getDetail(HttpStatus.NOT_FOUND), new ArrayList<>());
    @Builder.Default
    private ResponseEntry conflict = new ResponseEntry(HttpStatus.CONFLICT.value(), MapUtils.getDetail(HttpStatus.CONFLICT), new ArrayList<>());
    @Builder.Default
    private ResponseEntry serverError = new ResponseEntry(HttpStatus.INTERNAL_SERVER_ERROR.value(), MapUtils.getDetail(HttpStatus.INTERNAL_SERVER_ERROR), new ArrayList<>());
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime start;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime end;
}
