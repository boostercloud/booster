package io.boostercloud.booster.rockets.uploads;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.net.URI;

/** Rocket wiring shared by the local provider and Azure trigger dispatch. */
public final class FileUploadRocket {
    private final List<StorageConfiguration> configurations;
    private final Map<String, FileStorage> providers;
    private final AccessPolicy uploadPolicy;
    private final AccessPolicy readPolicy;

    public FileUploadRocket(List<StorageConfiguration> configurations, Map<String, FileStorage> providers,
                            AccessPolicy uploadPolicy, AccessPolicy readPolicy) {
        this.configurations = List.copyOf(configurations);
        this.providers = Map.copyOf(providers);
        this.uploadPolicy = Objects.requireNonNull(uploadPolicy); this.readPolicy = Objects.requireNonNull(readPolicy);
        if (configurations.isEmpty() || configurations.stream().map(StorageConfiguration::storageName).distinct().count() != configurations.size())
            throw new IllegalArgumentException("Unique nonempty storage configurations required");
        configurations.forEach(c -> Objects.requireNonNull(providers.get(c.storageName()), "Missing file provider"));
    }

    public Booster.Builder install(Booster.Builder builder) {
        return builder.event(UploadedFileEvent.class, UploadedFileEvent::entityID, UploadedFileEntity.class,
                (event, previous) -> new UploadedFileEntity(event.entityID(), event.metadata()));
    }

    public FileHandler handler(String storageName, RequestContext context) {
        return new FileHandler(configurations, providers, storageName, context, uploadPolicy, readPolicy);
    }

    /** Returns false for a trigger outside configured storage/container/directory selections. */
    public boolean fileUploaded(Booster runtime, UploadNotification notification) {
        boolean selected = configurations.stream().anyMatch(c -> c.storageName().equalsIgnoreCase(notification.storageName())
                && c.containerName().equals(notification.containerName()) && c.allows(FilePaths.parent(notification.key())));
        if (!selected) return false;
        // Delivery metadata may vary on retries; the immutable storage version is the identity.
        var input = Json.MAPPER.valueToTree(List.of(notification.storageName().toLowerCase(java.util.Locale.ROOT),
                notification.containerName(), notification.key(), notification.version()));
        runtime.executeAction("rocket.files.uploaded", input,
                new RequestContext(notification.idempotencyKey(), UserContext.anonymous(), Map.of("transport", "file-upload")),
                AccessPolicy.publicAccess(), (ignored, register) -> {
                    var event = new UploadedFileEvent(notification.metadata());
                    register.emit(event);
                    return Map.of("entityID", event.entityID());
                });
        return true;
    }

    /** Adapter for the baseline Azure Functions v4 trigger context shape. */
    public boolean azureFileUploaded(Booster runtime, String storageName, JsonNode invocation) {
        JsonNode metadata = invocation.path("context").path("triggerMetadata");
        if (!metadata.isObject() || !metadata.path("blobTrigger").isTextual())
            throw new IllegalArgumentException("InvocationContext.triggerMetadata is missing");
        String path = metadata.get("blobTrigger").asText();
        int separator = path.indexOf('/');
        if (separator < 1) throw new IllegalArgumentException("Invalid blobTrigger");
        String etag = metadata.path("eTag").asText(metadata.path("ETag").asText(metadata.path("etag").asText()));
        if (etag.isBlank()) throw new IllegalArgumentException("Blob trigger requires a stable ETag");
        // Invocation IDs change on retries; use the storage version and strip delivery-only metadata.
        var stable = metadata.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) stable).remove(List.of("invocationId", "invocationID"));
        return fileUploaded(runtime, new UploadNotification(storageName, path.substring(0, separator),
                path.substring(separator + 1), etag, stable));
    }

    /** Handles Event Grid and CloudEvents BlobCreated payloads after the HTTP adapter authenticates the sender. */
    public boolean handleCloudEvent(Booster runtime, JsonNode event) {
        String type = event.path("eventType").asText(event.path("type").asText());
        if (!type.equals("Microsoft.Storage.BlobCreated")) return false;
        JsonNode data = event.path("data");
        if (!data.isObject() || !data.path("url").isTextual()) throw new IllegalArgumentException("BlobCreated URL missing");
        URI url = URI.create(data.get("url").asText());
        if (!"https".equals(url.getScheme()) || url.getHost() == null || !url.getHost().endsWith(".blob.core.windows.net")
                || url.getRawUserInfo() != null || url.getPort() != -1 || url.getFragment() != null)
            throw new IllegalArgumentException("Invalid BlobCreated URL");
        String account = url.getHost().substring(0, url.getHost().length() - ".blob.core.windows.net".length());
        FileStorage provider = providers.get(account);
        if (provider == null || !provider.acceptsBlobEndpoint(url)) return false;
        String[] path = url.getPath().split("/", 3);
        if (path.length != 3 || path[1].isEmpty()) throw new IllegalArgumentException("BlobCreated path missing");
        String etag = data.path("eTag").asText();
        var metadata = Json.MAPPER.createObjectNode().put("blobTrigger", path[1] + "/" + path[2])
                .put("storageName", account).put("eTag", etag);
        if (data.has("contentType")) metadata.set("contentType", data.get("contentType"));
        if (data.has("contentLength")) metadata.set("contentLength", data.get("contentLength"));
        return fileUploaded(runtime, new UploadNotification(account, path[1], path[2], etag, metadata));
    }
}
