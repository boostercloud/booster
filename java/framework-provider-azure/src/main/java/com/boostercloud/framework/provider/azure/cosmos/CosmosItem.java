package com.boostercloud.framework.provider.azure.cosmos;

import java.util.Map;

/** A document handle ({@code Item} of {@code @azure/cosmos}). */
public interface CosmosItem {
  /**
   * {@code item.read()}. Like the JavaScript SDK, a missing document is not an error: the response has status
   * {@code 404} and a {@code null} resource.
   *
   * @return the response
   */
  ItemResponse read();

  /**
   * {@code item.replace(body)}.
   *
   * @param body the new document
   * @return the response
   * @throws CosmosException on failure
   */
  ItemResponse replace(Map<String, Object> body);

  /**
   * {@code item.delete()}.
   *
   * @return the response
   * @throws CosmosException on failure ({@code 404} when the document does not exist)
   */
  ItemResponse delete();
}
