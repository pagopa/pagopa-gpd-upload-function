package it.gov.pagopa.gpd.upload;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.gpd.upload.model.AppInfo;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class InfoTest {

	@Mock
    ExecutionContext context;

    @Spy
    Info infoFunction;

    @Test
    void runOK() {
        // test precondition
        final HttpResponseMessage.Builder builder = mock(HttpResponseMessage.Builder.class);
        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

        HttpResponseMessage responseMock = mock(HttpResponseMessage.class);
        doReturn(HttpStatus.OK).when(responseMock).getStatus();
        doReturn(builder).when(builder).body(any());
        doReturn(responseMock).when(builder).build();
        doReturn(builder).when(request).createResponseBuilder(any(HttpStatus.class));
        doReturn(builder).when(builder).header(anyString(), anyString());

        // test execution
        HttpResponseMessage response = infoFunction.run(request, context);

        // test assertion
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @SneakyThrows
    @Test
    void getInfoOk() {

        // Mocking service creation
        String path = "/META-INF/maven/it.gov.pagopa.gpd.upload/gpd-upload-function/pom.properties";

        // Execute function
        AppInfo response = infoFunction.getInfo(path);

        // Checking assertions
        assertNotNull(response.getName());
        assertNotNull(response.getVersion());
        assertNotNull(response.getEnvironment());
    }

    @SneakyThrows
    @Test
    void getInfoKo() {

        // Mocking service creation
        String path = "/META-INF/maven/it.gov.pagopa.gpd.upload/gpd-upload-function/fake";

        // Execute function
        AppInfo response = infoFunction.getInfo(path);

        // Checking assertions
        assertNull(response.getName());
        assertNull(response.getVersion());
        assertNotNull(response.getEnvironment());
    }
    
    @Test
    void getInfoReturnsDefaultInfoWhenPropertiesLoadingFails() throws IOException {
        InputStream brokenInputStream = mock(InputStream.class);

        doReturn(brokenInputStream)
                .when(infoFunction)
                .getResourceAsStream("/broken/pom.properties");

        doReturn(1)
                .doThrow(new IOException("boom"))
                .when(brokenInputStream)
                .read(any(byte[].class), anyInt(), anyInt());

        AppInfo response = infoFunction.getInfo("/broken/pom.properties");

        assertNull(response.getName());
        assertNull(response.getVersion());
        assertEquals("azure-fn", response.getEnvironment());
    }

}
