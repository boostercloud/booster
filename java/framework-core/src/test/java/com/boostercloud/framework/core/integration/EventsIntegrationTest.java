package com.boostercloud.framework.core.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.integration.app.AppState;
import com.boostercloud.framework.core.integration.app.common.CartItem;
import com.boostercloud.framework.core.integration.app.entities.Cart;
import com.boostercloud.framework.core.integration.app.notifications.CartAbandoned;
import com.boostercloud.framework.core.integration.inmemory.InMemoryRequests.RocketRequest;
import com.boostercloud.framework.core.integration.inmemory.InMemoryRequests.ScheduledTrigger;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventDeleteParameters;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.EventParametersFilterByEntity;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.framework.types.errors.NotFoundError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The event side beyond commands: the events API ({@code eventsByEntity}/{@code eventsByType}, {@code Booster.events}),
 * notifications and their handlers, scheduled commands, touching entities, deleting events (un-projecting their read
 * models) and rocket functions.
 */
class EventsIntegrationTest extends IntegrationTestSupport {

  private void changeCart(String cartId, String productId, int quantity, String token) {
    data(graphQL("mutation { ChangeCartItem(input: {cartId: \"" + cartId + "\", productId: \"" + productId
        + "\", quantity: " + quantity + "}) }", token));
  }

  @Test
  void eventsByEntityReturnsTheEventsNewestFirstWithTheirUserAndValue() {
    changeCart("cart-1", "p1", 2, TestTokens.customer());
    changeCart("cart-2", "p1", 1, null);

    Map<String, Object> result = graphQL("""
        { eventsByEntity(entity: Cart, entityID: "cart-1") { type entity entityID requestID createdAt value
            user { id username roles } deletedAt } }""");

    List<Map<String, Object>> events = listAt(data(result), "eventsByEntity");
    assertThat(events).extracting(event -> event.get("type")).containsExactly("CartChecked", "CartItemChanged");
    Map<String, Object> changed = events.get(1);
    assertThat(changed).containsEntry("entity", "Cart").containsEntry("entityID", "cart-1")
        .containsEntry("value", Map.of("cartId", "cart-1", "productId", "p1", "quantity", 2))
        .containsEntry("user", map("id", "customer-1", "username", "customer@store.test", "roles", List.of("Customer")))
        .containsEntry("deletedAt", null);
    assertThat((String) changed.get("createdAt")).isLessThan((String) events.get(0).get("createdAt"));
    assertThat(events.get(0).get("requestID")).isEqualTo(changed.get("requestID"));
  }

  @Test
  void eventsByTypeFiltersByTimeAndLimit() {
    changeCart("cart-1", "p1", 1, null);
    changeCart("cart-2", "p1", 1, null);
    changeCart("cart-3", "p1", 1, null);
    List<EventEnvelope> stored = provider.events().events().stream()
        .filter(event -> event.typeName().equals("CartItemChanged")).toList();

    Map<String, Object> all = graphQL("{ eventsByType(type: CartItemChanged) { entityID } }");
    assertThat(listAt(data(all), "eventsByType")).extracting(event -> event.get("entityID"))
        .containsExactly("cart-3", "cart-2", "cart-1");
    Map<String, Object> window = graphQL("query ($from: String, $to: String) { eventsByType(type: CartItemChanged, "
        + "from: $from, to: $to, limit: 1) { entityID } }",
        map("from", stored.get(0).createdAt(), "to", stored.get(1).createdAt()), null);
    assertThat(listAt(data(window), "eventsByType")).extracting(event -> event.get("entityID"))
        .containsExactly("cart-2");
  }

  @Test
  void eventsCanBeSearchedFromCodeAsInstances() {
    changeCart("cart-1", "p1", 3, null);

    List<EventSearchResponse> events = Booster.events(new EventParametersFilterByEntity("Cart", "cart-1", null, null,
        null));

    assertThat(events).extracting(EventSearchResponse::value).containsExactly(
        new com.boostercloud.framework.core.integration.app.events.CartChecked(UUID.of("cart-1")),
        new com.boostercloud.framework.core.integration.app.events.CartItemChanged(UUID.of("cart-1"), UUID.of("p1"), 3));
  }

