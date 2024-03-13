package io.github.m_moris.azure.log4j2;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.specialized.AppendBlobClient;
import com.azure.storage.common.StorageSharedKeyCredential;

/**
 * Appends log events to Azure Storage Blob.
 */
@Plugin(name = "AzureBlobAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class AzureBlobAppender extends AbstractAppender {

    private static final String SASURL = "DIAGNOSTICS_AZUREBLOBCONTAINERSASURL";
    private static final String WEBSITE_NAME = "WEBSITE_SITE_NAME";
    private static final String WEBSITE_ID = "WEBSITE_INSTANCE_ID";

    private BlobContainerClient _container;
    private final String _prefix1;
    private final String _prefix2;

    protected AzureBlobAppender(
        String name,
        Filter filter,
        Layout<? extends Serializable> layout,
        final boolean ignoreExceptions,
        final Property[] properties,
        String sas,
        String websiteName,
        String websiteId) {
        super(name, filter, layout, ignoreExceptions, properties);

        _container = new BlobContainerClientBuilder()
            .endpoint(sas)
            .buildClient();
        _prefix1 = websiteName;
        _prefix2 = websiteId;
    }

    protected AzureBlobAppender(
        String name,
        Filter filter,
        Layout<? extends Serializable> layout,
        final boolean ignoreExceptions,
        final Property[] properties,
        String containerUri,
        String accountName,
        String accountKey,
        String containerName,
        String prefix1,
        String prefix2) {

        super(name, filter, layout, ignoreExceptions, properties);

        if (isNullOrEmpty(containerUri)) {
            StorageSharedKeyCredential storageSharedKeyCredential = new StorageSharedKeyCredential(accountName, accountKey);

            _container = new BlobContainerClientBuilder()
                .endpoint("https://" + accountName + ".blob.core.windows.net")
                .credential(storageSharedKeyCredential)
                .containerName(containerName)
                .buildClient();
        } else {
            TokenCredential credential = new DefaultAzureCredentialBuilder().build();
            _container = new BlobContainerClientBuilder()
                .endpoint(containerUri)
                .credential(credential)
                .buildClient();
        }

        if (!_container.exists()) {
            _container.create();
        }

        _prefix1 = prefix1;
        _prefix2 = prefix2;
    }

    @Override
    public void append(LogEvent event) {

        String name = getBlobName();

        try {

            AppendBlobClient append = _container.getBlobClient(name).getAppendBlobClient();
            if (!append.exists()) {
                append.create();
            }
            byte[] bytes = getLayout().toByteArray(event);

            try (InputStream in = new ByteArrayInputStream(bytes)) {
                append.appendBlock(in, bytes.length);
            }
        } catch (IOException e) {
            if (!ignoreExceptions()) {
                throw new AppenderLoggingException(e);
            }
        }
    }

    private String getBlobName() {
        String dfmt = (new SimpleDateFormat("yyyy/MM/dd/HH")).format(new Date());
        if (isNullOrEmpty(_prefix2)) {
            return String.format("%s/%s_applicationLog.txt", dfmt, _prefix1);
        } else {
            return String.format("%s/%s/%s_applicationLog.txt", _prefix1, dfmt, _prefix2);
        }
    }

    /**
     * Create AzureBlobAppender.
     *
     * @param name          The name of the Appender.
     * @param webapps       WebApps mode. If this value is true, assume it is running on WebApps.
     * @param containerUri  Azure container URI. If container uri is set use DefaultAzureCredential. It becomes effective when WebApps is false. 
     * @param accountName   Azure storage account name. It becomes effective when WebApps is false.
     * @param accountKey    Azure storage account key. It becomes effective when WebApps is false.
     * @param containerName The name of blob container. It becomes effective when WebApps is false.
     * @param prefix1       Specify directory structure. It becomes effective when WebApps is false.
     * @param prefix2       Specify directory structure. It becomes effective when WebApps is false. Can be null, empty or unset.
     * @param layout        The layout to format the message.
     * @param filter        The filter to filter the message.
     * @return AzureBlobAppender instance.
     */
    @PluginFactory
    public static AzureBlobAppender createAppender(
        @PluginAttribute("name") String name,
        @PluginAttribute("webapps") boolean webapps,
        @PluginAttribute("containerUri") final String containerUri,
        @PluginAttribute("accountName") final String accountName,
        @PluginAttribute("accountKey") final String accountKey,
        @PluginAttribute("containerName") final String containerName,
        @PluginAttribute("prefix1") String prefix1,
        @PluginAttribute("prefix2") String prefix2,
        @PluginElement("Layout") Layout<? extends Serializable> layout,
        @PluginElement("Filter") final Filter filter) {

        if (layout == null) {
            layout = PatternLayout.createDefaultLayout();
        }

        if (webapps) {
            String sas = getPropertyOrFail(SASURL);
            prefix1 = getPropertyOrFail(WEBSITE_NAME);
            prefix2 = getPropertyOrFail(WEBSITE_ID);

            return new AzureBlobAppender(name, filter, layout, true, Property.EMPTY_ARRAY, sas, prefix1, prefix2);

        } else {

            if (isNullOrEmpty(containerUri)) {
                failIfNullOrEmpty(accountName, "accountName");
                failIfNullOrEmpty(accountKey, "accountKey");
                failIfNullOrEmpty(containerName, "containerName");
            } else {
                failIfNullOrEmpty(containerUri, "containerUri");
            }

            failIfNullOrEmpty(prefix1, "prefix1");

            return new AzureBlobAppender(name, filter, layout, true, Property.EMPTY_ARRAY, containerUri, accountName,
                accountKey,
                containerName, prefix1, prefix2);
        }
    }

    private static String getProperty(String key) {
        String value = System.getenv(key);
        if (!isNullOrEmpty(value)) {
            return value;
        }
        value = System.getProperty(key);
        return value;
    }

    private static String getPropertyOrFail(String key) {
        String value = getProperty(key);

        failIfNullOrEmpty(value, key);
        return value;
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static void failIfNullOrEmpty(String value, String key) {
        boolean result = value == null || value.trim().isEmpty();
        if (result) {
            throw new RuntimeException("Mandatory parameter missing: " + key);
        }
    }
}
