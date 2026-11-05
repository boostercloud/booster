package io.boostercloud.booster.rockets.uploads;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageTest {
    @TempDir Path temp;
    private static final byte[] SECRET = new byte[32];
    private static final StorageConfiguration CONFIG = new StorageConfiguration("local", "assets", List.of("**"));
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-04T12:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    private record Running(HttpServer server, LocalFileStorage storage) implements AutoCloseable {
        public void close() throws Exception { server.stop(0); storage.close(); }
    }
    private Running start(Consumer<UploadNotification> callback, Clock clock, long limit) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var store = new LocalFileStorage(temp.resolve("storage"), URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                SECRET, List.of(CONFIG), callback, clock, limit);
        server.createContext("/files", store); server.start();
        return new Running(server, store);
    }
    private static HttpResponse<byte[]> put(URI url, byte[] bytes) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(url).header("Content-Type", "image/png").header("x-ms-meta-owner", "alice")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static HttpResponse<byte[]> get(URI url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test void realTransfersMetadataListingAndSameUrlRetrySurviveRestart() throws Exception {
        var notifications = new CopyOnWriteArrayList<UploadNotification>();
        var clock = new MutableClock();
        byte[] content = {0, 1, 2, -1, 25};
        try (var running = start(notifications::add, clock, 4096)) {
            var upload = running.storage.presignedPut(CONFIG, "incoming", "foto π.png", Duration.ofHours(1));
            assertEquals(200, put(upload, content).statusCode());
            assertEquals(200, put(upload, content).statusCode());
            assertEquals(1, notifications.size());
            assertEquals(409, put(upload, new byte[]{9}).statusCode());
            var download = running.storage.presignedGet(CONFIG, "incoming", "foto π.png", Duration.ofHours(1));
            var response = get(download);
            assertEquals(200, response.statusCode()); assertArrayEquals(content, response.body());
            assertEquals("alice", response.headers().firstValue("x-ms-meta-owner").orElseThrow());
            var items = running.storage.list(CONFIG, "incoming");
            assertEquals(1, items.size()); assertEquals(5L, items.getFirst().properties().contentLength());
            assertEquals("image/png", items.getFirst().properties().contentType());
            assertEquals(0, running.storage.list(CONFIG, "in").size());
        }
        try (var running = start(notifications::add, clock, 4096)) {
            assertEquals(0, running.storage.resumeNotifications());
            assertArrayEquals(content, get(running.storage.presignedGet(CONFIG, "incoming", "foto π.png", Duration.ofHours(1))).body());
            var upload = running.storage.presignedPut(CONFIG, "incoming", "foto π.png", Duration.ofHours(1));
            assertEquals(200, put(upload, new byte[]{8, 7}).statusCode());
            assertEquals(2, notifications.size());
            assertEquals(1, running.storage.list(CONFIG, "incoming").size());
            assertArrayEquals(new byte[]{8, 7}, get(running.storage.presignedGet(CONFIG, "incoming", "foto π.png", Duration.ofHours(1))).body());
        }
    }

    @Test void signaturesBindMethodPathAndExpiryAndDoNotGrantListing() throws Exception {
        var clock = new MutableClock();
        try (var running = start(ignored -> {}, clock, 4096)) {
            URI put = running.storage.presignedPut(CONFIG, "incoming", "photo.png", Duration.ofSeconds(10));
            assertEquals(403, get(put).statusCode());
            URI tampered = URI.create(put.toString().replace("photo.png", "private.png"));
            assertEquals(403, put(tampered, new byte[]{1}).statusCode());
            URI changedExpiry = URI.create(put.toString().replace("expires=" + clock.now.plusSeconds(10).getEpochSecond(),
                    "expires=" + clock.now.plusSeconds(20).getEpochSecond()));
            assertEquals(403, put(changedExpiry, new byte[]{1}).statusCode());
            clock.now = clock.now.plusSeconds(10);
            assertEquals(403, put(put, new byte[]{1}).statusCode());
            assertEquals(0, running.storage.list(CONFIG, "incoming").size());
            assertThrows(IllegalArgumentException.class, () -> running.storage.presignedPut(CONFIG, "../secrets", "x", Duration.ofHours(1)));
            assertThrows(IllegalArgumentException.class, () -> running.storage.presignedPut(CONFIG, "incoming", "../x", Duration.ofHours(1)));
        }
    }

    @Test void sizeFailureDoesNotPublishPartialFileOrNotification() throws Exception {
        var notifications = new CopyOnWriteArrayList<UploadNotification>();
        try (var running = start(notifications::add, new MutableClock(), 4)) {
            var upload = running.storage.presignedPut(CONFIG, "incoming", "large.png", Duration.ofHours(1));
            assertEquals(413, put(upload, new byte[5]).statusCode());
            assertEquals(0, notifications.size()); assertEquals(0, running.storage.list(CONFIG, "incoming").size());
        }
    }

    @Test void failedNotificationRemainsDurableUntilSuccessfulReplay() throws Exception {
        var clock = new MutableClock();
        try (var running = start(ignored -> { throw new IllegalStateException("consumer unavailable"); }, clock, 4096)) {
            var upload = running.storage.presignedPut(CONFIG, "incoming", "photo.png", Duration.ofHours(1));
            assertEquals(503, put(upload, new byte[]{1, 2, 3}).statusCode());
            assertEquals(1, running.storage.list(CONFIG, "incoming").size());
        }
        var received = new CopyOnWriteArrayList<UploadNotification>();
        try (var running = start(received::add, clock, 4096)) {
            assertEquals(1, running.storage.resumeNotifications());
            assertEquals(1, received.size());
            assertEquals(0, running.storage.resumeNotifications());
            assertArrayEquals(new byte[]{1, 2, 3}, get(running.storage.presignedGet(CONFIG, "incoming", "photo.png", Duration.ofHours(1))).body());
        }
    }

    @Test void refusesSymlinkStoreAndConcurrentOpen() throws Exception {
        Path actual = Files.createDirectory(temp.resolve("actual"));
        Path symlink = temp.resolve("link"); Files.createSymbolicLink(symlink, actual);
        assertThrows(java.io.IOException.class, () -> new LocalFileStorage(symlink, URI.create("http://localhost:1234"), SECRET, List.of(CONFIG), ignored -> {}));
        try (var running = start(ignored -> {}, new MutableClock(), 4096)) {
            assertThrows(java.io.IOException.class, () -> new LocalFileStorage(temp.resolve("storage"), URI.create("http://localhost:1234"), SECRET, List.of(CONFIG), ignored -> {}));
        }
    }
}
