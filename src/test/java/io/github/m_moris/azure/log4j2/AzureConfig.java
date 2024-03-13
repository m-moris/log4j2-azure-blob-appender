package io.github.m_moris.azure.log4j2;

public class AzureConfig {

    public AzureConfig() {
    }

    public String sasUrl;
    public String accountName;
    public String accountKey;
    public String containerName;
    public String containerUri;

    @Override
    public String toString() {
      return "AzureConfig [sasUrl=" + sasUrl + ", accountName=" + accountName + ", accountKey=" + accountKey
        + ", containerName=" + containerName + ", containerUri=" + containerUri + "]";
    }
}