package it.gov.pagopa.gpd.upload.functions.util;

import it.gov.pagopa.gpd.upload.model.UploadReport;
import it.gov.pagopa.gpd.upload.util.MapUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static it.gov.pagopa.gpd.upload.functions.util.TestUtil.getMockStatus;

public class MapUtilsTest {

    @Test
    void runOK() {
        UploadReport report = MapUtils.convert(getMockStatus());
        Assertions.assertNotNull(report);
    }
}
