package com.boostercloud.application.tester;

import java.util.List;
import java.util.Map;

/**
 * Raw reads of the documents a provider has stored for the application under test. Port of the TypeScript
 * {@code Queries} interface ({@code provider-test-helper.ts}); {@code Promise<Array<unknown>>} becomes a blocking
 * method returning raw documents ({@code Map<String, Object>} trees, see ARCHITECTURE §4).
 *
 * <p>The lists may contain {@code null} elements (the TypeScript Azure helper returns {@code [undefined]} for a read
 * model that does not exist), so implementations should return mutable or {@code null}-tolerant lists.
 */
public interface Queries {

  /**
   * @param primaryKey the provider-specific primary key of the event stream, e.g. {@code "Cart-<id>-event"} or
   *     {@code "defaultTopic-<id>-event"}
   * @return the raw event-store documents with that key (newest first on Azure)
   */
  List<Map<String, Object>> events(String primaryKey);

  /**
   * @param primaryKey the read model id
   * @param readModelName the read model name (its simple class name)
   * @return the raw read-model documents with that key
   */
  List<Map<String, Object>> readModels(String primaryKey, String readModelName);
}
