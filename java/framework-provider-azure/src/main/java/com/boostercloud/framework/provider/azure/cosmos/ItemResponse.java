package com.boostercloud.framework.provider.azure.cosmos;

import java.util.Map;

/**
 * The response of a point operation ({@code ItemResponse} of {@code @azure/cosmos}).
 *
 * @param statusCode the HTTP status code
 * @param resource the returned document, or {@code null} (deletes, reads of missing documents)
 * @param etag the {@code etag} response header, or {@code null}
 * @param headers the response headers (lower-case names)
 */
public record ItemResponse(int statusCode, Map<String, Object> resource, String etag, Map<String, String> headers) {

  /** Canonical constructor; {@code null} headers become empty. */
  public ItemResponse {
    headers = headers == null ? Map.of() : headers;
  }

  /**
   * @param statusCode the status
   * @param resource the document
   * @return a response without headers
   */
  public static ItemResponse of(int statusCode, Map<String, Object> resource) {
    return new ItemResponse(statusCode, resource, null, Map.of());
  }
}
