package com.boostercloud.framework.provider.local;

import com.boostercloud.framework.provider.local.library.ApiAdapter;
import com.boostercloud.framework.provider.local.library.ConnectionsAdapter;
import com.boostercloud.framework.provider.local.library.EventDeleteAdapter;
import com.boostercloud.framework.provider.local.library.EventsAdapter;
import com.boostercloud.framework.provider.local.library.EventsSearchAdapter;
import com.boostercloud.framework.provider.local.library.GraphQLAdapter;
import com.boostercloud.framework.provider.local.library.HealthAdapter;
import com.boostercloud.framework.provider.local.library.ReadModelAdapter;
import com.boostercloud.framework.provider.local.library.RocketAdapter;
import com.boostercloud.framework.provider.local.library.ScheduledAdapter;
import com.boostercloud.framework.provider.local.library.SubscriptionAdapter;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.SequenceKey;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ConnectionDataEnvelope;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventDeleteParameters;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.EventSearchParameters;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelopeOrError;
import com.boostercloud.framework.types.envelope.HealthEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.PaginatedEntitiesIdsResult;
import com.boostercloud.framework.types.envelope.ReadModelEnvelope;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import com.boostercloud.framework.types.envelope.ScheduledCommandEnvelope;
import com.boostercloud.framework.types.envelope.SnapshotDeleteParameters;
import com.boostercloud.framework.types.envelope.SubscriptionEnvelope;
import com.boostercloud.framework.types.provider.HasInfrastructure;
import com.boostercloud.framework.types.provider.ProviderAPIHandling;
import com.boostercloud.framework.types.provider.ProviderConnectionsLibrary;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import com.boostercloud.framework.types.provider.ProviderGraphQLLibrary;
import com.boostercloud.framework.types.provider.ProviderInfrastructure;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.provider.ProviderReadModelsLibrary;
import com.boostercloud.framework.types.provider.ProviderRocketLibrary;
import com.boostercloud.framework.types.provider.ProviderSensorLibrary;
import com.boostercloud.framework.types.provider.ScheduledCommandsLibrary;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import com.boostercloud.framework.types.rockets.RocketEnvelope;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The {@link ProviderLibrary} of the local provider: the object {@code Provider(rocketDescriptors)} returns in
 * TypeScript, wiring every SPI method to the adapters of the {@code library} package over a
 * {@link LocalProviderRuntime}.
 *
 * <p>Differences from the TypeScript wiring: {@code events.store} records the batch in the dispatch journal and
 * returns the events with their database {@code id}, and {@code events.storeDispatched} answers {@code false} for an
 * event core already started to process (it is {@code true} otherwise, as in TypeScript); {@code readModels.fetch}
 * returns an empty list when the read model does not exist; {@code rawStreamToEnvelopes} and
 * {@code dedupEventStream} return empty lists and {@code produce} does nothing (TypeScript's {@code notImplemented}
 * stubs: the local provider has no event stream).
 */
public final class LocalProvider implements ProviderLibrary {

  private final LocalProviderRuntime runtime;
  private final List<RocketDescriptor> rocketDescriptors;
  private final Function<String, HasInfrastructure> infrastructureLoader;
  private final ProviderEventsLibrary events = new Events();
  private final ProviderReadModelsLibrary readModels = new ReadModels();
  private final ProviderGraphQLLibrary graphQL = new GraphQL();
  private final ProviderAPIHandling api = new Api();
  private final ProviderConnectionsLibrary connections = new Connections();
  private final ScheduledCommandsLibrary scheduled = new Scheduled();
  private final ProviderRocketLibrary rockets = new Rockets();
  private final ProviderSensorLibrary sensor = new Sensor();

  /**
   * Creates the wiring and starts the runtime's websocket server (TypeScript starts it in {@code Provider(...)}).
   *
   * @param runtime the runtime
   * @param rocketDescriptors the rockets, passed to the infrastructure
   * @param infrastructureLoader loads the infrastructure package by name ({@link Provider#loadInfrastructurePackage})
   */
  public LocalProvider(LocalProviderRuntime runtime, List<RocketDescriptor> rocketDescriptors,
      Function<String, HasInfrastructure> infrastructureLoader) {
    this.runtime = Objects.requireNonNull(runtime, "runtime");
    this.rocketDescriptors = rocketDescriptors == null ? List.of() : List.copyOf(rocketDescriptors);
    this.infrastructureLoader = Objects.requireNonNull(infrastructureLoader, "infrastructureLoader");
    runtime.webSocketServerAdapter();
  }

