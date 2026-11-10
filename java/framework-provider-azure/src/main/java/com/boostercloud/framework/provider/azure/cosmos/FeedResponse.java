package com.boostercloud.framework.provider.azure.cosmos;

import java.util.List;
import java.util.Map;

/**
 * Query results ({@code FeedResponse} of {@code @azure/cosmos}).
 *
 * @param resources the raw results
 * @param continuationToken the token for the next page, or {@code null}
 * @param hasMoreResults whether more pages exist
 * @param headers the response headers of the last backend page (lower-case names)
 */
public record FeedResponse(
    List<Object> resources, String continuationToken, boolean hasMoreResults, Map<String, String> headers) {

  /** Canonical constructor; {@code null} collections become empty. */
  public FeedResponse {
    resources = resources == null ? List.of() : resources;
    headers = headers == null ? Map.of() : headers;
  }

  /**
   * @param resources the results
   * @return a final page with no continuation
   */
  public static FeedResponse of(List<Object> resources) {
    return new FeedResponse(resources, null, false, Map.of());
  }
}
