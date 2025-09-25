package it.gov.pagopa.gpd.upload.client;

import it.gov.pagopa.gpd.upload.model.ResponseGPD;
import it.gov.pagopa.gpd.upload.model.RetryStep;
import it.gov.pagopa.gpd.upload.util.MapUtils;
import com.microsoft.azure.functions.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import javax.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;

class GPDClientTest {

    private ResponseGPD invokeMapResponse(GPDClient client, Response resp) throws Exception {
        Method m = GPDClient.class.getDeclaredMethod("mapResponse", Response.class);
        m.setAccessible(true);
        return (ResponseGPD) m.invoke(client, resp);
    }

    @Test
    void mapResponse_200_usesDefaultDetail() throws Exception {
        Response resp = mock(Response.class);
        when(resp.getStatus()).thenReturn(HttpStatus.OK.value());
        when(resp.hasEntity()).thenReturn(false);

        GPDClient client = new GPDClient(Logger.getLogger("test"));
        ResponseGPD out = invokeMapResponse(client, resp);

        assertEquals(RetryStep.DONE, out.getRetryStep());
        assertEquals(200, out.getStatus());
        assertEquals("200 - " + MapUtils.getDetail(HttpStatus.OK), out.getDetail());
    }

    // ========= Parameterized =========
    @ParameterizedTest(name = "[{index}] body={0}")
    @MethodSource("badRequestBodies")
    void mapResponse_400_usedAsMessage(String body, String expectedDetail) throws Exception {
        Response resp = mock(Response.class);
        when(resp.getStatus()).thenReturn(HttpStatus.BAD_REQUEST.value());
        when(resp.hasEntity()).thenReturn(true);
        when(resp.readEntity(eq(String.class))).thenReturn(body);

        GPDClient client = new GPDClient(Logger.getLogger("test"));
        ResponseGPD out = invokeMapResponse(client, resp);

        assertEquals(RetryStep.ERROR, out.getRetryStep());
        assertEquals(400, out.getStatus());
        assertEquals(expectedDetail, out.getDetail());
    }

    private static Stream<Arguments> badRequestBodies() {
        return Stream.of(
            Arguments.of("{\"detail\":\"Invalid IUV\",\"status\":400}", "400 - Invalid IUV"),
            Arguments.of("{\"detail\":\"Invalid IUV\"}",               "400 - Invalid IUV"),
            Arguments.of("Bad input in field XYZ",                     "400 - Bad input in field XYZ")
        );
    }
    // ================================================

    @Test
    void mapResponse_500_emptyBody_fallsBackToMapUtils() throws Exception {
        Response resp = mock(Response.class);
        when(resp.getStatus()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR.value());
        when(resp.hasEntity()).thenReturn(false);

        GPDClient client = new GPDClient(Logger.getLogger("test"));
        ResponseGPD out = invokeMapResponse(client, resp);

        assertEquals(RetryStep.RETRY, out.getRetryStep());
        assertEquals(500, out.getStatus());
        assertEquals("500 - " + MapUtils.getDetail(HttpStatus.INTERNAL_SERVER_ERROR), out.getDetail());
    }
}
