package io.boostercloud.booster.rockets.uploads;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

public record UploadedFileEntity(String id, JsonNode metadata) {
    public UploadedFileEntity { Objects.requireNonNull(id); metadata = Objects.requireNonNull(metadata).deepCopy(); }
    @Override public JsonNode metadata() { return metadata.deepCopy(); }
}
