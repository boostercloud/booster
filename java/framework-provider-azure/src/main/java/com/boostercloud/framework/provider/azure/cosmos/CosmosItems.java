package com.boostercloud.framework.provider.azure.cosmos;

import java.util.List;
import java.util.Map;

/** Container-level item operations ({@code container.items} of {@code @azure/cosmos}). */
public interface CosmosItems {
  /**
   * {@code items.query(querySpec)}: a cross-partition query.
   *
   * @param querySpec the SQL and its parameters
   * @return a lazy iterator (no request is made until {@code fetchAll}/{@code fetchNext})
   */
  default CosmosQueryIterator query(SqlQuerySpec querySpec) {
    return query(querySpec, FeedOptions.none());
  }

  /**
   * {@code items.query(querySpec, feedOptions)}.
   *
   * @param querySpec the SQL and its parameters
   * @param options continuation token, page size and optional partition key
   * @return a lazy iterator
   */
  CosmosQueryIterator query(SqlQuerySpec querySpec, FeedOptions options);

  /**
   * {@code items.create(body)}: inserts a document. Like the JavaScript SDK, an {@code id} is generated (random UUID)
   * when the body has none, and the partition key value is read from the body using the container's partition key
   * definition.
   *
   * @param body the document (a raw map; it is not modified)
   * @return the response, with the created document
   * @throws CosmosException on failure ({@code 409} on conflicts)
   */
  ItemResponse create(Map<String, Object> body);

  /**
   * {@code items.upsert(body, options)}.
   *
   * @param body the document
   * @param options request options ({@code accessCondition} for If-Match)
   * @return the response
   * @throws CosmosException on failure ({@code 412} when the If-Match condition fails)
   */
  ItemResponse upsert(Map<String, Object> body, RequestOptions options);

  /**
   * {@code items.batch(operations, partitionKey, options)}: a transactional batch on one logical partition. A
   * failed batch is returned (not thrown) with a status code different from 200, like the JavaScript SDK.
   *
   * @param operations the operations (at most 100)
   * @param partitionKey the partition key value shared by every operation
   * @param options request options
   * @return the batch response
   * @throws CosmosException when the request itself is rejected (bad request, throttling exhausted...)
   */
  BatchResponse batch(List<OperationInput> operations, Object partitionKey, RequestOptions options);
}
