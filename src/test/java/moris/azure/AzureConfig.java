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
        b.append(sasUrl)
          .append("\n")
          .append(accountName)
          .append("\n")
          .append(accountKey)
          .append("\n")
          .append(containerName);
        return b.toString();
    }
}