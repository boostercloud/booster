package io.boostercloud.booster.azure;

import com.azure.core.amqp.*;
import com.azure.core.credential.TokenCredential;
import com.azure.cosmos.*;
import com.azure.messaging.eventhubs.*;
import com.azure.messaging.webpubsub.*;
import io.boostercloud.booster.core.Booster;
import java.net.URI;
import java.util.*;

/** Real Azure service clients and provider stores. Infrastructure is provisioned separately. */
public final class AzureProvider implements AutoCloseable {
  public record Config(String cosmosEndpoint, String database, String journalContainer, String stateContainer,
      String leaseContainer, String application, String eventHubNamespace, String eventHubName, String webPubSubEndpoint) {
    public Config {
      https(cosmosEndpoint); https(webPubSubEndpoint);
      for (String value : List.of(database, journalContainer, stateContainer, leaseContainer, application, eventHubName))
        if (value.isBlank() || value.length() > 200) throw new IllegalArgumentException("Invalid Azure resource name");
      if (eventHubNamespace == null || !eventHubNamespace.matches("[a-zA-Z0-9.-]+")) throw new IllegalArgumentException("Event Hubs namespace must be a host name");
    }
    public static Config fromEnvironment() { return fromEnvironment(System.getenv()); }
    public static Config fromEnvironment(Map<String, String> env) {
      return new Config(required(env, "COSMOS_ENDPOINT"), required(env, "DATABASE"), env.getOrDefault("BOOSTER_AZURE_JOURNAL_CONTAINER", "journal"),
          env.getOrDefault("BOOSTER_AZURE_STATE_CONTAINER", "state"), env.getOrDefault("BOOSTER_AZURE_LEASE_CONTAINER", "leases"),
          required(env, "APPLICATION"), required(env, "EVENTHUB_NAMESPACE"), env.getOrDefault("BOOSTER_AZURE_EVENTHUB_NAME", "booster"), required(env, "WEBPUBSUB_ENDPOINT"));
    }
    private static String required(Map<String, String> env, String suffix) {
      String key = "BOOSTER_AZURE_" + suffix, value = env.get(key);
      if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key); return value;
    }
    private static void https(String endpoint) {
      if (endpoint == null) throw new IllegalArgumentException("Azure endpoint required");
      var uri = URI.create(endpoint);
      if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
        throw new IllegalArgumentException("Azure endpoint must use HTTPS without embedded credentials");
    }
  }
  private final Config config;
  private final CosmosClient cosmos;
  private final CosmosAsyncClient asyncCosmos;
  private final EventHubProducerClient producer;
  private final EventHubConsumerClient consumer;
  private final CosmosEventStore events;
  private final CosmosStateStore state;
  private final WebPubSubServiceClient webPubSub;
  private final List<AutoCloseable> workers = new ArrayList<>();
  private boolean closed;

  public static AzureProvider open(Config config) { return new AzureProvider(config, AzureClients.credential()); }
  public AzureProvider(Config config, TokenCredential credential) {
    this.config = Objects.requireNonNull(config); Objects.requireNonNull(credential);
    var gateway = new GatewayConnectionConfig();
    if (AzureClients.proxy() != null) gateway.setProxy(AzureClients.proxy());
    var cosmosBuilder = new CosmosClientBuilder().endpoint(config.cosmosEndpoint()).credential(credential)
        .consistencyLevel(ConsistencyLevel.SESSION).gatewayMode(gateway).contentResponseOnWriteEnabled(true);
    cosmos = cosmosBuilder.buildClient(); asyncCosmos = cosmosBuilder.buildAsyncClient();
    events = new CosmosEventStore(cosmos.getDatabase(config.database()).getContainer(config.journalContainer()), config.application());
    state = new CosmosStateStore(cosmos.getDatabase(config.database()).getContainer(config.stateContainer()), config.application());
    producer = eventHubBuilder(config, credential).buildProducerClient();
    consumer = eventHubBuilder(config, credential).consumerGroup(EventHubClientBuilder.DEFAULT_CONSUMER_GROUP_NAME).buildConsumerClient();
    webPubSub = new WebPubSubServiceClientBuilder().endpoint(config.webPubSubEndpoint()).hub("booster")
        .credential(credential).httpClient(AzureClients.httpClient()).buildClient();
  }
  static EventHubClientBuilder eventHubBuilder(Config config, TokenCredential credential) {
    var builder = new EventHubClientBuilder().credential(config.eventHubNamespace(), config.eventHubName(), credential)
        .transportType(AmqpTransportType.AMQP_WEB_SOCKETS);
    var proxy = AzureClients.proxy();
    if (proxy != null) builder.proxyOptions(new com.azure.core.amqp.ProxyOptions(
        proxy.getUsername() == null ? ProxyAuthenticationType.NONE : ProxyAuthenticationType.BASIC,
        new java.net.Proxy(java.net.Proxy.Type.HTTP, proxy.getAddress()), proxy.getUsername(), proxy.getPassword()));
    return builder;
  }
  public CosmosEventStore eventStore() { return events; }
  public CosmosStateStore stateStore() { return state; }
  public WebPubSubServiceClient webPubSub() { return webPubSub; }
  public Config config() { return config; }
  public AzureSubscriptionStore subscriptions() { return new AzureSubscriptionStore(cosmos.getDatabase(config.database()).getContainer(config.stateContainer()), config.application()); }
  public synchronized AzureEventPipeline startEventPipeline(Booster runtime, String workerName) {
    if (closed) throw new IllegalStateException("Azure provider is closed");
    var database = asyncCosmos.getDatabase(config.database());
    var worker = new AzureEventPipeline(database.getContainer(config.journalContainer()), database.getContainer(config.leaseContainer()),
        producer, consumer, state, runtime, config.application(), workerName);
    worker.start(); workers.add(worker); return worker;
  }
  public synchronized AzureSubscriptionNotifier startSubscriptionNotifications(AzureWebPubSubHandler handler, String workerName) {
    if (closed) throw new IllegalStateException("Azure provider is closed");
    var database = asyncCosmos.getDatabase(config.database());
    var worker = new AzureSubscriptionNotifier(database.getContainer(config.stateContainer()), database.getContainer(config.leaseContainer()), config.application(), workerName, handler);
    worker.start(); workers.add(worker); return worker;
  }
  /** Performs real service requests; failures remain failures rather than reporting a synthetic UP. */
  public Map<String, Object> health() {
    var result = new LinkedHashMap<String, Object>();
    result.put("journalContainer", cosmos.getDatabase(config.database()).getContainer(config.journalContainer()).read().getStatusCode());
    result.put("stateContainer", cosmos.getDatabase(config.database()).getContainer(config.stateContainer()).read().getStatusCode());
    result.put("eventHub", producer.getEventHubProperties().getName()); result.put("journalPosition", events.lastPosition());
    return Map.copyOf(result);
  }
  @Override public synchronized void close() {
    if (closed) return; closed = true;
    try { stopWorkers(); }
    finally { producer.close(); consumer.close(); asyncCosmos.close(); cosmos.close(); }
  }
  /** Stop callbacks before closing the Booster runtime. Service clients remain available until close(). */
  public synchronized void stopWorkers() {
    RuntimeException first = null;
    for (var worker : workers.reversed()) try { worker.close(); }
    catch (Exception failure) {
      if (first == null) first = new IllegalStateException("Azure worker shutdown failed", failure);
      else first.addSuppressed(failure);
    }
    workers.clear();
    if (first != null) throw first;
  }
}