  /**
   * @return the runtime
   */
  public LocalProviderRuntime runtime() {
    return runtime;
  }

  /**
   * Java-only: re-dispatches the events stored before a crash whose dispatch never started. The local infrastructure
   * calls it when the application starts.
   *
   * @param config the configuration
   * @return the re-dispatched events
   */
  public List<EventEnvelope> recoverUndispatchedEvents(BoosterConfig config) {
    return runtime.recoverUndispatchedEvents(config);
  }

  @Override
  public ProviderEventsLibrary events() {
    return events;
  }

  @Override
  public ProviderReadModelsLibrary readModels() {
    return readModels;
  }

  @Override
  public ProviderGraphQLLibrary graphQL() {
    return graphQL;
  }

  @Override
  public ProviderAPIHandling api() {
    return api;
  }

  @Override
  public ProviderConnectionsLibrary connections() {
    return connections;
  }

  @Override
  public ScheduledCommandsLibrary scheduled() {
    return scheduled;
  }

  /**
   * Loads {@code com.boostercloud.framework.provider.local.infrastructure.Infrastructure} and builds it with the
   * rockets.
   *
   * @return the infrastructure
   * @throws IllegalStateException {@code The Local infrastructure package could not be loaded. ...} when the module
   *     is missing
   */
  @Override
  public ProviderInfrastructure infrastructure() {
    String infrastructurePackageName = Provider.INFRASTRUCTURE_PACKAGE_NAME;
    HasInfrastructure infrastructure;
    try {
      infrastructure = infrastructureLoader.apply(infrastructurePackageName);
    } catch (RuntimeException e) {
      throw new IllegalStateException("The Local infrastructure package could not be loaded. The following error was "
          + "thrown: " + e.getMessage() + ". Please ensure that one of the following actions has been done:\n"
          + "  - It has been added to the dependencies of your project. You can do so by adding the Maven dependency '"
          + Provider.INFRASTRUCTURE_ARTIFACT + "'\n"
          + "  - Or its jar is on the classpath of the application (package '" + infrastructurePackageName + "')", e);
    }
    return infrastructure.infrastructure(rocketDescriptors);
  }

  @Override
  public ProviderRocketLibrary rockets() {
    return rockets;
  }

  @Override
  public ProviderSensorLibrary sensor() {
    return sensor;
  }

  private final class Events implements ProviderEventsLibrary {
    @Override
    public List<EventEnvelope> rawToEnvelopes(Object rawEvents) {
      return EventsAdapter.rawEventsToEnvelopes(rawEvents);
    }

    @Override
    public List<EventEnvelope> rawStreamToEnvelopes(BoosterConfig config, Object context,
        List<Object> dedupEventStream) {
      return List.of();
    }

    @Override
    public List<Object> dedupEventStream(BoosterConfig config, Object rawEvents) {
      return List.of();
    }

    @Override
    public void produce(String entityName, UUID entityID, List<EventEnvelope> eventEnvelopes, BoosterConfig config) {
      // the local provider has no event stream (TypeScript: notImplemented)
    }

    @Override
    public List<EventEnvelope> forEntitySince(BoosterConfig config, String entityTypeName, UUID entityID,
        String since) {
      return EventsAdapter.readEntityEventsSince(runtime.eventRegistry(), config, entityTypeName, entityID, since);
    }

    @Override
    public Optional<EntitySnapshotEnvelope> latestEntitySnapshot(BoosterConfig config, String entityTypeName,
        UUID entityID) {
      return EventsAdapter.readEntityLatestSnapshot(runtime.eventRegistry(), config, entityTypeName, entityID);
    }

    @Override
    public List<EventSearchResponse> search(BoosterConfig config, EventSearchParameters parameters) {
      return EventsSearchAdapter.searchEvents(runtime.eventRegistry(), config, parameters);
    }