  @Test
  void anUnknownEntityIsAnError() {
    assertThat(errorMessage(graphQL("{ eventsByEntity(entity: Nope) { type } }")))
        .contains("Nope");
  }

  @Test
  void notificationsAreDispatchedToTheirHandlersWithoutSnapshots() {
    data(graphQL("mutation { AbandonCart(input: {cartId: \"cart-7\"}) }", TestTokens.customer()));

    assertThat(AppState.ABANDONED_CARTS).containsExactly(UUID.of("cart-7"));
    assertThat(AppState.ABANDONED_BY).containsExactly("customer@store.test");
    assertThat(AppState.EVENTS).containsExactly("CartAbandoned");
    assertThat(provider.events().events()).singleElement().satisfies(event -> {
      // As in TypeScript, only the framework's own events are stored with another super kind than "domain"
      assertThat(event.superKind()).isEqualTo(SuperKindType.DOMAIN);
      assertThat(event.entityTypeName()).isEqualTo("cart-abandoned");
      assertThat(event.entityID()).isEqualTo(UUID.of("cart-7"));
      assertThat(event.typeName()).isEqualTo(CartAbandoned.class.getSimpleName());
    });
    assertThat(provider.events().snapshots()).isEmpty();
  }

  @Test
  void aScheduledCommandReadsReadModelsAndRegistersEvents() {
    changeCart("cart-1", "p1", 2, null);
    changeCart("cart-2", "p1", 2, null);
    changeCart("cart-2", "p1", 0, null);

    app.boosterTriggerScheduledCommands(new ScheduledTrigger("FlagEmptyCarts"));

    assertThat(AppState.ABANDONED_CARTS).containsExactly(UUID.of("cart-2"));
    assertThat(AppState.ABANDONED_BY).containsExactly("nobody");
  }

  @Test
  void anUnknownScheduledCommandFails() {
    assertThatThrownBy(() -> app.boosterTriggerScheduledCommands(new ScheduledTrigger("Nope")))
        .isInstanceOf(NotFoundError.class).hasMessage("Could not find a proper handler for Nope");
  }

  @Test
  void touchingAnEntityProjectsItAgain() {
    changeCart("cart-1", "p1", 2, null);
    Map<String, Object> document = provider.readModels().documents("CartReadModel").getFirst();
    provider.readModels().delete(config, "CartReadModel", document);
    assertThat(data(graphQL("{ CartReadModel(id: \"cart-1\") { id } }"))).isEqualTo(map("CartReadModel", null));

    data(graphQL("mutation { TouchCart(input: {cartId: \"cart-1\"}) }", TestTokens.admin()));

    assertThat(data(graphQL("{ CartReadModel(id: \"cart-1\") { id checks totalQuantity } }")))
        .isEqualTo(Map.of("CartReadModel", map("id", "cart-1", "checks", 1.0, "totalQuantity", 2.0)));
    assertThat(provider.events().events()).last().satisfies(event -> {
      assertThat(event.typeName()).isEqualTo("BoosterEntityTouched");
      assertThat(event.superKind()).isEqualTo(SuperKindType.BOOSTER);
      assertThat(event.entityTypeName()).isEqualTo("Cart");
    });
    assertThat(Booster.entity(Cart.class, UUID.of("cart-1")))
        .contains(new Cart(UUID.of("cart-1"), List.of(new CartItem(UUID.of("p1"), 2)), 1));
  }

