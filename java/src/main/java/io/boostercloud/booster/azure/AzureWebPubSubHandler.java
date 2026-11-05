package io.boostercloud.booster.azure;

import com.azure.messaging.webpubsub.WebPubSubServiceClient;
import com.azure.messaging.webpubsub.models.WebPubSubContentType;
import com.fasterxml.jackson.databind.JsonNode;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import io.boostercloud.booster.core.*;
import io.boostercloud.booster.transport.*;
import org.reactivestreams.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Azure Web PubSub CloudEvents adapter. Hosting MUST authenticate the service request
 * before calling handleCloudEvent; ce-* headers alone do not establish authenticity.
 * Operations and identity survive host restarts; notifications originate from Cosmos.
 */
public final class AzureWebPubSubHandler {
  public record Response(int status, Map<String, String> headers, String body) {}
  public interface Messenger { void send(String connectionId, String message); void close(String connectionId); }
  private final Booster runtime;
  private final BoosterGraphQL graphql;
  private final AuthenticationProvider authentication;
  private final AzureSubscriptionStore store;
  private final Messenger messenger;
  private final Clock clock;
  public AzureWebPubSubHandler(Booster runtime, BoosterGraphQL graphql, AuthenticationProvider authentication,
      AzureSubscriptionStore store, WebPubSubServiceClient service) {
    this(runtime, graphql, authentication, store, new Messenger() {
      public void send(String connectionId, String message) { service.sendToConnection(connectionId, message, WebPubSubContentType.TEXT_PLAIN); }
      public void close(String connectionId) { service.closeConnectionWithResponse(connectionId, null); }
    }, Clock.systemUTC());
  }
  AzureWebPubSubHandler(Booster runtime, BoosterGraphQL graphql, AuthenticationProvider authentication,
      AzureSubscriptionStore store, Messenger messenger, Clock clock) {
    this.runtime = Objects.requireNonNull(runtime); this.graphql = Objects.requireNonNull(graphql);
    this.authentication = Objects.requireNonNull(authentication); this.store = Objects.requireNonNull(store);
    this.messenger = Objects.requireNonNull(messenger); this.clock = Objects.requireNonNull(clock);
  }
  public Response handleCloudEvent(Map<String, String> requestHeaders, String body) {
    var headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER); headers.putAll(requestHeaders);
    if (!"1.0".equals(headers.get("ce-specversion")) || !"booster".equals(headers.get("ce-hub")))
      return response(400, "Invalid Web PubSub CloudEvent");
    String connectionId = headers.get("ce-connectionId"), type = headers.get("ce-type");
    if (connectionId == null || connectionId.isBlank() || connectionId.length() > 1024 || type == null)
      return response(400, "Invalid Web PubSub connection");
    switch (type) {
      case "azure.webpubsub.sys.connect" -> { return new Response(200, Map.of("Content-Type", "application/json"), "{\"subprotocol\":\"graphql-ws\"}"); }
      case "azure.webpubsub.sys.disconnect", "azure.webpubsub.sys.disconnected" -> { store.removeConnection(connectionId); return response(204, ""); }
      case "azure.webpubsub.sys.connected" -> { return response(204, ""); }
      case "azure.webpubsub.user.message" -> {
        if (body == null || body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > GraphQLWebSocketSession.MAX_MESSAGE_BYTES)
          return response(413, "Message too large");
        String eventId = headers.get("ce-id");
        if (eventId == null || eventId.isBlank() || eventId.length() > 1024) return response(400, "CloudEvent ID required");
        var delivery = store.beginDelivery(connectionId, eventId, CosmosEventStore.hash(body));
        if (delivery.duplicate()) return response(200, "");
        boolean success = false;
        try {
          processMessage(connectionId, eventId, body, delivery.retry()); success = true;
          return response(200, "");
        } finally { store.finishDelivery(delivery, success); }
      }
      default -> { return response(400, "Unsupported Web PubSub event"); }
    }
  }
  private void processMessage(String connectionId, String cloudEventId, String message, boolean retry) {
    if (retry && recoverPersistedMessage(connectionId, message)) return;
    var restoring = new AtomicBoolean(true);
    var deliveryFailure = new java.util.concurrent.atomic.AtomicReference<RuntimeException>();
    GraphQLWebSocketSession.Listener listener = new GraphQLWebSocketSession.Listener() {
      @Override public void initialized(UserContext user, Instant expires) { store.saveConnection(new AzureSubscriptionStore.Connection(connectionId, user, expires)); }
      @Override public void started(GraphQLWebSocketSession.Operation operation, RequestContext context) {
        if (restoring.get()) return;
        var connection = store.connection(connectionId).orElseThrow();
        store.saveSubscription(new AzureSubscriptionStore.Subscription(connectionId, operation, runtime.position(), connection.expiresAt()));
      }
      @Override public void stopped(String operationId) { if (!restoring.get() && deliveryFailure.get() == null) store.removeSubscription(connectionId, operationId); }
      @Override public void closed() { if (deliveryFailure.get() == null) store.removeConnection(connectionId); }
    };
    var session = new GraphQLWebSocketSession(connectionId, (operation, context) -> {
      // A service retry retains its identity even when the local protocol allows a
      // completed operation ID to be reused by a different CloudEvent.
      var stable = new RequestContext("azure-ws:" + CosmosEventStore.hash(Json.MAPPER.valueToTree(List.of(connectionId, cloudEventId, operation.id())).toString()),
          context.user(), context.metadata());
      ExecutionResult result = graphql.execute(operation.query(), operation.variables(), operation.operationName(), stable);
      if (result.getData() instanceof Publisher<?>) {
        // Durable operations are notified by the leased Cosmos processor, never an
        // instance-local listener. A request-scoped protocol session owns no socket.
        Publisher<ExecutionResult> dormant = downstream -> downstream.onSubscribe(new Subscription() {
          public void request(long count) {} public void cancel() {}
        });
        return ExecutionResultImpl.newExecutionResult().from(result).data(dormant).build();
      }
      return result;
    }, authentication, value -> {
      try { messenger.send(connectionId, value); }
      catch (RuntimeException failure) { deliveryFailure.compareAndSet(null, failure); throw failure; }
    }, () -> { if (deliveryFailure.get() == null) messenger.close(connectionId); },
        clock, Duration.ofHours(2), listener);
    var connection = store.connection(connectionId);
    if (connection.isPresent()) {
      session.restore(connection.get().user(), connection.get().expiresAt());
      for (var subscription : store.subscriptions(connectionId)) session.receive(startMessage(subscription.operation()));
    }
    restoring.set(false); session.receive(message);
    if (deliveryFailure.get() != null) throw deliveryFailure.get();
  }
  private boolean recoverPersistedMessage(String connectionId, String message) {
    try {
      var parsed = Json.MAPPER.readTree(message);
      if (parsed == null || !parsed.isObject()) return false;
      if ("connection_init".equals(parsed.path("type").asText()) && store.connection(connectionId).isPresent()) {
        messenger.send(connectionId, "{\"type\":\"connection_ack\"}"); return true;
      }
      if ("start".equals(parsed.path("type").asText())) {
        String id = parsed.path("id").asText();
        if (store.subscriptions(connectionId).stream().anyMatch(s -> s.operation().id().equals(id))) return true;
      }
      return false;
    } catch (java.io.IOException malformed) { return false; }
  }
  private static String startMessage(GraphQLWebSocketSession.Operation operation) {
    var payload = Json.MAPPER.createObjectNode().put("query", operation.query());
    payload.set("variables", Json.MAPPER.valueToTree(operation.variables()));
    if (operation.operationName() != null) payload.put("operationName", operation.operationName());
    var message = Json.MAPPER.createObjectNode().put("type", "start").put("id", operation.id()); message.set("payload", payload);
    return message.toString();
  }
  /** Called by the Cosmos state change-feed processor; failed delivery must fail the batch for retry. */
  public void notifyChange(ReadModelChange change) {
    for (var subscription : store.allSubscriptions()) {
      if (change.position() <= subscription.afterPosition() || !store.isActive(subscription)) continue;
      var connection = store.connection(subscription.connectionId());
      if (connection.isEmpty()) continue;
      var operation = subscription.operation();
      var context = new RequestContext("azure-sub:" + CosmosEventStore.hash(subscription.connectionId() + ":" + operation.id()),
          connection.get().user(), Map.of("transport", "azure-webpubsub", "connectionId", subscription.connectionId()));
      var result = graphql.executeSubscriptionChange(operation.query(), operation.variables(), operation.operationName(), context, change);
      if (!result.getErrors().isEmpty()) {
        messenger.send(subscription.connectionId(), Json.MAPPER.valueToTree(Map.of("id", operation.id(), "type", "error", "payload", Map.of("errors", result.toSpecification().get("errors")))).toString());
        store.removeSubscription(subscription.connectionId(), operation.id()); continue;
      }
      if (result.getData() instanceof Publisher<?> publisher) deliver(subscription, publisher);
    }
  }
  public void closeExpiredConnections() {
    for (var connection : store.expiredConnections()) {
      messenger.close(connection.id());
      store.removeConnection(connection.id());
    }
  }
  @SuppressWarnings("unchecked") private void deliver(AzureSubscriptionStore.Subscription subscription, Publisher<?> publisher) {
    var completed = new CompletableFuture<Void>();
    var upstream = new java.util.concurrent.atomic.AtomicReference<Subscription>();
    ((Publisher<ExecutionResult>) publisher).subscribe(new Subscriber<>() {
      public void onSubscribe(Subscription value) { upstream.set(value); value.request(1); }
      public void onNext(ExecutionResult result) {
        try {
          if (store.isActive(subscription)) messenger.send(subscription.connectionId(), Json.MAPPER.valueToTree(
              Map.of("id", subscription.operation().id(), "type", "data", "payload", result.toSpecification())).toString());
          upstream.get().request(1);
        } catch (RuntimeException failure) { upstream.get().cancel(); completed.completeExceptionally(failure); }
      }
      public void onError(Throwable failure) { completed.completeExceptionally(failure); }
      public void onComplete() { completed.complete(null); }
    });
    try { completed.get(30, TimeUnit.SECONDS); }
    catch (Exception failure) {
      if (upstream.get() != null) upstream.get().cancel();
      if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new IllegalStateException("Azure subscription delivery failed", failure);
    }
  }
  private static Response response(int status, String message) { return new Response(status, Map.of("Content-Type", "text/plain"), message); }
}