    @Override
    public PaginatedEntitiesIdsResult searchEntitiesIDs(BoosterConfig config, int limit,
        Map<String, String> afterCursor, String entityTypeName) {
      return EventsSearchAdapter.searchEntitiesIds(runtime.eventRegistry(), config, limit, afterCursor,
          entityTypeName);
    }

    @Override
    public List<EventEnvelope> store(List<NonPersistedEventEnvelope> eventEnvelopes, BoosterConfig config) {
      return EventsAdapter.storeEvents(runtime.userApp(), runtime.eventRegistry(), runtime.dispatchJournal(),
          eventEnvelopes, config);
    }

    @Override
    public EntitySnapshotEnvelope storeSnapshot(NonPersistedEntitySnapshotEnvelope snapshotEnvelope,
        BoosterConfig config) {
      return EventsAdapter.storeSnapshot(runtime.eventRegistry(), snapshotEnvelope, config);
    }

    @Override
    public boolean storeDispatched(EventEnvelope eventEnvelope, BoosterConfig config) {
      return EventsAdapter.storeDispatchedEvent(runtime.dispatchJournal(), eventEnvelope, config);
    }

    @Override
    public List<EventEnvelope> findDeletableEvent(BoosterConfig config, EventDeleteParameters parameters) {
      return EventDeleteAdapter.findDeletableEvent(runtime.eventRegistry(), config, parameters);
    }

    @Override
    public List<EntitySnapshotEnvelope> findDeletableSnapshot(BoosterConfig config,
        SnapshotDeleteParameters parameters) {
      return EventDeleteAdapter.findDeletableSnapshot(runtime.eventRegistry(), config, parameters);
    }

    @Override
    public void deleteEvent(BoosterConfig config, List<EventEnvelope> eventEnvelopes) {
      EventDeleteAdapter.deleteEvent(runtime.eventRegistry(), config, eventEnvelopes);
    }

    @Override
    public void deleteSnapshot(BoosterConfig config, List<EntitySnapshotEnvelope> snapshots) {
      EventDeleteAdapter.deleteSnapshot(runtime.eventRegistry(), config, snapshots);
    }
  }

  private final class ReadModels implements ProviderReadModelsLibrary {
    @Override
    public List<ReadModelEnvelope> rawToEnvelopes(BoosterConfig config, Object rawEvents) {
      return ReadModelAdapter.rawReadModelEventsToEnvelopes(config, rawEvents);
    }

    @Override
    public List<Map<String, Object>> fetch(BoosterConfig config, String readModelName, UUID readModelID,
        SequenceKey sequenceKey) {
      return ReadModelAdapter.fetchReadModel(runtime.readModelRegistry(), config, readModelName, readModelID);
    }

    @Override
    public ReadModelListResult<Map<String, Object>> search(BoosterConfig config, String readModelName,
        Map<String, Object> filters, Map<String, Object> sortBy, Integer limit, Object afterCursor,
        Boolean paginatedVersion, List<String> select) {
      return ReadModelAdapter.searchReadModel(runtime.readModelRegistry(), config, readModelName, filters, sortBy,
          limit, afterCursor, paginatedVersion, select);
    }

    @Override
    public void store(BoosterConfig config, String readModelName, Map<String, Object> readModel,
        Long expectedCurrentVersion) {
      ReadModelAdapter.storeReadModel(runtime.graphQLService(), runtime.readModelRegistry(), config, readModelName,
          readModel, expectedCurrentVersion);
    }

    @Override
    public void delete(BoosterConfig config, String readModelName, Map<String, Object> readModel) {
      ReadModelAdapter.deleteReadModel(runtime.readModelRegistry(), config, readModelName, readModel);
    }

    @Override
    public void subscribe(BoosterConfig config, SubscriptionEnvelope subscriptionEnvelope) {
      SubscriptionAdapter.subscribeToReadModel(runtime.subscriptionRegistry(), config, subscriptionEnvelope);
    }

    @Override
    public List<SubscriptionEnvelope> fetchSubscriptions(BoosterConfig config, String subscriptionName) {
      return SubscriptionAdapter.fetchSubscriptions(runtime.subscriptionRegistry(), config, subscriptionName);
    }

    @Override
    public void deleteSubscription(BoosterConfig config, String connectionID, String subscriptionID) {
      SubscriptionAdapter.deleteSubscription(runtime.subscriptionRegistry(), config, connectionID, subscriptionID);
    }

