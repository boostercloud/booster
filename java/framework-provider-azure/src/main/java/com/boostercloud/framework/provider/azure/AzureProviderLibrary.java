package com.boostercloud.framework.provider.azure;

import com.boostercloud.framework.provider.azure.library.ApiAdapter;
import com.boostercloud.framework.provider.azure.library.ConnectionsAdapter;
import com.boostercloud.framework.provider.azure.library.EventDeleteAdapter;
import com.boostercloud.framework.provider.azure.library.EventsAdapter;
import com.boostercloud.framework.provider.azure.library.EventsSearcherAdapter;
import com.boostercloud.framework.provider.azure.library.EventsStoreAdapter;
import com.boostercloud.framework.provider.azure.library.EventsStreamConsumerAdapter;
import com.boostercloud.framework.provider.azure.library.EventsStreamProducerAdapter;
import com.boostercloud.framework.provider.azure.library.GraphQLAdapter;
import com.boostercloud.framework.provider.azure.library.HealthAdapter;
import com.boostercloud.framework.provider.azure.library.ReadModelAdapter;
import com.boostercloud.framework.provider.azure.library.RocketAdapter;
import com.boostercloud.framework.provider.azure.library.ScheduledAdapter;
import com.boostercloud.framework.provider.azure.library.SearcherAdapter;
import com.boostercloud.framework.provider.azure.library.SubscriptionAdapter;
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
import com.boostercloud.framework.types.provider.ProviderLoader;
import com.boostercloud.framework.types.provider.ProviderReadModelsLibrary;
import com.boostercloud.framework.types.provider.ProviderRocketLibrary;
import com.boostercloud.framework.types.provider.ProviderSensorLibrary;
import com.boostercloud.framework.types.provider.ScheduledCommandsLibrary;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Azure {@link ProviderLibrary}: the object {@code Provider(rockets)} builds in {@code index.ts}, with every
 * adapter bound to the shared clients ({@code fn.bind(null, cosmosClient)}). The argument orders of the SPI are the
 * TypeScript ones ({@code config} last for {@code store}, {@code storeSnapshot}, {@code storeDispatched} and
 * {@code produce}).
 */
public final class AzureProviderLibrary implements ProviderLibrary {

  private final List<RocketDescriptor> rockets;
  private final AzureClients clients;
  private final ProviderEventsLibrary events = new Events();
  private final ProviderReadModelsLibrary readModels = new ReadModels();
  private final ProviderGraphQLLibrary graphQL = new GraphQL();
  private final ProviderAPIHandling api = new Api();
  private final ProviderConnectionsLibrary connections = new Connections();
  private final ScheduledCommandsLibrary scheduled = (config, input) -> scheduled(config, input);
  private final ProviderRocketLibrary rocketsLibrary = RocketAdapter::rawRocketInputToEnvelope;
  private final ProviderSensorLibrary sensor = new Sensor();

  /**
   * @param rockets the rockets of the application (passed to the infrastructure)
   * @param clients the Azure clients
   */
  public AzureProviderLibrary(List<RocketDescriptor> rockets, AzureClients clients) {
    this.rockets = rockets == null ? List.of() : List.copyOf(rockets);
    this.clients = clients;
  }

