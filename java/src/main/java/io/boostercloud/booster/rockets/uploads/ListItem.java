package io.boostercloud.booster.rockets.uploads;

import java.time.Instant;
import java.util.Map;

public record ListItem(String name, Properties properties, Map<String, String> metadata) {
    public ListItem { metadata = metadata == null ? Map.of() : Map.copyOf(metadata); }
    public record Properties(Instant createdOn, Instant lastModified, Long contentLength, String contentType) {}
}