    @Override
    public void deleteAllSubscriptions(BoosterConfig config, String connectionID) {
      SubscriptionAdapter.deleteAllSubscriptions(runtime.subscriptionRegistry(), config, connectionID);
    }
  }

  private static final class GraphQL implements ProviderGraphQLLibrary {
    @Override
    public GraphQLRequestEnvelopeOrError rawToEnvelope(BoosterConfig config, Object rawGraphQLRequest) {
      return GraphQLAdapter.rawGraphQLRequestToEnvelope(config, rawGraphQLRequest);
    }

    @Override
    public Object handleResult(Object result, Map<String, String> headers) {
      return ApiAdapter.requestSucceeded(result, headers);
    }
  }

  private static final class Api implements ProviderAPIHandling {
    @Override
    public Object requestSucceeded(Object body, Map<String, Object> headers) {
      return ApiAdapter.requestSucceeded(body, headers);
    }

    @Override
    public Object requestFailed(Throwable error) {
      return ApiAdapter.requestFailed(error);
    }

    @Override
    public Object healthRequestResult(Object body, boolean isHealthy) {
      return ApiAdapter.healthRequestResult(body, isHealthy);
    }
  }

  private final class Connections implements ProviderConnectionsLibrary {
    @Override
    public void storeData(BoosterConfig config, String connectionID, ConnectionDataEnvelope data) {
      ConnectionsAdapter.storeConnectionData(runtime.connectionRegistry(), config, connectionID, data);
    }

    @Override
    public Optional<ConnectionDataEnvelope> fetchData(BoosterConfig config, String connectionID) {
      return ConnectionsAdapter.fetchConnectionData(runtime.connectionRegistry(), config, connectionID);
    }

    @Override
    public void deleteData(BoosterConfig config, String connectionID) {
      ConnectionsAdapter.deleteConnectionData(runtime.connectionRegistry(), config, connectionID);
    }

    @Override
    public void sendMessage(BoosterConfig config, String connectionID, Object data) {
      ConnectionsAdapter.sendMessageToConnection(runtime.webSocketServerAdapter(), config, connectionID, data);
    }
  }

  private static final class Scheduled implements ScheduledCommandsLibrary {
    @Override
    public ScheduledCommandEnvelope rawToEnvelope(BoosterConfig config, Object rawMessage) {
      return ScheduledAdapter.rawScheduledInputToEnvelope(config, rawMessage);
    }
  }

  private static final class Rockets implements ProviderRocketLibrary {
    @Override
    public RocketEnvelope rawToEnvelopes(BoosterConfig config, Object request) {
      return RocketAdapter.rawRocketInputToEnvelope(config, request);
    }
  }

  private final class Sensor implements ProviderSensorLibrary {
    @Override
    public Object databaseEventsHealthDetails(BoosterConfig config) {
      return HealthAdapter.databaseEventsHealthDetails(runtime.eventRegistry());
    }

    @Override
    public Object databaseReadModelsHealthDetails(BoosterConfig config) {
      return HealthAdapter.databaseReadModelsHealthDetails(runtime.readModelRegistry());
    }

    @Override
    public boolean isDatabaseEventUp(BoosterConfig config) {
      return HealthAdapter.isDatabaseEventUp(runtime.baseDirectory());
    }

    @Override
    public boolean areDatabaseReadModelsUp(BoosterConfig config) {
      return HealthAdapter.areDatabaseReadModelsUp(runtime.baseDirectory());
    }

    @Override
    public List<String> databaseUrls(BoosterConfig config) {
      return HealthAdapter.databaseUrl();
    }

    @Override
    public boolean isGraphQLFunctionUp(BoosterConfig config) {
      return HealthAdapter.isGraphQLFunctionUp();
    }

    @Override
    public String graphQLFunctionUrl(BoosterConfig config) {
      return HealthAdapter.graphqlFunctionUrl();
    }

    @Override
    public HealthEnvelope rawRequestToHealthEnvelope(Object rawRequest) {
      return HealthAdapter.rawRequestToSensorHealth(rawRequest);
    }

    @Override
    public Map<String, Boolean> areRocketFunctionsUp(BoosterConfig config) {
      return HealthAdapter.areRocketFunctionsUp(config);
    }
  }
}
