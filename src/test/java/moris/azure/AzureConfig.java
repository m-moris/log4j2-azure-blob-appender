package moris.azure;

public class AzureConfig {
    public AzureConfig() {
    }

    public String sasUrl;
    public String accountName;
    public String accountKey;
    public String containerName;

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append(sasUrl);
        return b.toString();
    }
}