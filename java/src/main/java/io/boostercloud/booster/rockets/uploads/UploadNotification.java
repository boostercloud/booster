package io.boostercloud.booster.rockets.uploads;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.Json;
import java.util.Objects;

/** A stable blob version is mandatory: redelivery must identify the same upload. */
public record UploadNotification(String storageName, String containerName, String key, String version, JsonNode metadata) {
    public UploadNotification {
        storageName = Objects.requireNonNull(storageName).toLowerCase(java.util.Locale.ROOT);
        Objects.requireNonNull(containerName);
        key = FilePaths.key(key);
        if (version == null || version.isBlank() || version.length() > 512) throw new IllegalArgumentException("Upload version required");
        metadata = Objects.requireNonNull(metadata).deepCopy();
        if (!metadata.isObject()) throw new IllegalArgumentException("Upload metadata must be an object");
    }
    @Override public JsonNode metadata() { return metadata.deepCopy(); }
    public String idempotencyKey() {
        return "uploads:" + FilePaths.sha256(Json.MAPPER.valueToTree(java.util.List.of(storageName, containerName, key, version)).toString());
    }
}
