package io.boostercloud.booster.rockets.uploads;

import io.boostercloud.booster.core.*;
import io.boostercloud.booster.store.JdbcEventStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FileUploadContractTest {
    @TempDir Path temp;
    static final StorageConfiguration CONFIG = new StorageConfiguration("media", "assets", List.of("**"));
    static final FileStorage UNUSED = new FileStorage() {
        public URI presignedPut(StorageConfiguration c, String d, String f, Duration v) { return URI.create("https://example.test/put"); }
        public URI presignedGet(StorageConfiguration c, String d, String f, Duration v) { return URI.create("https://example.test/get"); }
        public List<ListItem> list(StorageConfiguration c, String d) { return List.of(); }
    };
    static FileStorage azure(String account) {
        return new AzureBlobStorage(new com.azure.storage.blob.BlobServiceClientBuilder()
                .endpoint("https://" + account + ".blob.core.windows.net").buildClient(), Clock.systemUTC());
    }

    @Test void recordedTypeScriptDirectoryFixtures() throws Exception {
        // Cases copied from rocket-file-uploads-types/test/utils.test.ts at cc6d6463.
        try (var stream = getClass().getResourceAsStream("/contracts/uploads/directories.json")) {
            for (var fixture : Json.MAPPER.readTree(stream)) {
                List<String> patterns = new ArrayList<>(); fixture.get("patterns").forEach(p -> patterns.add(p.asText()));
                assertEquals(fixture.get("allowed").asBoolean(), DirectoryPatterns.matches(fixture.get("source").asText(), patterns), fixture.toString());
            }
        }
        assertFalse(DirectoryPatterns.matches("../private", List.of("**")));
        assertFalse(DirectoryPatterns.matches(".secrets", List.of("**")));
        assertTrue(DirectoryPatterns.matches("incoming/images", List.of("incoming/{images,documents}")));
    }

    @Test void recordedMinimatchExtglobNegationBraceAndEscapeContracts() throws Exception {
        try (var stream = getClass().getResourceAsStream("/contracts/uploads/minimatch-3.1.5.json")) {
            for (var fixture : Json.MAPPER.readTree(stream)) {
                List<String> patterns = new ArrayList<>(); fixture.get("patterns").forEach(p -> patterns.add(p.asText()));
                assertEquals(fixture.get("allowed").asBoolean(), DirectoryPatterns.matches(fixture.get("source").asText(), patterns), fixture.toString());
            }
        }
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertTrue(DirectoryPatterns.matches("a".repeat(250), List.of("+(a|aa)"))));
        assertThrows(IllegalArgumentException.class, () -> new StorageConfiguration("media", "assets", List.of("{1..1000000}")));
    }

    @Test void storageSelectionAndCapabilitiesRequireExplicitAuthorization() {
        var context = RequestContext.anonymous();
        var handler = new FileHandler(List.of(CONFIG), Map.of("media", UNUSED), "MeDiA", context, AccessPolicy.deny(), AccessPolicy.publicAccess());
        assertThrows(BoosterException.class, () -> handler.presignedPut("incoming", "x"));
        assertEquals("https://example.test/get", handler.presignedGet("incoming", "x").toString());
        assertThrows(IllegalArgumentException.class, () -> handler.presignedGet("../private", "x"));
        assertThrows(IllegalArgumentException.class, () -> handler.presignedGet("incoming", "../x"));
        assertThrows(IllegalArgumentException.class, () -> new FileHandler(List.of(CONFIG,
                new StorageConfiguration("other", "assets", List.of("**"))), Map.of("media", UNUSED), null,
                context, AccessPolicy.publicAccess(), AccessPolicy.publicAccess()));
        assertThrows(IllegalArgumentException.class, () -> new FileHandler(List.of(CONFIG), Map.of("media", UNUSED), "missing",
                context, AccessPolicy.publicAccess(), AccessPolicy.publicAccess()));
    }

    @Test void requestAwarePoliciesReceiveTenantAndFileOperation() {
        var policy = AccessPolicy.input((context, input) -> "tenant-a".equals(context.metadata().get("tenant"))
                && input.path("directory").asText().startsWith("tenant-a/")
                && input.path("storageName").asText().equals("media")
                && input.path("containerName").asText().equals("assets")
                && input.path("operation").asText().equals("presignedPut"));
        var context = new RequestContext("upload", new UserContext("alice", Set.of(), Map.of()), Map.of("tenant", "tenant-a"));
        var handler = new FileHandler(List.of(CONFIG), Map.of("media", UNUSED), null, context, policy, policy);
        assertEquals("https://example.test/put", handler.presignedPut("tenant-a/incoming", "file").toString());
        assertThrows(BoosterException.class, () -> handler.presignedPut("tenant-b/incoming", "file"));
        assertThrows(BoosterException.class, () -> handler.presignedGet("tenant-a/incoming", "file"));
        assertThrows(BoosterException.class, () -> handler.list("tenant-a/incoming"));
    }

    public record FileView(String id, String path) {}
    private static Booster runtime(FileUploadRocket rocket, Path db) {
        return rocket.install(Booster.builder(new JdbcEventStore(db)))
                .readModel(FileView.class, UploadedFileEntity.class, AccessPolicy.publicAccess(),
                        (file, old) -> new FileView(file.id(), file.metadata().path("blobTrigger").asText())).build();
    }

    @Test void notificationsReduceProjectDeduplicateAndSurviveRestart() {
        var rocket = new FileUploadRocket(List.of(CONFIG), Map.of("media", UNUSED), AccessPolicy.deny(), AccessPolicy.publicAccess());
        var metadata = Json.MAPPER.createObjectNode().put("blobTrigger", "assets/incoming/photo.jpg").put("eTag", "v1");
        var notification = new UploadNotification("media", "assets", "incoming/photo.jpg", "v1", metadata);
        String id = new UploadedFileEvent(metadata).entityID();
        Path db = temp.resolve("events");
        try (var runtime = runtime(rocket, db)) {
            assertTrue(rocket.fileUploaded(runtime, notification));
            assertTrue(rocket.fileUploaded(runtime, notification));
            assertEquals(1, runtime.position());
            assertEquals("assets/incoming/photo.jpg", runtime.getReadModel("FileView", id, RequestContext.anonymous()).path("path").asText());
            assertFalse(rocket.fileUploaded(runtime, new UploadNotification("media", "other-container", "incoming/photo.jpg", "v1", metadata)));
            assertFalse(rocket.fileUploaded(runtime, new UploadNotification("other-account", "assets", "incoming/photo.jpg", "v1", metadata)));
        }
        try (var runtime = runtime(rocket, db)) {
            assertTrue(rocket.fileUploaded(runtime, notification));
            assertEquals(1, runtime.position());
            assertFalse(runtime.getReadModel("FileView", id, RequestContext.anonymous()).isNull());
        }
    }

    @Test void azureTriggersSelectExactContainerAndDeduplicateChangingDeliveryMetadata() {
        var selected = new StorageConfiguration("media", "assets", List.of("incoming/**"));
        var rocket = new FileUploadRocket(List.of(selected), Map.of("media", azure("media")), AccessPolicy.deny(), AccessPolicy.publicAccess());
        try (var runtime = runtime(rocket, temp.resolve("triggers"))) {
            var event = Json.MAPPER.createObjectNode().put("eventType", "Microsoft.Storage.BlobCreated");
            event.putObject("data").put("url", "https://media.blob.core.windows.net/assets/incoming/photos/a.jpg")
                    .put("eTag", "version-1").put("contentLength", 10);
            assertTrue(rocket.handleCloudEvent(runtime, event));
            event.put("id", "new-delivery-id");
            assertTrue(rocket.handleCloudEvent(runtime, event));
            assertEquals(1, runtime.position());
            var invocation = Json.MAPPER.createObjectNode();
            invocation.putObject("context").putObject("triggerMetadata")
                    .put("blobTrigger", "assets/incoming/photos/a.jpg").put("eTag", "version-1").put("invocationId", "changes-on-retry");
            assertTrue(rocket.azureFileUploaded(runtime, "media", invocation));
            assertEquals(1, runtime.position());
            ((com.fasterxml.jackson.databind.node.ObjectNode) event.get("data")).put("url", "https://media.blob.core.windows.net/assets-other/incoming/photos/a.jpg");
            assertFalse(rocket.handleCloudEvent(runtime, event));
            ((com.fasterxml.jackson.databind.node.ObjectNode) event.get("data")).put("url", "https://media.blob.core.windows.net/assets/other/a.jpg");
            assertFalse(rocket.handleCloudEvent(runtime, event));
            ((com.fasterxml.jackson.databind.node.ObjectNode) event.get("data")).put("url", "http://media.blob.core.windows.net/assets/incoming/photos/a.jpg");
            assertThrows(IllegalArgumentException.class, () -> rocket.handleCloudEvent(runtime, event));
        }
    }

    @Test void blobEventsMatchConfiguredEndpointsAndExactContainersAcrossStores() {
        var archive = new StorageConfiguration("archive", "archive-assets", List.of("incoming"));
        var rocket = new FileUploadRocket(List.of(CONFIG, archive), Map.of("media", azure("media"), "archive", azure("archive")),
                AccessPolicy.deny(), AccessPolicy.publicAccess());
        try (var runtime = runtime(rocket, temp.resolve("multi-storage"))) {
            var event = Json.MAPPER.createObjectNode().put("eventType", "Microsoft.Storage.BlobCreated");
            var data = event.putObject("data").put("eTag", "same-etag");
            data.put("url", "https://media.blob.core.windows.net/assets/incoming/file.bin");
            assertTrue(rocket.handleCloudEvent(runtime, event));
            assertTrue(rocket.handleCloudEvent(runtime, event));
            data.put("url", "https://archive.blob.core.windows.net/archive-assets/incoming/file.bin");
            assertTrue(rocket.handleCloudEvent(runtime, event));
            data.put("url", "https://media.blob.core.windows.net/archive-assets/incoming/file.bin");
            assertFalse(rocket.handleCloudEvent(runtime, event));
            data.put("url", "https://unknown.blob.core.windows.net/assets/incoming/file.bin");
            assertFalse(rocket.handleCloudEvent(runtime, event));
            assertEquals(2, runtime.position());
        }
        var mismatched = new FileUploadRocket(List.of(CONFIG), Map.of("media", azure("other-account")), AccessPolicy.deny(), AccessPolicy.deny());
        try (var runtime = runtime(mismatched, temp.resolve("mismatched"))) {
            var event = Json.MAPPER.createObjectNode().put("eventType", "Microsoft.Storage.BlobCreated");
            event.putObject("data").put("eTag", "v1").put("url", "https://media.blob.core.windows.net/assets/incoming/file.bin");
            assertFalse(mismatched.handleCloudEvent(runtime, event)); assertEquals(0, runtime.position());
        }
    }
}
