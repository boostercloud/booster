package com.boostercloud.framework.core.integration.inmemory;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.UserAppRegistry;
import com.boostercloud.framework.types.concepts.SequenceKey;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ReadModelEnvelope;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import com.boostercloud.framework.types.envelope.SubscriptionEnvelope;
import com.boostercloud.framework.types.errors.OptimisticConcurrencyUnexpectedVersionError;
import com.boostercloud.framework.types.provider.ProviderReadModelsLibrary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The read model store of the in-memory provider: raw documents per read model (identified by {@code id} plus the
 * sequence key property, when the read model has one), optimistic concurrency on
 * {@code boosterMetadata.version} (a new document must be stored with expected version {@code 0}, an existing one
 * with its current version, otherwise {@link OptimisticConcurrencyUnexpectedVersionError}), searches with filters,
 * sorting, projections and the local provider's offset cursor ({@code {id: "<next offset>"}}), and the GraphQL
 * subscriptions. Like the local provider, every stored read model is handed to {@code boosterNotifySubscribers}
 * (when {@code config.enableSubscriptions}).
 */
public final class InMemoryReadModelStore implements ProviderReadModelsLibrary {

  private final Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<>();
  private final List<Map<String, Object>> subscriptions = new ArrayList<>();
  private final AtomicInteger concurrencyConflicts = new AtomicInteger();
  private int injectedConflicts;

  /**
   * The next {@code count} stores fail with an optimistic concurrency error (another writer won the race).
   *
   * @param count how many stores fail
   */
  public synchronized void failNextStores(int count) {
    injectedConflicts = count;
  }

  /**
   * @return how many stores were rejected because of the expected version (injected ones included)
   */
  public int concurrencyConflicts() {
    return concurrencyConflicts.get();
  }

  /**
   * @param readModelName a read model name
   * @return copies of its stored documents
   */
  public synchronized List<Map<String, Object>> documents(String readModelName) {
    return tables.getOrDefault(readModelName, List.of()).stream().map(InMemoryReadModelStore::copy).toList();
  }

  /**
   * Stores a document as it is, without version checks or notifications (tests use it to plant documents written by
   * an older version of the application).
   *
   * @param readModelName the read model name
   * @param document the raw document
   */
  public synchronized void plant(String readModelName, Map<String, Object> document) {
    tables.computeIfAbsent(readModelName, name -> new ArrayList<>()).add(copy(document));
  }

  /**
   * @return copies of the stored subscriptions, raw
   */
  public synchronized List<Map<String, Object>> subscriptions() {
    return subscriptions.stream().map(InMemoryReadModelStore::copy).toList();
  }

  // ---------------------------------------------------------------------------------------------------------------
  // ProviderReadModelsLibrary
  // ---------------------------------------------------------------------------------------------------------------

  @Override
  @SuppressWarnings("unchecked")
  public List<ReadModelEnvelope> rawToEnvelopes(BoosterConfig config, Object rawEvents) {
    return List.copyOf((List<ReadModelEnvelope>) rawEvents);
  }

  @Override
  public synchronized List<Map<String, Object>> fetch(BoosterConfig config, String readModelName, UUID readModelID,
      SequenceKey sequenceKey) {
    return tables.getOrDefault(readModelName, List.of()).stream()
        .filter(document -> readModelID.value().equals(String.valueOf(document.get("id"))))
        .filter(document -> sequenceKey == null || sequenceKey.value() == null
            || sequenceKey.value().value().equals(String.valueOf(document.get(sequenceKey.name()))))
        .map(InMemoryReadModelStore::copy)
        .toList();
  }

  @Override
  public synchronized ReadModelListResult<Map<String, Object>> search(BoosterConfig config, String readModelName,
      Map<String, Object> filters, Map<String, Object> sortBy, Integer limit, Object afterCursor,
      Boolean paginatedVersion, List<String> select) {
    int skip = afterCursor instanceof Map<?, ?> cursor && cursor.get("id") != null
        ? Integer.parseInt(String.valueOf(cursor.get("id")))
        : 0;
    List<Map<String, Object>> items = tables.getOrDefault(readModelName, List.of()).stream()
        .filter(document -> InMemoryQueries.matches(document, filters))
        .sorted(InMemoryQueries.comparator(sortBy))
        .skip(skip)
        .limit(limit == null ? Long.MAX_VALUE : limit)
        .map(document -> InMemoryQueries.project(copy(document), select))
        .toList();
    if (Boolean.TRUE.equals(paginatedVersion)) {
      return new ReadModelListResult<>(items, items.size(),
          Map.of("id", String.valueOf((limit != null && limit != 0 ? limit : 1) + skip)));
    }
    return ReadModelListResult.of(items);
  }

