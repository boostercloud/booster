package io.boostercloud.booster.rockets.uploads;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/** Metadata-derived entity ID preserves the original rocket's UploadedFileEvent contract. */
public record UploadedFileEvent(JsonNode metadata) {
    public UploadedFileEvent { metadata = Objects.requireNonNull(metadata).deepCopy(); }
    @Override public JsonNode metadata() { return metadata.deepCopy(); }
    public String entityID() {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(metadata.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
}
