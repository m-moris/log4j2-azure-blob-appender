package io.github.m_moris.azure.log4j2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.common.policy.RequestRetryOptions;
import com.azure.storage.common.policy.RetryPolicyType;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Timeout(15)
class AzureBlobAppenderCachingTest {

    private static final String FIRST_BLOB = "/logs/site/2026/11/13/10/instance_applicationLog.txt";
    private static final String NEXT_BLOB = "/logs/site/2026/11/13/11/instance_applicationLog.txt";

    private final StorageHttpClient storage = new StorageHttpClient();
    private final MutableClock clock = new MutableClock();
    private final AzureBlobAppender appender = createAppender("instance");

    private AzureBlobAppender createAppender(String prefix2) {
        BlobContainerClient container = new BlobContainerClientBuilder()
            .endpoint("https://example.blob.core.windows.net/logs")
            .httpClient(storage)
            .retryOptions(new RequestRetryOptions(RetryPolicyType.FIXED, 1, Duration.ofSeconds(5), null, null, null))
            .buildClient();
        return new AzureBlobAppender("test", null, PatternLayout.newBuilder().withPattern("%m").build(),
            false, Property.EMPTY_ARRAY, container, "site", prefix2, clock);
    }

    private void append(String message) {
        appender.append(Log4jLogEvent.newBuilder().setMessage(new SimpleMessage(message)).build());
    }

    @Test
    void reusesBlobWithoutCheckingExistenceAndPreservesExistingContent() {
        storage.blobs.put(FIRST_BLOB, "existing;");

        append("first;");
        append("second;");

        assertEquals(Map.of(FIRST_BLOB, "existing;first;second;"), storage.blobs);
        assertEquals(1, storage.creates);
        assertEquals(2, storage.appends);
    }

    @Test
    void createsNewBlobWhenHourChanges() {
        append("first;");
        clock.instant = clock.instant.plusSeconds(3600);
        append("second;");
        append("third;");

        assertEquals("first;", storage.blobs.get(FIRST_BLOB));
        assertEquals("second;third;", storage.blobs.get(NEXT_BLOB));
        assertEquals(2, storage.creates);
        assertEquals(3, storage.appends);
    }

    @Test
    void preservesNamingWithoutPrefix2() {
        AzureBlobAppender withoutPrefix2 = createAppender(null);
        withoutPrefix2.append(Log4jLogEvent.newBuilder().setMessage(new SimpleMessage("message")).build());

        assertEquals("message", storage.blobs.get("/logs/2026/11/13/10/site_applicationLog.txt"));
    }