  private static ScheduledCommandEnvelope scheduled(BoosterConfig config, Object input) {
    return ScheduledAdapter.rawScheduledInputToEnvelope(config, input);
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

  @Override
  public ProviderRocketLibrary rockets() {
    return rocketsLibrary;
  }

  @Override
  public ProviderSensorLibrary sensor() {
    return sensor;
  }

  /**
   * Loads {@code com.boostercloud.framework.provider.azure.infrastructure.Infrastructure} lazily (it is a deploy-time
   * module, not a runtime dependency of the function app). A loading failure is reported with the TypeScript message,
   * adapted to Maven.
   */
  @Override
  public ProviderInfrastructure infrastructure() {
    String infrastructurePackageName = Provider.INFRASTRUCTURE_ARTIFACT;
    HasInfrastructure infrastructure;
    try {
      infrastructure = Provider.loadInfrastructurePackage(Provider.PACKAGE);
    } catch (RuntimeException e) {
      throw new IllegalStateException("The Azure infrastructure package could not be loaded. The following error was "
          + "thrown: " + e.getMessage() + ". Please ensure that one of the following actions has been done:\n"
          + "  - It has been specified in the \"dependencies\" section of your \"pom.xml\" file. You can do so by "
          + "adding the dependency '" + infrastructurePackageName + "'\n"
          + "  - Or it is on the classpath of the process that runs the Booster CLI", e);
    }
    return infrastructure.infrastructure(rockets);
  }

  private final class Events implements ProviderEventsLibrary {
    @Override
    public List<EventEnvelope> rawToEnvelopes(Object rawEvents) {
      return EventsAdapter.rawEventsToEnvelopes(rawEvents);
    }

    @Override
    public List<EventEnvelope> rawStreamToEnvelopes(BoosterConfig config, Object context, List<Object> dedupEventStream) {
      return EventsStreamConsumerAdapter.rawEventsStreamToEnvelopes(config, context, dedupEventStream);
    }

    @Override
    public List<Object> dedupEventStream(BoosterConfig config, Object rawEvents) {
      return EventsStreamConsumerAdapter.dedupEventStream(clients.cosmos().get(), config, rawEvents);
    }

    @Override
    public void produce(String entityName, UUID entityID, List<EventEnvelope> eventEnvelopes, BoosterConfig config) {
      EventsStreamProducerAdapter.produceEventsStream(clients.producer().get(), entityName, entityID, eventEnvelopes,
          config);
    }

    @Override
    public List<EventEnvelope> forEntitySince(BoosterConfig config, String entityTypeName, UUID entityID, String since) {
      return EventsAdapter.readEntityEventsSince(clients.cosmos().get(), config, entityTypeName, entityID, since);
    }

    @Override
    public Optional<EntitySnapshotEnvelope> latestEntitySnapshot(
        BoosterConfig config, String entityTypeName, UUID entityID) {
      return EventsAdapter.readEntityLatestSnapshot(clients.cosmos().get(), config, entityTypeName, entityID);
    }

    @Override
    public List<EventSearchResponse> search(BoosterConfig config, EventSearchParameters parameters) {
      return EventsSearcherAdapter.searchEvents(clients.cosmos().get(), config, parameters);
    }

    @Override
    public PaginatedEntitiesIdsResult searchEntitiesIDs(
        BoosterConfig config, int limit, Map<String, String> afterCursor, String entityTypeName) {
      return EventsSearcherAdapter.searchEntitiesIds(clients.cosmos().get(), config, limit, afterCursor,
          entityTypeName);
    }

    @Override
    public List<EventEnvelope> store(List<NonPersistedEventEnvelope> eventEnvelopes, BoosterConfig config) {
      return EventsStoreAdapter.storeEvents(clients.cosmos().get(), eventEnvelopes, config);
    }

    @Override
    public EntitySnapshotEnvelope storeSnapshot(NonPersistedEntitySnapshotEnvelope snapshotEnvelope, BoosterConfig config) {
      return EventsAdapter.storeSnapshot(clients.cosmos().get(), snapshotEnvelope, config);
    }

    @Override
    public boolean storeDispatched(EventEnvelope eventEnvelope, BoosterConfig config) {
      return EventsAdapter.storeDispatchedEvent(clients.cosmos().get(), eventEnvelope, config);
    }

    @Override
    public List<EventEnvelope> findDeletableEvent(BoosterConfig config, EventDeleteParameters parameters) {
      return EventDeleteAdapter.findDeletableEvent(clients.cosmos().get(), config, parameters);
    }

    @Override
    public List<EntitySnapshotEnvelope> findDeletableSnapshot(BoosterConfig config, SnapshotDeleteParameters parameters) {
      return EventDeleteAdapter.findDeletableSnapshot(clients.cosmos().get(), config, parameters);
    }

    @Override
    public void deleteEvent(BoosterConfig config, List<EventEnvelope> events) {
      EventDeleteAdapter.deleteEvent(clients.cosmos().get(), config, events);
    }

    @Override
    public void deleteSnapshot(BoosterConfig config, List<EntitySnapshotEnvelope> snapshots) {
      EventDeleteAdapter.deleteSnapshot(clients.cosmos().get(), config, snapshots);
    }
  }

  private final class ReadModels implements ProviderReadModelsLibrary {
    @Override
    public List<ReadModelEnvelope> rawToEnvelopes(BoosterConfig config, Object rawEvents) {
      return ReadModelAdapter.rawReadModelEventsToEnvelopes(config, rawEvents);
    }

    /** {@code fetchReadModel}; the sequence key is ignored, as in TypeScript. */
    @Override
    public List<Map<String, Object>> fetch(
        BoosterConfig config, String readModelName, UUID readModelID, SequenceKey sequenceKey) {
      return ReadModelAdapter.fetchReadModel(clients.cosmos().get(), config, readModelName, readModelID);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ReadModelListResult<Map<String, Object>> search(
        BoosterConfig config,
        String readModelName,
        Map<String, Object> filters,
        Map<String, Object> sortBy,
        Integer limit,
        Object afterCursor,
        Boolean paginatedVersion,
        List<String> select) {
      Object result = SearcherAdapter.searchReadModel(clients.cosmos().get(), config, readModelName,
          filters == null ? Map.of() : filters, sortBy, limit,
          afterCursor instanceof Map<?, ?> cursor ? (Map<String, ?>) cursor : null, paginatedVersion, select);
      if (result instanceof ReadModelListResult<?> page) {
        return (ReadModelListResult<Map<String, Object>>) page;
      }
      return ReadModelListResult.of((List<Map<String, Object>>) result);
    }

    /** {@code storeReadModel}; {@code expectedCurrentVersion} is ignored, concurrency is etag-based (quirk Q4). */
    @Override
    public void store(
        BoosterConfig config, String readModelName, Map<String, Object> readModel, Long expectedCurrentVersion) {
      ReadModelAdapter.storeReadModel(clients.cosmos().get(), config, readModelName, readModel);
    }

    @Override
    public void delete(BoosterConfig config, String readModelName, Map<String, Object> readModel) {
      ReadModelAdapter.deleteReadModel(clients.cosmos().get(), config, readModelName, readModel);
    }

    @Override
    public void subscribe(BoosterConfig config, SubscriptionEnvelope subscriptionEnvelope) {
      SubscriptionAdapter.subscribeToReadModel(clients.cosmos().get(), config, subscriptionEnvelope);
    }

    @Override
    public List<SubscriptionEnvelope> fetchSubscriptions(BoosterConfig config, String subscriptionName) {
      return SubscriptionAdapter.fetchSubscriptions(clients.cosmos().get(), config, subscriptionName);
    }

    @Override
    public void deleteSubscription(BoosterConfig config, String connectionID, String subscriptionID) {
      SubscriptionAdapter.deleteSubscription(clients.cosmos().get(), config, connectionID, subscriptionID);
    }

    @Override
    public void deleteAllSubscriptions(BoosterConfig config, String connectionID) {
      SubscriptionAdapter.deleteAllSubscriptions(clients.cosmos().get(), config, connectionID);
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
      ConnectionsAdapter.storeConnectionData(clients.cosmos().get(), config, connectionID, data);
    }

    @Override
    public Optional<ConnectionDataEnvelope> fetchData(BoosterConfig config, String connectionID) {
      return ConnectionsAdapter.fetchConnectionData(clients.cosmos().get(), config, connectionID);
    }

    @Override
    public void deleteData(BoosterConfig config, String connectionID) {
      ConnectionsAdapter.deleteConnectionData(clients.cosmos().get(), config, connectionID);
    }

    @Override
    public void sendMessage(BoosterConfig config, String connectionID, Object data) {
      ConnectionsAdapter.sendMessageToConnection(clients.webPubSub(), config, connectionID, data);
    }
  }

  private final class Sensor implements ProviderSensorLibrary {
    @Override
    public Object databaseEventsHealthDetails(BoosterConfig config) {
      return HealthAdapter.databaseEventsHealthDetails(clients.cosmos().get(), config);
    }

    @Override
    public Object databaseReadModelsHealthDetails(BoosterConfig config) {
      return HealthAdapter.databaseReadModelsHealthDetails(clients.cosmos().get(), config);
    }

    @Override
    public boolean isDatabaseEventUp(BoosterConfig config) {
      return HealthAdapter.isDatabaseEventUp(clients.cosmos().get(), config);
    }

    @Override
    public boolean areDatabaseReadModelsUp(BoosterConfig config) {
      return HealthAdapter.areDatabaseReadModelsUp(clients.cosmos().get(), config);
    }

    @Override
    public List<String> databaseUrls(BoosterConfig config) {
      return HealthAdapter.databaseUrl(clients.cosmos().get(), config);
    }

    /** {@code isGraphQLFunctionUp}; the configuration is ignored, as in TypeScript. */
    @Override
    public boolean isGraphQLFunctionUp(BoosterConfig config) {
      return HealthAdapter.isGraphQLFunctionUp(clients.http());
    }

    /** {@code graphqlFunctionUrl}; the configuration is ignored, as in TypeScript. */
    @Override
    public String graphQLFunctionUrl(BoosterConfig config) {
      return HealthAdapter.graphqlFunctionUrl();
    }

    @Override
    public HealthEnvelope rawRequestToHealthEnvelope(Object rawRequest) {
      return HealthAdapter.rawRequestToSensorHealth(rawRequest);
    }

    /** {@code areRocketFunctionsUp}; the configuration is ignored, as in TypeScript. */
    @Override
    public Map<String, Boolean> areRocketFunctionsUp(BoosterConfig config) {
      return HealthAdapter.areRocketFunctionsUp(clients.http());
    }
  }

  /**
   * @return the rocket descriptors the library was built with
   */
  public List<RocketDescriptor> rocketDescriptors() {
    return rockets;
  }
}
