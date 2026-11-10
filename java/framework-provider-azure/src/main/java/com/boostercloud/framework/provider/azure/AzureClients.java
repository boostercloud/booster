package com.boostercloud.framework.provider.azure;

import com.boostercloud.framework.provider.azure.Constants.EnvironmentVarNames;
import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.cosmos.rest.CosmosRestClient;
import com.boostercloud.framework.provider.azure.cosmos.rest.CosmosRetryOptions;
import com.boostercloud.framework.provider.azure.eventhubs.EventHubProducerClient;
import com.boostercloud.framework.provider.azure.eventhubs.EventHubRetryOptions;
import com.boostercloud.framework.provider.azure.eventhubs.rest.EventHubsRestProducerClient;
import com.boostercloud.framework.provider.azure.library.HealthAdapter;
import com.boostercloud.framework.provider.azure.webpubsub.WebPubSubServiceClientFactory;
import com.boostercloud.framework.provider.azure.webpubsub.rest.WebPubSubRestServiceClient;
import com.boostercloud.framework.types.EnvironmentVariables;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The Azure service clients of the provider: the Java counterpart of the module-level {@code cosmosClient} and
 * {@code producer} of {@code index.ts}. They are created lazily, on first use, from the app settings, so the provider
 * can be loaded without Azure credentials (the CLI and the deployer do); using a client whose settings are missing
 * fails with a descriptive {@link IllegalStateException} (TypeScript fails with
 * {@code TypeError: cosmosDb.database is not a function}).
 *
 * @param cosmos the Cosmos DB client
 * @param producer the Event Hubs producer
 * @param webPubSub creates Web PubSub clients (one per message, as TypeScript does)
 * @param http the HTTP requester of the health checks
 */
public record AzureClients(
    Supplier<CosmosClient> cosmos,
    Supplier<EventHubProducerClient> producer,
    WebPubSubServiceClientFactory webPubSub,
    HealthAdapter.HttpRequester http) {

  private static final Lazy<AzureClients> SHARED = new Lazy<>(AzureClients::fromEnvironment);

  /**
   * @return the process-wide clients (every {@code Provider.provider(...)} result shares them, like the module-level
   *     singletons of TypeScript)
   */
  public static AzureClients shared() {
    return SHARED.get();
  }

  /**
   * Clients built from the app settings, each on first use:
   *
   * <ul>
   *   <li>Cosmos DB: {@code COSMOSDB_CONNECTION_STRING}; unset means "no client" (an empty value is parsed and
   *       rejected); retry options from {@code COSMOSDB_MAX_RETRIES}, {@code COSMOSDB_RETRY_INTERVAL},
   *       {@code COSMOSDB_MAX_WAIT_TIME};
   *   <li>Event Hubs: {@code EVENTHUB_CONNECTION_STRING} and {@code EVENTHUB_NAME}; unset or empty means "no producer";
   *       retry options from {@code EVENTHUB_MAX_RETRIES} and {@code EVENTHUB_MODE};
   *   <li>Web PubSub: the REST client factory (the connection string is read per message).
   * </ul>
   *
   * @return the clients
   */
  public static AzureClients fromEnvironment() {
    Lazy<CosmosClient> cosmos = new Lazy<>(() -> {
      Optional<String> connectionString = EnvironmentVariables.get(EnvironmentVarNames.COSMOS_DB_CONNECTION_STRING);
      if (connectionString.isEmpty()) {
        throw new IllegalStateException("The Cosmos DB client is not available: the "
            + EnvironmentVarNames.COSMOS_DB_CONNECTION_STRING + " app setting is not set");
      }
      return CosmosRestClient.create(connectionString.get(), CosmosRetryOptions.fromEnvironment());
    });
    Lazy<EventHubProducerClient> producer = new Lazy<>(() -> {
      String connectionString = EnvironmentVariables.get(EnvironmentVarNames.EVENT_HUB_CONNECTION_STRING).orElse("");
      String eventHubName = EnvironmentVariables.get(EnvironmentVarNames.EVENT_HUB_NAME).orElse("");
      if (connectionString.isEmpty() || eventHubName.isEmpty()) {
        throw new IllegalStateException("The Event Hubs producer is not available: the "
            + EnvironmentVarNames.EVENT_HUB_CONNECTION_STRING + " and " + EnvironmentVarNames.EVENT_HUB_NAME
            + " app settings must be set (event streaming is probably disabled)");
      }
      return EventHubsRestProducerClient.create(connectionString, eventHubName, EventHubRetryOptions.fromEnvironment());
    });
    return new AzureClients(cosmos, producer, WebPubSubRestServiceClient.factory(), HealthAdapter.HttpRequester.DEFAULT);
  }

  /**
   * @param cosmos the Cosmos DB client
   * @param producer the Event Hubs producer
   * @param webPubSub the Web PubSub factory
   * @param http the HTTP requester
   * @return fixed clients (tests)
   */
  public static AzureClients of(
      CosmosClient cosmos,
      EventHubProducerClient producer,
      WebPubSubServiceClientFactory webPubSub,
      HealthAdapter.HttpRequester http) {
    return new AzureClients(() -> cosmos, () -> producer, webPubSub, http);
  }

  /** A thread-safe memoising supplier; a failed initialisation is retried on the next call. */
  static final class Lazy<T> implements Supplier<T> {
    private final Supplier<T> initializer;
    private volatile T value;

    Lazy(Supplier<T> initializer) {
      this.initializer = initializer;
    }

    @Override
    public T get() {
      T current = value;
      if (current == null) {
        synchronized (this) {
          current = value;
          if (current == null) {
            current = initializer.get();
            value = current;
          }
        }
      }
      return current;
    }
  }
}