  @Override
  public void store(BoosterConfig config, String readModelName, Map<String, Object> readModel,
      Long expectedCurrentVersion) {
    Map<String, Object> document = copy(readModel);
    synchronized (this) {
      if (injectedConflicts > 0) {
        injectedConflicts--;
        concurrencyConflicts.incrementAndGet();
        throw new OptimisticConcurrencyUnexpectedVersionError("Injected conflict storing " + readModelName);
      }
      List<Map<String, Object>> table = tables.computeIfAbsent(readModelName, name -> new ArrayList<>());
      int index = indexOf(config, readModelName, table, document);
      long currentVersion = index < 0 ? 0 : version(table.get(index));
      long expected = expectedCurrentVersion == null ? 0 : expectedCurrentVersion;
      if (currentVersion != expected) {
        concurrencyConflicts.incrementAndGet();
        throw new OptimisticConcurrencyUnexpectedVersionError("Read model " + readModelName + " " + document.get("id")
            + " is at version " + currentVersion + ", expected " + expected);
      }
      if (index < 0) {
        table.add(document);
      } else {
        table.set(index, document);
      }
    }
    if (config.enableSubscriptions) {
      UserAppRegistry.get().boosterNotifySubscribers(List.of(new ReadModelEnvelope(readModelName, copy(document))));
    }
  }

  @Override
  public synchronized void delete(BoosterConfig config, String readModelName, Map<String, Object> readModel) {
    if (readModel == null) {
      return;
    }
    List<Map<String, Object>> table = tables.getOrDefault(readModelName, new ArrayList<>());
    int index = indexOf(config, readModelName, table, readModel);
    if (index >= 0) {
      table.remove(index);
    }
  }

  @Override
  public synchronized void subscribe(BoosterConfig config, SubscriptionEnvelope subscriptionEnvelope) {
    subscriptions.add(BoosterJson.toRawMap(subscriptionEnvelope));
  }

  @Override
  public synchronized List<SubscriptionEnvelope> fetchSubscriptions(BoosterConfig config, String subscriptionName) {
    return subscriptions.stream()
        .filter(subscription -> subscriptionName.equals(subscription.get("className")))
        .map(subscription -> BoosterJson.convert(copy(subscription), SubscriptionEnvelope.class))
        .toList();
  }

  @Override
  public synchronized void deleteSubscription(BoosterConfig config, String connectionID, String subscriptionID) {
    subscriptions.removeIf(subscription -> connectionID.equals(subscription.get("connectionID"))
        && subscription.get("operation") instanceof Map<?, ?> operation
        && subscriptionID.equals(operation.get("id")));
  }

  @Override
  public synchronized void deleteAllSubscriptions(BoosterConfig config, String connectionID) {
    subscriptions.removeIf(subscription -> connectionID.equals(subscription.get("connectionID")));
  }

  // ---------------------------------------------------------------------------------------------------------------

  private static int indexOf(BoosterConfig config, String readModelName, List<Map<String, Object>> table,
      Map<String, Object> document) {
    String sequenceKeyName = config.readModelSequenceKeys.get(readModelName);
    for (int i = 0; i < table.size(); i++) {
      Map<String, Object> candidate = table.get(i);
      if (Objects.equals(String.valueOf(candidate.get("id")), String.valueOf(document.get("id")))
          && (sequenceKeyName == null
              || Objects.equals(String.valueOf(candidate.get(sequenceKeyName)),
                  String.valueOf(document.get(sequenceKeyName))))) {
        return i;
      }
    }
    return -1;
  }

  private static long version(Map<String, Object> document) {
    return document.get("boosterMetadata") instanceof Map<?, ?> metadata
        && metadata.get("version") instanceof Number version ? version.longValue() : 0;
  }

  private static Map<String, Object> copy(Map<String, Object> document) {
    return new LinkedHashMap<>(BoosterJson.toRawMap(document));
  }
}