    @Test
    void initializesOnceForConcurrentWriters() throws Exception {
        int writers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(writers);
        CountDownLatch ready = new CountDownLatch(writers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < writers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    append("x");
                    return null;
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        assertEquals("x".repeat(writers), storage.blobs.get(FIRST_BLOB));
        assertEquals(1, storage.creates);
        assertEquals(writers, storage.appends);
    }

    @Test
    void recreatesDeletedBlobAndRetriesFullMessage() {
        append("before;");
        storage.blobs.remove(FIRST_BLOB);
        append("after;");
        append("last;");

        assertEquals("after;last;", storage.blobs.get(FIRST_BLOB));
        assertEquals(2, storage.creates);
        assertEquals(4, storage.appends);
    }

    @Test
    void doesNotCacheFailedCreation() {
        storage.createErrors.add("AuthorizationPermissionMismatch");
        assertThrows(BlobStorageException.class, () -> append("failed;"));
        append("success;");

        assertEquals("success;", storage.blobs.get(FIRST_BLOB));
        assertEquals(2, storage.creates);
        assertEquals(1, storage.appends);
    }

    @Test
    void propagatesOtherErrorsWithoutRecreatingOrRetrying() {
        append("before;");
        for (String error : List.of("AuthorizationPermissionMismatch", "ContainerNotFound", "InternalError")) {
            storage.appendErrors.add(error);
            BlobStorageException exception = assertThrows(BlobStorageException.class, () -> append("failed;"));
            assertEquals(BlobErrorCode.fromString(error), exception.getErrorCode());
        }

        assertEquals("before;", storage.blobs.get(FIRST_BLOB));
        assertEquals(1, storage.creates);
        assertEquals(4, storage.appends);
    }

    @Test
    void retriesMissingBlobOnlyOnce() {
        append("before;");
        storage.appendErrors.add("BlobNotFound");
        storage.appendErrors.add("BlobNotFound");

        BlobStorageException exception = assertThrows(BlobStorageException.class, () -> append("failed;"));

        assertEquals(BlobErrorCode.BLOB_NOT_FOUND, exception.getErrorCode());
        assertEquals("before;", storage.blobs.get(FIRST_BLOB));
        assertEquals(2, storage.creates);
        assertEquals(3, storage.appends);
    }

    private static class MutableClock extends Clock {
        private Instant instant = LocalDateTime.of(2026, 11, 13, 10, 30)
            .atZone(ZoneId.systemDefault()).toInstant();

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static class StorageHttpClient implements HttpClient {
        private final Map<String, String> blobs = new HashMap<>();
        private final Deque<String> createErrors = new ArrayDeque<>();
        private final Deque<String> appendErrors = new ArrayDeque<>();
        private int creates;
        private int appends;

        @Override
        public Mono<HttpResponse> send(HttpRequest request) {
            return Mono.fromSupplier(() -> respond(request));
        }

        private synchronized HttpResponse respond(HttpRequest request) {
            assertEquals(HttpMethod.PUT, request.getHttpMethod(), "Existence checks must not be sent");
            String path = URI.create(request.getUrl().toString()).getPath();
            if ("comp=appendblock".equals(request.getUrl().getQuery())) {
                appends++;
                // Consume even failed requests to exercise retrying with a fresh stream.
                String body = request.getBodyAsBinaryData().toString();
                if (!appendErrors.isEmpty()) {
                    return error(request, appendErrors.remove());
                }
                if (!blobs.containsKey(path)) {
                    return error(request, "BlobNotFound");
                }
                blobs.put(path, blobs.get(path) + body);
            } else {
                creates++;
                assertEquals("*", request.getHeaders().getValue("If-None-Match"),
                    "Creation must not overwrite an existing blob");
                assertEquals("AppendBlob", request.getHeaders().getValue("x-ms-blob-type"));
                if (!createErrors.isEmpty()) {
                    return error(request, createErrors.remove());
                }
                if (blobs.containsKey(path)) {
                    return error(request, "BlobAlreadyExists");
                }
                blobs.put(path, "");
            }
            return new StorageResponse(request, 201, null);
        }

        private HttpResponse error(HttpRequest request, String code) {
            int status = code.endsWith("NotFound") ? 404
                : "BlobAlreadyExists".equals(code) ? 409
                : "InternalError".equals(code) ? 500 : 403;
            return new StorageResponse(request, status, code);
        }
    }

    private static class StorageResponse extends HttpResponse {
        private final int status;
        private final HttpHeaders headers = new HttpHeaders()
            .set("Content-Length", "0")
            .set("x-ms-request-server-encrypted", "true")
            .set("x-ms-blob-append-offset", "0")
            .set("x-ms-blob-committed-block-count", "1");

        StorageResponse(HttpRequest request, int status, String errorCode) {
            super(request);
            this.status = status;
            if (errorCode != null) {
                headers.set("x-ms-error-code", errorCode);
            }
        }

        @Override
        public int getStatusCode() {
            return status;
        }

        @Override
        public String getHeaderValue(String name) {
            return headers.getValue(name);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public Flux<ByteBuffer> getBody() {
            return Flux.empty();
        }

        @Override
        public Mono<byte[]> getBodyAsByteArray() {
            return Mono.just(new byte[0]);
        }

        @Override
        public Mono<String> getBodyAsString() {
            return Mono.just("");
        }

        @Override
        public Mono<String> getBodyAsString(Charset charset) {
            return Mono.just("");
        }
    }
}
