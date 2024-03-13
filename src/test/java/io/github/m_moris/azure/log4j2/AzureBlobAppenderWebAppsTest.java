package io.github.m_moris.azure.log4j2;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang3.StringUtils;
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

        if (StringUtils.isNotEmpty(System.getProperty("azure-pipelines"))) {
            System.out.println("Use properties.");
            System.setProperty("DIAGNOSTICS_AZUREBLOBCONTAINERSASURL", System.getProperty("sasUrl"));
        } else {

            System.out.println("Use config file.");
            ObjectMapper mapper = new ObjectMapper();
            try (InputStream is = ClassLoader.getSystemResourceAsStream("azureconfig.json")) {
                _azureConfig = mapper.readValue(is, AzureConfig.class);
                System.setProperty("DIAGNOSTICS_AZUREBLOBCONTAINERSASURL", _azureConfig.sasUrl);
            }
        }
    }

    @Test
    public void test() {
        System.setProperty("WEBSITE_SITE_NAME", "testwesitename");
        System.setProperty("WEBSITE_INSTANCE_ID", "testinstanceid");
        try (var context = Configurator.initialize("test", "config-webapps.xml")) {
            var logger = context.getLogger(AzureBlobAppenderWebAppsTest.class.getName());
            logger.info("info message");
            logger.debug("debug message");
            logger.info("info message");
            logger.warn("warn message");
            logger.error("error message", new IOException("test"));
            assertTrue(true);
        }
        System.out.println("done");
    }
}
