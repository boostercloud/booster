package com.boostercloud.framework.types.provider;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.SequenceKey;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ReadModelEnvelope;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import com.boostercloud.framework.types.envelope.SubscriptionEnvelope;
import java.util.List;
import java.util.Map;

/**
 * The read model store and the subscriptions store of a provider. Port of {@code ProviderReadModelsLibrary}.
 *
 * <p>Read models cross this interface as <b>raw documents</b> ({@code Map<String, Object>}, including the
 * {@code boosterMetadata} key); core turns them into instances.
 */
public interface ProviderReadModelsLibrary {
  /**
   * Converts the provider-native payload of a read-model change notification into envelopes.
   *
   * @param config the configuration
   * @param rawEvents the provider-native payload
   * @return the changed read models
   */
  List<ReadModelEnvelope> rawToEnvelopes(BoosterConfig config, Object rawEvents);

  /**
   * Fetches a read model by id. TypeScript types the result as a non-empty array but callers still check the first
   * element; an empty list means "not found".
   *
   * @param config the configuration
   * @param readModelName the read model class name
   * @param readModelID the id
   * @param sequenceKey the sequence key for sequenced read models, or {@code null}
   * @return the matching documents (several for a sequenced read model without sequence key)
   */
  List<Map<String, Object>> fetch(BoosterConfig config, String readModelName, UUID readModelID, SequenceKey sequenceKey);

  /**
   * Searches read models. TypeScript returns a bare array when {@code paginatedVersion} is false and
   * {@code { items, count, cursor }} otherwise; Java always returns a {@link ReadModelListResult} (count and cursor
   * may be absent when not paginated) and core unwraps {@code items} for non-paginated searches.
   *
   * @param config the configuration
   * @param readModelName the read model class name (TypeScript calls it {@code entityTypeName})
   * @param filters the filter ({@code FilterFor} JSON shape)
   * @param sortBy the sort ({@code SortFor} JSON shape, ordered), or {@code null}
   * @param limit maximum number of results, or {@code null}
   * @param afterCursor opaque cursor of the previous page, or {@code null}
   * @param paginatedVersion whether the paginated shape was requested, or {@code null}
   * @param select projection paths, or {@code null}
   * @return the results
   */
  ReadModelListResult<Map<String, Object>> search(
      BoosterConfig config,
      String readModelName,
      Map<String, Object> filters,
      Map<String, Object> sortBy,
      Integer limit,
      Object afterCursor,
      Boolean paginatedVersion,
      List<String> select);

  /**
   * Stores a read model. If {@code expectedCurrentVersion} is given and the stored document has a different
   * {@code boosterMetadata.version}, the provider must throw {@code OptimisticConcurrencyUnexpectedVersionError}.
   *
   * @param config the configuration
   * @param readModelName the read model class name
   * @param readModel the document to store
   * @param expectedCurrentVersion the version the caller read, or {@code null}
   */
  void store(BoosterConfig config, String readModelName, Map<String, Object> readModel, Long expectedCurrentVersion);

  /**
   * Deletes a read model; a {@code null} document is a no-op.
   *
   * @param config the configuration
   * @param readModelName the read model class name
   * @param readModel the document to delete, or {@code null}
   */
  void delete(BoosterConfig config, String readModelName, Map<String, Object> readModel);

  /**
   * @param config the configuration
   * @param subscriptionEnvelope the subscription to persist
   */
  void subscribe(BoosterConfig config, SubscriptionEnvelope subscriptionEnvelope);

  /**
   * @param config the configuration
   * @param subscriptionName the read model class name
   * @return every subscription to that read model
   */
  List<SubscriptionEnvelope> fetchSubscriptions(BoosterConfig config, String subscriptionName);

  /**
   * @param config the configuration
   * @param connectionID the websocket connection id
   * @param subscriptionID the subscription id ({@code operation.id})
   */
  void deleteSubscription(BoosterConfig config, String connectionID, String subscriptionID);

  /**
   * @param config the configuration
   * @param connectionID the websocket connection id
   */
  void deleteAllSubscriptions(BoosterConfig config, String connectionID);
}
