package com.boostercloud.framework.types;

import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventDeleteParameters;
import com.boostercloud.framework.types.envelope.EventSearchParameters;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import com.boostercloud.framework.types.envelope.PaginatedEntitiesIdsResult;
import com.boostercloud.framework.types.searcher.Searcher;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The user-facing Booster facade. Port of {@code BoosterApp} (a TypeScript declaration that nothing implements: the
 * core {@code Booster} class has static methods). Core may expose an instance implementing it, for code that prefers
 * an injectable facade over the static {@code Booster} class.
 */
public interface BoosterApp {
  /**
   * Starts the application whose classes are in the package of {@code applicationClass}.
   *
   * @param applicationClass a class of the application (typically the main class)
   */
  void start(Class<?> applicationClass);

  /**
   * @return the configuration
   */
  BoosterConfig config();

  /**
   * Configures an environment; the configurator runs only when it is the current environment.
   *
   * @param environment the environment name
   * @param configurator the configurator
   */
  void configure(String environment, Consumer<BoosterConfig> configurator);

  /**
   * @param configurator configurator run on the current configuration
   */
  void configureCurrentEnv(Consumer<BoosterConfig> configurator);

  /**
   * Reduces an entity from its events.
   *
   * @param entityClass the entity class
   * @param entityID the entity id
   * @param <T> the entity type
   * @return the entity, if it exists
   */
  <T> Optional<T> entity(Class<T> entityClass, UUID entityID);

  /**
   * @param readModelClass the read model class
   * @param <T> the read model type
   * @return a searcher for it
   */
  <T> Searcher<T> readModel(Class<T> readModelClass);

  /**
   * @param request what to search
   * @return the matching events
   */
  List<EventSearchResponse> events(EventSearchParameters request);

  /**
   * @param entityTypeName the entity class name
   * @param limit the page size
   * @param afterCursor the cursor of the previous page, or {@code null}
   * @return a page of entity ids
   */
  PaginatedEntitiesIdsResult entitiesIDs(String entityTypeName, int limit, Map<String, String> afterCursor);

  /**
   * @param parameters the event to delete
   * @return whether something was deleted
   */
  boolean deleteEvent(EventDeleteParameters parameters);

  /**
   * @return the environments named in {@link #configure(String, Consumer)}
   */
  Set<String> configuredEnvironments();
}
