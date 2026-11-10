package com.boostercloud.rocket.fileuploads.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * The properties of a {@link ListItem} ({@code ListItemProperties} of {@code list-item.ts}). Dates are written as
 * ISO-8601 strings with milliseconds ({@code 2022-10-26T05:40:47.000Z}), like a JavaScript {@code Date}.
 *
 * @param createdOn creation time, or {@code null}
 * @param lastModified last modification time
 * @param contentLength size in bytes, or {@code null}
 * @param contentType content type, or {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListItemProperties(Instant createdOn, Instant lastModified, Long contentLength, String contentType) {

  /**
   * @param lastModified the last modification time
   * @return properties with only {@code lastModified} (the local provider's shape)
   */
  public static ListItemProperties lastModified(Instant lastModified) {
    return new ListItemProperties(null, lastModified, null, null);
  }
}
