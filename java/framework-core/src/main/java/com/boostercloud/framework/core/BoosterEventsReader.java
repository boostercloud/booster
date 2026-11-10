package com.boostercloud.framework.core;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.EntityMetadata;
import com.boostercloud.framework.types.concepts.EventStreamAuthorizer;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventParametersFilterByEntity;
import com.boostercloud.framework.types.envelope.EventParametersFilterByType;
import com.boostercloud.framework.types.envelope.EventSearchParameters;
import com.boostercloud.framework.types.envelope.EventSearchRequest;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.framework.types.errors.NotFoundError;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code booster-events-reader.ts}: validates and authorizes an event search request (GraphQL
 * {@code eventsByEntity}/{@code eventsByType}) and runs it.
 *
 * <p>Events are authorized <b>per entity</b>: a by-entity search uses the {@code eventStreamAuthorizer} of that
 * entity ({@code @Entity(authorizeReadEvents = ...)}); a by-type search uses the one of the entity whose reducer
 * handles the event type ({@code config.reducers}), so types without a reducer (notifications,
 * {@code BoosterEntityTouched}, {@code BoosterEntityMigrated}) cannot be searched by type. The authorizer denies by
 * throwing ({@code NotAuthorizedError: Access denied for this resource} for roles). The search itself is
 * {@link Booster#events} (the <b>global</b> configuration, as in TypeScript; the validation uses this reader's).
 *
 * <p>Errors (exact TypeScript messages): {@link InvalidParameterError} {@code Invalid event search request. It should
 * contain either "type" or "entity" field, but it included none} / {@code ... but it included both};
 * {@link NotFoundError} {@code Could not find entity metadata for "<Entity>"} and
 * {@code Could not find the entity associated to event type "<Event>"}.
 */
public class BoosterEventsReader {

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public BoosterEventsReader(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * {@code fetch(eventRequest)}: validates and authorizes the request, then searches.
   *
   * <p>The typed {@link EventSearchParameters} cannot hold both an {@code entity} and a {@code type}; {@code null}
   * parameters are the "none" case. To validate parameters as received (for example GraphQL arguments, where the
   * presence of the keys decides the kind of search), use {@link #fetch(UUID, UserEnvelope, Map)}.
   *
   * @param eventRequest the request
   * @return the events, values bound to their classes
   * @throws InvalidParameterError when the request has no parameters
   * @throws NotFoundError when the entity, or the entity of the event type, is unknown
   * @throws RuntimeException whatever the authorizer throws to deny access
   */
  public List<EventSearchResponse> fetch(EventSearchRequest eventRequest) {
    validateRequest(eventRequest);
    return processFetch(eventRequest);
  }

  /**
   * {@code fetch({requestID, currentUser, parameters})} with the parameters as a raw map (TypeScript passes the
   * GraphQL arguments through untouched): whether it is a by-entity or a by-type search is decided by the presence
   * of the {@code entity}/{@code type} keys, as TypeScript's {@code 'entity' in parameters}. A map with neither or
   * both keys is rejected with the {@link InvalidParameterError}s of TypeScript; otherwise the parameters are
   * converted with {@link EventSearchParameters#fromMap(Map)} and {@link #fetch(EventSearchRequest)} runs.
   *
   * @param requestID the request id
   * @param currentUser the user, or {@code null}
   * @param parameters the raw search parameters ({@code entity}, {@code entityID}, {@code type}, {@code from},
   *     {@code to}, {@code limit})
   * @return the events
   */
  public List<EventSearchResponse> fetch(UUID requestID, UserEnvelope currentUser, Map<String, ?> parameters) {
    switch (EventSearchParameters.kindOf(parameters)) {
      case NONE -> throw noneError();
      case BOTH -> throw bothError();
      default -> {
        // a by-entity or by-type search
      }
    }
    return fetch(EventSearchRequest.builder()
        .requestID(requestID)
        .currentUser(currentUser)
        .parameters(EventSearchParameters.fromMap(parameters))
        .build());
  }

  private void validateRequest(EventSearchRequest eventRequest) {
    Logger logger = Loggers.getLogger(config, "BoosterEventsReader#validateRequest");
    logger.debug("Validating the following event request: ", eventRequest);
    EntityMetadata entityMetadata = entityMetadataFromRequest(eventRequest);
    EventStreamAuthorizer authorizer = entityMetadata.eventStreamAuthorizer();
    if (authorizer == null) {
      // An entity registered without authorizer cannot be read (the decorators always set one; null = deny)
      throw new NotAuthorizedError(BoosterAuthorizer.ACCESS_DENIED_MESSAGE);
    }
    authorizer.authorize(eventRequest.currentUser(), eventRequest);
  }

  private EntityMetadata entityMetadataFromRequest(EventSearchRequest eventRequest) {
    return switch (eventRequest.parameters()) {
      case null -> throw noneError();
      case EventParametersFilterByEntity byEntity -> entityMetadataFromEntityName(byEntity.entity());
      case EventParametersFilterByType byType -> entityMetadataFromEventName(byType.type());
    };
  }

  private EntityMetadata entityMetadataFromEntityName(String entityName) {
    EntityMetadata entityMetadata = config.entities.get(entityName);
    if (entityMetadata == null) {
      throw new NotFoundError("Could not find entity metadata for \"" + entityName + "\"");
    }
    return entityMetadata;
  }

  private EntityMetadata entityMetadataFromEventName(String eventName) {
    // All the events must be reduced by an entity, so we can get the associated entity from the reducers
    ReducerMetadata reducerMetadata = config.reducers.get(eventName);
    if (reducerMetadata == null) {
      throw new NotFoundError("Could not find the entity associated to event type \"" + eventName + "\"");
    }
    return entityMetadataFromEntityName(reducerMetadata.type().getSimpleName());
  }

  private List<EventSearchResponse> processFetch(EventSearchRequest eventRequest) {
    return Booster.events(eventRequest.parameters());
  }

  private static InvalidParameterError noneError() {
    return new InvalidParameterError(
        "Invalid event search request. It should contain either \"type\" or \"entity\" field, but it included none");
  }

  private static InvalidParameterError bothError() {
    return new InvalidParameterError(
        "Invalid event search request. It should contain either \"type\" or \"entity\" field, but it included both");
  }
}
