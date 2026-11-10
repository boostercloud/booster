package com.boostercloud.framework.core;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.SequenceKey;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code booster-read-models-reader.ts}: the read side of read models (validation, authorization,
 * {@code before} hooks, {@code findById}, {@code search} with {@code select} projections and calculated-field
 * dependencies, subscriptions). {@link #readModelSearch} and {@link #finderByIdFunction} back the user-facing
 * {@code Searcher} ({@code Booster.readModel(...)}): their signatures match {@code SearcherFunction} and
 * {@code FinderByKeyFunction} and must be kept.
 *
 * <p>Skeleton: implemented by stage <b>core-2b</b> (spec 07 §3.2); trace actions {@code READ_MODEL_FIND_BY_ID},
 * {@code GRAPHQL_READ_MODEL_SEARCH}, {@code READ_MODEL_SEARCH}.
 */
public class BoosterReadModelsReader {

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public BoosterReadModelsReader(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * {@code findById(readModelRequest)}. Implemented by stage core-2b.
   *
   * @param readModelRequest the request
   * @return the read model, or the list of read models for sequenced read models
   */
  public Object findById(ReadModelRequestEnvelope readModelRequest) {
    throw new UnsupportedOperationException("not implemented yet: BoosterReadModelsReader#findById");
  }

  /**
   * {@code search(readModelRequest)}. Implemented by stage core-2b.
   *
   * @param readModelRequest the request
   * @return a {@code List} or a {@code ReadModelListResult} (paginated version)
   */
  public Object search(ReadModelRequestEnvelope readModelRequest) {
    throw new UnsupportedOperationException("not implemented yet: BoosterReadModelsReader#search");
  }

  /**
   * {@code readModelSearch(readModelClass, filters, sort, limit, afterCursor, paginatedVersion, select)}.
   * Implemented by stage core-2b.
   *
   * @param readModelClass the read model class
   * @param filters the filters
   * @param sortBy the sort, or {@code null}
   * @param limit the limit, or {@code null}
   * @param afterCursor the cursor, or {@code null}
   * @param paginatedVersion whether a {@code ReadModelListResult} is returned
   * @param select the selected paths, or {@code null}
   * @param <T> the read model type
   * @return a {@code List<T>} or a {@code ReadModelListResult<T>}
   */
  public <T> Object readModelSearch(Class<T> readModelClass, Map<String, Object> filters, Map<String, Object> sortBy,
      Integer limit, Object afterCursor, boolean paginatedVersion, List<String> select) {
    throw new UnsupportedOperationException("not implemented yet: BoosterReadModelsReader#readModelSearch");
  }

  /**
   * {@code finderByIdFunction(readModelClass, id, sequenceKey?)}. Implemented by stage core-2b.
   *
   * @param readModelClass the read model class
   * @param id the id
   * @param sequenceKey the sequence key, or {@code null}
   * @param <T> the read model type
   * @return a {@code T} or a {@code List<T>}
   */
  public <T> Object finderByIdFunction(Class<T> readModelClass, UUID id, SequenceKey sequenceKey) {
    throw new UnsupportedOperationException("not implemented yet: BoosterReadModelsReader#finderByIdFunction");
  }

  /**
   * {@code subscribe(connectionID, readModelRequest, operation)}. Implemented by stage core-2b.
   *
   * @param connectionID the websocket connection
   * @param readModelRequest the request
   * @param operation the GraphQL operation
   * @return the provider result
   */
  public Object subscribe(String connectionID, ReadModelRequestEnvelope readModelRequest, GraphQLOperation operation) {
    throw new UnsupportedOperationException("not implemented yet: BoosterReadModelsReader#subscribe");
  }

  /**
   * {@code unsubscribe(connectionID, subscriptionID)}. Implemented by stage core-2b.
   *
   * @param connectionID the websocket connection
   * @param subscriptionID the subscription
   */
  public void unsubscribe(String connectionID, String subscriptionID) {
    throw new UnsupportedOperationException("not implemented yet: BoosterReadModelsReader#unsubscribe");
  }

  /**
   * {@code unsubscribeAll(connectionID)}. Implemented by stage core-2b.
   *
   * @param connectionID the websocket connection
   */
  public void unsubscribeAll(String connectionID) {
    throw new UnsupportedOperationException("not implemented yet: BoosterReadModelsReader#unsubscribeAll");
  }
}
