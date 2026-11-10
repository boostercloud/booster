package com.boostercloud.rocket.fileuploads.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One file returned by {@code list} ({@code ListItem} of {@code list-item.ts}). Absent members are omitted from JSON
 * (the local provider only sets {@code name} and {@code properties.lastModified}).
 *
 * @param name the file name including its directory ({@code client1/myfile.txt})
 * @param properties the file properties
 * @param metadata the user-defined blob metadata (Azure), or {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListItem(String name, ListItemProperties properties, Map<String, String> metadata) {

  /** Canonical constructor: keeps an unmodifiable, ordered copy of the metadata. */
  public ListItem {
    metadata = metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
  }

  /**
   * @param name the file name
   * @param properties the properties
   * @return an item without metadata
   */
  public static ListItem of(String name, ListItemProperties properties) {
    return new ListItem(name, properties, null);
  }
}