  @Test
  void deletingAnEventUnprojectsItsReadModelsAndRemovesItsSnapshot() {
    changeCart("cart-1", "p1", 2, null);
    EventEnvelope checked = provider.events().events().getLast();
    assertThat(checked.typeName()).isEqualTo("CartChecked");

    boolean deleted = Booster.deleteEvent(new EventDeleteParameters("Cart", UUID.of("cart-1"), checked.createdAt()));

    assertThat(deleted).isTrue();
    assertThat(provider.readModels().documents("CartReadModel")).isEmpty();
    assertThat(provider.events().snapshots()).extracting(snapshot -> snapshot.snapshottedEventCreatedAt())
        .doesNotContain(checked.createdAt());
    assertThat(provider.events().events().getLast()).satisfies(event -> {
      assertThat(event.deletedAt()).isNotNull();
      assertThat(event.value()).isEqualTo(Map.of());
    });
    assertThat(Booster.entity(Cart.class, UUID.of("cart-1")))
        .contains(new Cart(UUID.of("cart-1"), List.of(new CartItem(UUID.of("p1"), 2)), 0));
    assertThat(listAt(data(graphQL("{ eventsByEntity(entity: Cart, entityID: \"cart-1\") { type deletedAt } }")),
        "eventsByEntity")).first().satisfies(event -> assertThat(event.get("deletedAt")).isNotNull());

    changeCart("cart-1", "p2", 1, null);
    assertThat(data(graphQL("{ CartReadModel(id: \"cart-1\") { checks totalQuantity } }")))
        .isEqualTo(Map.of("CartReadModel", map("checks", 1.0, "totalQuantity", 3.0)));
  }

  @Test
  void deletingAnUnknownEventDeletesNothing() {
    assertThat(Booster.deleteEvent(new EventDeleteParameters("Cart", UUID.of("nope"), "2020-01-01T00:00:00.000Z")))
        .isFalse();
  }

  @Test
  void entityIdsArePaginated() {
    for (int i = 0; i < 5; i++) {
      changeCart("cart-" + i, "p", 1, null);
    }

    var first = Booster.entitiesIDs("Cart", 3, null);
    var second = Booster.entitiesIDs("Cart", 3, first.cursor());

    assertThat(first.items()).hasSize(3);
    assertThat(second.items()).hasSize(2);
    assertThat(List.of(first.items(), second.items()).stream().flatMap(List::stream).map(item -> item.entityID().value()))
        .containsExactlyInAnyOrder("cart-0", "cart-1", "cart-2", "cart-3", "cart-4");
  }

  @Test
  void eventsCanGoThroughAnEventStream() {
    provider.events().autoDispatch(false);
    changeCart("cart-1", "p1", 2, null);
    changeCart("cart-2", "p1", 3, null);
    List<EventEnvelope> stored = provider.events().events();

    app.boosterProduceEventStream(stored);
    List<Object> messages = provider.events().produced().stream().<Object>map(BoosterJson::toRawMap).toList();
    assertThat(provider.events().produced()).extracting(EventEnvelope::entityID)
        .containsExactlyInAnyOrder(UUID.of("cart-1"), UUID.of("cart-2"));
    assertThat(provider.readModels().documents("CartReadModel")).isEmpty();

    app.boosterConsumeEventStream(messages);
    app.boosterConsumeEventStream(messages);

    assertThat(AppState.EVENTS).containsExactlyInAnyOrder("CartItemChanged", "CartItemChanged");
    assertThat(data(graphQL("{ CartReadModels(filter: {}) { id totalQuantity } }")).get("CartReadModels"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .containsExactlyInAnyOrder(map("id", "cart-1", "totalQuantity", 2.0), map("id", "cart-2", "totalQuantity", 3.0));
  }

  @Test
  void rocketFunctionsAreDispatchedById() {
    config.registerRocketFunction("echo", (rocketConfig, request) -> Map.of("echo", ((RocketRequest) request).body()));

    assertThat(app.boosterRocketDispatcher(new RocketRequest("echo", "hello"))).isEqualTo(Map.of("echo", "hello"));
  }
}
