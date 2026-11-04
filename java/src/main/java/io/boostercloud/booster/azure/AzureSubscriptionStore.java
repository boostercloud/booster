package io.boostercloud.booster.azure;

import com.azure.cosmos.CosmosContainer;
import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.*;
import io.boostercloud.booster.transport.GraphQLWebSocketSession;
import java.time.*;
import java.util.*;

/** Persisted Web PubSub identities and operations. No raw bearer tokens are stored. */
public final class AzureSubscriptionStore {
  public record Connection(String id, UserContext user, Instant expiresAt) {}
  public record Subscription(String connectionId, GraphQLWebSocketSession.Operation operation, long afterPosition, Instant expiresAt) {}
  record Delivery(String id, String owner, boolean duplicate, boolean retry) {}
  private final CosmosDocuments documents;
  private final Clock clock;
  public AzureSubscriptionStore(CosmosContainer container, String application) { this(new SdkCosmosDocuments(container, application), Clock.systemUTC()); }
  AzureSubscriptionStore(CosmosDocuments documents, Clock clock) { this.documents = Objects.requireNonNull(documents); this.clock = Objects.requireNonNull(clock); }
  public void saveConnection(Connection connection) {
    validate(connection.id()); Objects.requireNonNull(connection.user());
    write(connectionId(connection.id()), "connection", connection.id(), null, connection, connection.expiresAt());
  }
  public Optional<Connection> connection(String id) {
    validate(id);
    return documents.read(connectionId(id)).filter(this::active)
        .map(d -> CosmosEventStore.decode(d.value().get("data"), Connection.class));
  }
  public void saveSubscription(Subscription subscription) {
    validate(subscription.connectionId()); validate(subscription.operation().id());
    var connection = connection(subscription.connectionId()).orElseThrow(() -> new BoosterException(BoosterException.Code.FORBIDDEN, "Connection expired"));
    if (subscription.expiresAt().isAfter(connection.expiresAt()) || subscription.afterPosition() < 0) throw new IllegalArgumentException("Invalid subscription lifetime or position");
    write(subscriptionId(subscription.connectionId(), subscription.operation().id()), "subscription", subscription.connectionId(),
        subscription.operation().id(), subscription, subscription.expiresAt());
  }
  public List<Subscription> subscriptions(String connectionId) {
    validate(connectionId);
    return documents.query("SELECT * FROM c WHERE c.kind = 'subscription' AND c.connectionId = @connection AND c.expires > @now",
        Map.of("@connection", connectionId, "@now", clock.millis()), 10000).stream().filter(this::active)
        .map(d -> CosmosEventStore.decode(d.value().get("data"), Subscription.class)).toList();
  }
  public List<Subscription> allSubscriptions() {
    return documents.query("SELECT * FROM c WHERE c.kind = 'subscription' AND c.expires > @now", Map.of("@now", clock.millis()), Integer.MAX_VALUE)
        .stream().filter(this::active).map(d -> CosmosEventStore.decode(d.value().get("data"), Subscription.class)).toList();
  }
  public List<Connection> expiredConnections() {
    return documents.query("SELECT * FROM c WHERE c.kind = 'connection' AND c.expires > 0 AND c.expires <= @now", Map.of("@now", clock.millis()), Integer.MAX_VALUE)
        .stream().filter(d -> d.value().path("expires").asLong() > 0 && !active(d))
        .map(d -> CosmosEventStore.decode(d.value().get("data"), Connection.class)).toList();
  }
  public boolean isActive(Subscription subscription) {
    return connection(subscription.connectionId()).isPresent()
        && documents.read(subscriptionId(subscription.connectionId(), subscription.operation().id())).filter(this::active)
        .map(d -> CosmosEventStore.decode(d.value().get("data"), Subscription.class).equals(subscription)).orElse(false);
  }
  public void removeSubscription(String connectionId, String operationId) { expire(subscriptionId(connectionId, operationId)); }
  public void removeConnection(String connectionId) {
    // Expire connection first: concurrent notifiers fail closed even if cleanup is retried.
    expire(connectionId(connectionId));
    for (var subscription : subscriptions(connectionId)) removeSubscription(connectionId, subscription.operation().id());
  }
  /** Durable CloudEvent retry receipt. Fingerprints prevent one service event ID changing its body. */
  Delivery beginDelivery(String connection, String eventId, String fingerprint) {
    validate(connection); validate(eventId);
    String id = "delivery-" + CosmosEventStore.hash(Json.MAPPER.valueToTree(List.of(connection, eventId)).toString());
    for (int attempt = 0; attempt < 16; attempt++) {
      var previous = documents.read(id);
      if (previous.isPresent()) {
        var value = previous.get().value();
        if (!fingerprint.equals(value.path("fingerprint").asText())) throw CosmosEventStore.conflict("CloudEvent ID reused with different content");
        if (value.path("completed").asBoolean()) return new Delivery(id, "", true, true);
        if (value.path("leaseUntil").asLong() > clock.millis()) throw CosmosEventStore.conflict("CloudEvent is already being processed");
      }
      String owner = UUID.randomUUID().toString();
      var value = CosmosEventStore.object("delivery").put("fingerprint", fingerprint).put("owner", owner)
          .put("leaseUntil", clock.millis() + 120000).put("completed", false).put("ttl", 86400);
      if (documents.transact(List.of(new CosmosDocuments.Write(id, value, previous.map(CosmosDocuments.Document::etag).orElse(null)))))
        return new Delivery(id, owner, false, previous.isPresent());
    }
    throw CosmosEventStore.conflict("CloudEvent receipt contention");
  }
  void finishDelivery(Delivery delivery, boolean success) {
    var current = documents.read(delivery.id()).orElseThrow();
    if (!delivery.owner().equals(current.value().path("owner").asText())) throw CosmosEventStore.conflict("CloudEvent receipt lease lost");
    var value = (com.fasterxml.jackson.databind.node.ObjectNode) current.value();
    value.put("leaseUntil", 0).put("completed", success);
    if (!documents.transact(List.of(new CosmosDocuments.Write(delivery.id(), value, current.etag())))) throw CosmosEventStore.conflict("CloudEvent receipt changed");
  }
  private boolean active(CosmosDocuments.Document document) { return document.value().path("expires").asLong() > clock.millis(); }
  private void write(String id, String kind, String connectionId, String operationId, Object data, Instant expires) {
    if (expires == null || !expires.isAfter(clock.instant())) throw new IllegalArgumentException("Future expiration required");
    // Keep expired rows long enough for the background worker to close Web PubSub
    // sockets. Authorization uses absolute expiry and never this cleanup grace period.
    long seconds = Math.max(1, (Duration.between(clock.instant(), expires).toMillis() + 999) / 1000) + 300;
    if (seconds > Integer.MAX_VALUE) throw new IllegalArgumentException("Connection lifetime exceeds Cosmos TTL");
    var value = CosmosEventStore.object(kind).put("connectionId", connectionId).put("expires", expires.toEpochMilli()).put("ttl", seconds);
    if (operationId != null) value.put("operationId", operationId);
    value.set("data", Json.MAPPER.valueToTree(data));
    for (int attempt = 0; attempt < 16; attempt++) {
      var previous = documents.read(id);
      if (documents.transact(List.of(new CosmosDocuments.Write(id, value, previous.map(CosmosDocuments.Document::etag).orElse(null))))) return;
    }
    throw CosmosEventStore.conflict("Subscription state contention");
  }
  private void expire(String id) {
    for (int attempt = 0; attempt < 16; attempt++) {
      var previous = documents.read(id);
      if (previous.isEmpty()) return;
      var value = previous.get().value().deepCopy();
      ((com.fasterxml.jackson.databind.node.ObjectNode) value).put("expires", 0L).put("ttl", 1);
      if (documents.transact(List.of(new CosmosDocuments.Write(id, value, previous.get().etag())))) return;
    }
    throw CosmosEventStore.conflict("Subscription cleanup contention");
  }
  private static String connectionId(String value) { validate(value); return "connection-" + CosmosEventStore.hash(value); }
  private static String subscriptionId(String connection, String operation) { validate(connection); validate(operation); return "subscription-" + CosmosEventStore.hash(Json.MAPPER.valueToTree(List.of(connection, operation)).toString()); }
  private static void validate(String value) { if (value == null || value.isBlank() || value.length() > 1024) throw new IllegalArgumentException("Invalid connection or operation ID"); }
}
