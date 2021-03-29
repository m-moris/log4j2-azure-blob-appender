package io.github.m_moris.azure.log4j2;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
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

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudAppendBlob;
import com.microsoft.azure.storage.blob.CloudBlobContainer;

/**
 * Appends log events to Azure Storage Blob.
 * 
 */
@Plugin(name = "AzureBlobAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class AzureBlobAppender extends AbstractAppender {

    private static String SASURL = "DIAGNOSTICS_AZUREBLOBCONTAINERSASURL";
    private static String WEBSITE_NAME = "WEBSITE_SITE_NAME";
    private static String WEBSITE_ID = "WEBSITE_INSTANCE_ID";

    private CloudBlobContainer _container;
    private String _prefix1;
    private String _prefix2;

    protected AzureBlobAppender(
            String name,
            Filter filter,
            Layout<? extends Serializable> layout,
            final boolean ignoreExceptions,
            final Property[] properties,
            String sas,
            String websiteName,
            String websiteId) throws StorageException {
        super(name, filter, layout, ignoreExceptions, properties);
        _container = new CloudBlobContainer(URI.create(sas));
        _prefix1 = websiteName;
        _prefix2 = websiteId;
    }

    protected AzureBlobAppender(
            String name,
            Filter filter,
            Layout<? extends Serializable> layout,
            final boolean ignoreExceptions,
            final Property[] properties,
            String accountName,
            String accountKey,
            String containerName,
            String prefix1,
            String prefix2) throws StorageException, URISyntaxException {

        super(name, filter, layout, ignoreExceptions, properties);
        StorageCredentialsAccountAndKey creds = new StorageCredentialsAccountAndKey(accountName, accountKey);
        _container = (new CloudStorageAccount(creds, true)).createCloudBlobClient().getContainerReference(containerName);
        _container.createIfNotExists();
        _prefix1 = prefix1;
        _prefix2 = prefix2;
    }

    @Override
    public void append(LogEvent event) {

        String name = getBlobname();

        try {
            CloudAppendBlob blob = _container.getAppendBlobReference(name);
            if (!blob.exists()) {
                blob.createOrReplace();
            }
            byte[] bytes = getLayout().toByteArray(event);

            blob.appendFromByteArray(bytes, 0, bytes.length);
        } catch (URISyntaxException | StorageException | IOException e) {

            if (!ignoreExceptions()) {
                throw new AppenderLoggingException(e);
            }
        }
    }

    private String getBlobname() {
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
     * @param name The name of the Appender.
     * @param webapps WebApps mode. If this value is true, assume it is running on WebApps.
     * @param accountName Azure storage account name. It becomes effective when WebApps is false.
     * @param accountKey Azure storage account key. It becomes effective when WebApps is false.
     * @param containerName The name of blob container. It becomes effective when WebApps is false.
     * @param prefix1 Specify directory structure. It becomes effective when WebApps is false.
     * @param prefix2 Specify directory structure. It becomes effective when WebApps is false. Can be null, empty or unset.
     * @param layout The layout to format the message.
     * @param filter The filter to filter the message.
     * @return AzureBlobAppender instance.
     */
    @PluginFactory
    public static AzureBlobAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginAttribute("webapps") boolean webapps,
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

            try {
                return new AzureBlobAppender(name, filter, layout, true, Property.EMPTY_ARRAY, sas, prefix1, prefix2);
            } catch (StorageException e) {
                throw new RuntimeException(sas + " is invalid.", e);
            }
        } else {
            failIfNullOrEmpty(accountName, "accountName");
            failIfNullOrEmpty(accountKey, "accountKey");
            failIfNullOrEmpty(containerName, "containerName");
            failIfNullOrEmpty(prefix1, "prefix1");
            try {
                return new AzureBlobAppender(name, filter, layout, true, Property.EMPTY_ARRAY, accountName, accountKey, containerName, prefix1, prefix2);
            } catch (StorageException | URISyntaxException e) {
                throw new RuntimeException("storage account is invalid.", e);
            }
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
        if(result) {
            throw new RuntimeException("Mandatory parameter missing: "+key);
        }
    }
}
