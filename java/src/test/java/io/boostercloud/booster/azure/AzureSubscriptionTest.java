package io.boostercloud.booster.azure;

import io.boostercloud.booster.core.*;
import io.boostercloud.booster.example.ShoppingCartApplication;
import io.boostercloud.booster.transport.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AzureSubscriptionTest {
  static class MutableClock extends Clock {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    public ZoneId getZone() { return ZoneOffset.UTC; } public Clock withZone(ZoneId zone) { return this; } public Instant instant() { return now; }
  }
  static class Messages implements AzureWebPubSubHandler.Messenger {
    final List<String> messages = new ArrayList<>(), closed = new ArrayList<>();
    public void send(String connectionId, String message) { messages.add(message); }
    public void close(String connectionId) { closed.add(connectionId); }
  }
  static Map<String, String> headers(String type) { return Map.of("ce-specversion", "1.0", "ce-hub", "booster", "ce-connectionId", "c", "ce-type", type, "ce-id", UUID.randomUUID().toString()); }
  @Test void persistedIdentityOperationsAndTtl() {
    var data = new TestCosmosDocuments(); var clock = new MutableClock(); var store = new AzureSubscriptionStore(data, clock);
    store.saveConnection(new AzureSubscriptionStore.Connection("c", new UserContext("alice", Set.of("reader"), Map.of("custom:role", "reader")), clock.now.plusSeconds(60)));
    var operation = new GraphQLWebSocketSession.Operation("op", "subscription { View(id:\"x\") { id } }", Map.of(), null);
    store.saveSubscription(new AzureSubscriptionStore.Subscription("c", operation, 5, clock.now.plusSeconds(60)));
    var restarted = new AzureSubscriptionStore(data, clock);
    assertEquals("alice", restarted.connection("c").orElseThrow().user().subject());
    assertEquals(operation, restarted.subscriptions("c").getFirst().operation());
    assertTrue(data.values.values().stream().filter(d -> d.value().path("kind").asText().equals("connection")).allMatch(d -> d.value().path("ttl").asInt() == 360));
    clock.now = clock.now.plusSeconds(61);
    assertTrue(restarted.connection("c").isEmpty()); assertTrue(restarted.subscriptions("c").isEmpty());
    assertThrows(BoosterException.class, () -> restarted.saveSubscription(new AzureSubscriptionStore.Subscription("c", operation, 5, clock.now.plusSeconds(60))));
  }
  @Test void connectionCleanupImmediatelyDisablesOperations() {
    var data = new TestCosmosDocuments(); var clock = new MutableClock(); var store = new AzureSubscriptionStore(data, clock);
    store.saveConnection(new AzureSubscriptionStore.Connection("c", UserContext.anonymous(), clock.now.plusSeconds(60)));
    for (String id : List.of("one", "two")) store.saveSubscription(new AzureSubscriptionStore.Subscription("c", new GraphQLWebSocketSession.Operation(id, "subscription{x}", Map.of(), null), 0, clock.now.plusSeconds(60)));
    store.removeConnection("c");
    assertTrue(store.connection("c").isEmpty()); assertTrue(store.allSubscriptions().isEmpty());
  }
  @Test void protocolRestoresAcrossRequestsAndNotificationsUseSelectedFields() {
    var clock = new MutableClock(); var data = new TestCosmosDocuments(); var store = new AzureSubscriptionStore(data, clock); var messages = new Messages();
    try (var runtime = ShoppingCartApplication.create(new InMemoryEventStore())) {
      var graphql = new BoosterGraphQL(runtime);
      var first = new AzureWebPubSubHandler(runtime, graphql, AuthenticationProvider.anonymous(), store, messages, clock);
      assertEquals(200, first.handleCloudEvent(headers("azure.webpubsub.sys.connect"), "").status());
      first.handleCloudEvent(headers("azure.webpubsub.user.message"), "{\"type\":\"connection_init\"}");
      assertTrue(messages.messages.getFirst().contains("connection_ack"));
      // Every event is intentionally handled by a NEW object to model arbitrary replica routing.
      var restarted = new AzureWebPubSubHandler(runtime, graphql, AuthenticationProvider.anonymous(), new AzureSubscriptionStore(data, clock), messages, clock);
      restarted.handleCloudEvent(headers("azure.webpubsub.user.message"), "{\"type\":\"start\",\"id\":\"view\",\"payload\":{\"query\":\"subscription { CartView(id: \\\"a\\\") { id totalCents } }\"}}");
      assertEquals(1, store.subscriptions("c").size());
      runtime.executeCommand("OpenCart", Json.MAPPER.valueToTree(new ShoppingCartApplication.OpenCart("a")), RequestContext.anonymous());
      var value = runtime.getReadModel("CartView", "a", RequestContext.anonymous());
      restarted.notifyChange(new ReadModelChange("CartView", "a", value, runtime.position()));
      var notification = Json.MAPPER.readTree(messages.messages.getLast());
      assertEquals("data", notification.path("type").asText()); assertEquals("a", notification.path("payload").path("data").path("CartView").path("id").asText());
      assertFalse(notification.path("payload").path("data").path("CartView").has("checkedOut"));
      restarted.handleCloudEvent(headers("azure.webpubsub.user.message"), "{\"type\":\"stop\",\"id\":\"view\"}");
      assertTrue(store.subscriptions("c").isEmpty());
      restarted.handleCloudEvent(headers("azure.webpubsub.sys.disconnect"), ""); assertTrue(store.connection("c").isEmpty());
    } catch (java.io.IOException impossible) { throw new AssertionError(impossible); }
  }
  @Test void rejectedAuthenticationCannotCreateDurableConnection() {
    var clock = new MutableClock(); var store = new AzureSubscriptionStore(new TestCosmosDocuments(), clock); var messages = new Messages();
    try (var runtime = ShoppingCartApplication.create(new InMemoryEventStore())) {
      var handler = new AzureWebPubSubHandler(runtime, new BoosterGraphQL(runtime), AuthenticationProvider.anonymous(), store, messages, clock);
      handler.handleCloudEvent(headers("azure.webpubsub.user.message"), "{\"type\":\"connection_init\",\"payload\":{\"Authorization\":\"Bearer forged\"}}");
      assertTrue(store.connection("c").isEmpty()); assertEquals(List.of("c"), messages.closed);
      assertTrue(messages.messages.getFirst().contains("connection_error"));
      assertEquals(400, handler.handleCloudEvent(Map.of(), "{}").status());
    }
  }
  @Test void retriedCloudEventDoesNotReinitializeOrCloseConnection() {
    var clock = new MutableClock(); var store = new AzureSubscriptionStore(new TestCosmosDocuments(), clock); var messages = new Messages();
    try (var runtime = ShoppingCartApplication.create(new InMemoryEventStore())) {
      var handler = new AzureWebPubSubHandler(runtime, new BoosterGraphQL(runtime), AuthenticationProvider.anonymous(), store, messages, clock);
      var headers = headers("azure.webpubsub.user.message");
      String initialize = "{\"type\":\"connection_init\"}";
      handler.handleCloudEvent(headers, initialize); handler.handleCloudEvent(headers, initialize);
      assertEquals(1, messages.messages.size()); assertTrue(messages.closed.isEmpty()); assertTrue(store.connection("c").isPresent());
      assertThrows(BoosterException.class, () -> handler.handleCloudEvent(headers, "{\"type\":\"connection_terminate\"}"));
    }
  }
  @Test void interruptedCloudEventCanResumeWithPersistedConnection() {
    var clock = new MutableClock(); var store = new AzureSubscriptionStore(new TestCosmosDocuments(), clock); var messages = new Messages();
    String body = "{\"type\":\"connection_init\"}"; var headers = headers("azure.webpubsub.user.message");
    var ticket = store.beginDelivery("c", headers.get("ce-id"), CosmosEventStore.hash(body));
    store.saveConnection(new AzureSubscriptionStore.Connection("c", UserContext.anonymous(), clock.now.plusSeconds(600)));
    // A process dies after identity was persisted but before its receipt was completed.
    clock.now = clock.now.plusSeconds(121);
    try (var runtime = ShoppingCartApplication.create(new InMemoryEventStore())) {
      var handler = new AzureWebPubSubHandler(runtime, new BoosterGraphQL(runtime), AuthenticationProvider.anonymous(), store, messages, clock);
      handler.handleCloudEvent(headers, body);
      assertTrue(messages.messages.getFirst().contains("connection_ack")); assertTrue(messages.closed.isEmpty());
      assertThrows(BoosterException.class, () -> store.finishDelivery(ticket, true));
    }
  }
  @Test void expiryClosesRemoteConnectionBeforeCosmosTtlCleanup() {
    var clock = new MutableClock(); var store = new AzureSubscriptionStore(new TestCosmosDocuments(), clock); var messages = new Messages();
    store.saveConnection(new AzureSubscriptionStore.Connection("c", UserContext.anonymous(), clock.now.plusSeconds(60)));
    clock.now = clock.now.plusSeconds(61);
    try (var runtime = ShoppingCartApplication.create(new InMemoryEventStore())) {
      var handler = new AzureWebPubSubHandler(runtime, new BoosterGraphQL(runtime), AuthenticationProvider.anonymous(), store, messages, clock);
      handler.closeExpiredConnections();
      assertEquals(List.of("c"), messages.closed); assertTrue(store.expiredConnections().isEmpty());
      handler.closeExpiredConnections(); assertEquals(1, messages.closed.size());
    }
  }
  @Test void failedMutationReplyRetriesWithSameReceiptAndKeepsConnection() {
    var clock = new MutableClock(); var store = new AzureSubscriptionStore(new TestCosmosDocuments(), clock);
    var fail = new java.util.concurrent.atomic.AtomicBoolean(); var messages = new Messages() {
      @Override public void send(String connectionId, String message) {
        if (fail.getAndSet(false)) throw new IllegalStateException("Simulated Web PubSub outage");
        super.send(connectionId, message);
      }
    };
    try (var runtime = ShoppingCartApplication.create(new InMemoryEventStore())) {
      var handler = new AzureWebPubSubHandler(runtime, new BoosterGraphQL(runtime), AuthenticationProvider.anonymous(), store, messages, clock);
      handler.handleCloudEvent(headers("azure.webpubsub.user.message"), "{\"type\":\"connection_init\"}");
      var headers = headers("azure.webpubsub.user.message");
      String body = "{\"type\":\"start\",\"id\":\"command\",\"payload\":{\"query\":\"mutation { OpenCart(input:{cartId:\\\"a\\\"}) { id } }\"}}";
      fail.set(true);
      assertThrows(IllegalStateException.class, () -> handler.handleCloudEvent(headers, body));
      assertEquals(1, runtime.position()); assertTrue(store.connection("c").isPresent()); assertTrue(messages.closed.isEmpty());
      handler.handleCloudEvent(headers, body);
      assertEquals(1, runtime.position()); assertTrue(messages.messages.stream().anyMatch(m -> m.contains("\"data\"") && m.contains("\"OpenCart\"")));
    }
  }
}
