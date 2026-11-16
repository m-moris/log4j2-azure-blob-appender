package io.github.m_moris.azure.log4j2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.sas.BlobContainerSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.blob.specialized.AppendBlobClient;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.common.policy.RequestRetryOptions;
import com.azure.storage.common.policy.RetryPolicyType;

@Timeout(30)
class AzureBlobAppenderAzuriteIT {

    // Public development credential built into Azurite, not an Azure account secret.
    private static final String ACCOUNT_KEY =
        "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";
    private static final String BLOB_NAME = "site/2026/11/13/10/instance_applicationLog.txt";
    private static final Clock CLOCK = Clock.fixed(
        LocalDateTime.of(2026, 11, 13, 10, 30).atZone(ZoneId.systemDefault()).toInstant(),
        ZoneId.systemDefault());

    private BlobContainerClient container;
    private AzureBlobAppender appender;

    @BeforeEach
    void setUp() {
        int port = Integer.parseInt(System.getProperty("azurite.port", "10000"));
        container = new BlobContainerClientBuilder()
            .endpoint("http://127.0.0.1:" + port + "/devstoreaccount1")
            .credential(new StorageSharedKeyCredential("devstoreaccount1", ACCOUNT_KEY))
            .containerName("appender-test-" + UUID.randomUUID())
            .retryOptions(new RequestRetryOptions(RetryPolicyType.FIXED, 1, Duration.ofSeconds(5), null, null, null))
            .buildClient();
        container.create();
        appender = createAppender("test");
    }

    @AfterEach
    void tearDown() {
        try {
            if (appender != null) {
                appender.stop();
            }
        } finally {
            if (container != null) {
                container.deleteIfExists();
            }
        }
    }

    private AzureBlobAppender createAppender(String name) {
        AzureBlobAppender result = new AzureBlobAppender(name, null,
            PatternLayout.newBuilder().withPattern("%m").build(), false, Property.EMPTY_ARRAY,
            container, "site", "instance", CLOCK);
        result.start();
        return result;
    }

    private void append(AzureBlobAppender target, String message) {
        target.append(Log4jLogEvent.newBuilder().setMessage(new SimpleMessage(message)).build());
    }

    private AppendBlobClient blob() {
        return container.getBlobClient(BLOB_NAME).getAppendBlobClient();
    }

    @Test
    void createsBlobAndStoresUtf8Messages() {
        append(appender, "first;\u65e5\u672c\u8a9e;");
        append(appender, "second;");

        assertEquals("first;\u65e5\u672c\u8a9e;second;", blob().downloadContent().toString());
    }

    @Test
    void preservesExistingBlobAndAppendsInOrder() {
        AppendBlobClient existing = blob();
        existing.create();
        BinaryData content = BinaryData.fromString("existing;");
        existing.appendBlock(content.toStream(), content.getLength());

        append(appender, "first;");
        append(appender, "second;");

        assertEquals("existing;first;second;", existing.downloadContent().toString());
    }

    @Test
    void recreatesDeletedBlobAndRetriesMessage() {
        append(appender, "before;");
        blob().delete();

        append(appender, "after;");
        append(appender, "last;");

        assertEquals("after;last;", blob().downloadContent().toString());
    }

    @Test
    void concurrentAppendersDoNotOverwriteOrLoseMessages() throws Exception {
        AzureBlobAppender other = createAppender("other");
        int writers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(writers);
        CountDownLatch ready = new CountDownLatch(writers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        try {
            for (int i = 0; i < writers; i++) {
                String message = "message-" + i;
                expected.add(message);
                AzureBlobAppender target = i % 2 == 0 ? appender : other;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    append(target, message + "\n");
                    return null;
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            try {
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            } finally {
                other.stop();
            }
        }

        assertEquals(expected, blob().downloadContent().toString().lines().sorted().collect(Collectors.toList()));
    }

    @Test
    void webAppsConstructorAppendsUsingContainerSas() {
        BlobContainerSasPermission permissions = new BlobContainerSasPermission()
            .setReadPermission(true)
            .setAddPermission(true)
            .setCreatePermission(true)
            .setWritePermission(true);
        String sas = container.generateSas(new BlobServiceSasSignatureValues(
            OffsetDateTime.now().plusMinutes(10), permissions));
        AzureBlobAppender webApps = new AzureBlobAppender("webapps", null,
            PatternLayout.newBuilder().withPattern("%m").build(), false, Property.EMPTY_ARRAY,
            container.getBlobContainerUrl() + "?" + sas, "site", "instance");
        webApps.start();
        try {
            append(webApps, "sas message;");
        } finally {
            webApps.stop();
        }

        Iterator<BlobItem> blobs = container.listBlobs().iterator();
        assertTrue(blobs.hasNext());
        String name = blobs.next().getName();
        assertTrue(name.startsWith("site/"));
        assertTrue(name.endsWith("/instance_applicationLog.txt"));
        assertFalse(blobs.hasNext());
        assertEquals("sas message;", container.getBlobClient(name).downloadContent().toString());
    }
}
