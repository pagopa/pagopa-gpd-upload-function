package it.gov.pagopa.gpd.upload.model;

import java.util.List;

public interface ModelGPD<T> {

    List<String> getIUPD();

    T filterById(List<String> ids);
}
