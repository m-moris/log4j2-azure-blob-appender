package moris.azure;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AzureBlobAppenderWebAppsTest {

    private AzureConfig _azureConfig;

    @BeforeEach

    public void prepare() throws JsonParseException, JsonMappingException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = ClassLoader.getSystemResourceAsStream("azureconfig.json")) {
            _azureConfig = mapper.readValue(is, AzureConfig.class);
            System.setProperty("DIAGNOSTICS_AZUREBLOBCONTAINERSASURL", _azureConfig.sasUrl);
        }
    }

    @Test
    public void test() {
        System.setProperty("WEBSITE_SITE_NAME", "testwesitename");
        System.setProperty("WEBSITE_INSTANCE_ID", "testinstanceid");
        Logger logger = Configurator.initialize("test", "config-webapps.xml").getLogger(AzureBlobAppenderWebAppsTest.class.getName());
        logger.info("info message");
        logger.debug("debug message");
        logger.info("info message");
        logger.warn("warn message");
        logger.error("error message", new IOException("test"));
        assertTrue(true);
    }
}
