package it.gov.pagopa.gpd.upload;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.gpd.upload.model.AppInfo;

import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Azure Functions with Azure Http trigger. */
public class Info {
	private static final Logger log = LoggerFactory.getLogger(Info.class);

	/**
	 * This function will be invoked when a Http Trigger occurs
	 *
	 * @return
	 */
	@FunctionName("Info")
	public HttpResponseMessage run (
			@HttpTrigger(name = "InfoTrigger",
					methods = {HttpMethod.GET},
					route = "info",
					authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
			final ExecutionContext context) {

		return request.createResponseBuilder(HttpStatus.OK)
					   .header("Content-Type", "application/json")
					   .body(getInfo("/maven-archiver/pom.properties"))
					   .build();
	}

	public synchronized AppInfo getInfo(String path) {
		String version = null;
		String name = null;
		try {
			Properties properties = new Properties();
			try (InputStream inputStream = getResourceAsStream(path)) {
			    if (inputStream != null) {
			        properties.load(inputStream);
			        version = properties.getProperty("version", null);
			        name = properties.getProperty("artifactId", null);
			    }
			}
		} catch (Exception e) {
			log.error("[Info] Impossible to retrieve information from pom.properties file.", e);
		}
		return AppInfo.builder().version(version).environment("azure-fn").name(name).build();
	}
	
	protected InputStream getResourceAsStream(String path) {
	    return getClass().getResourceAsStream(path);
	}
}
