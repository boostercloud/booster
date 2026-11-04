package io.boostercloud.booster.integration;

import io.boostercloud.booster.core.*;
import io.boostercloud.booster.local.LocalProvider;
import io.boostercloud.booster.migration.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import static io.boostercloud.booster.integration.MigrationProjectionFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class LocalProjectionMigrationIT {
  @TempDir Path directory;
  private static final UserContext OPERATOR = new UserContext("local-operator", Set.of("operator", "reader"), Map.of("tenant", "sample"));
  private static RequestContext request(String id) { return new RequestContext(id, OPERATOR, Map.of()); }

  private static Booster application(LocalProvider provider) {
    var builder = Booster.builder(provider.eventStore()).stateStore(provider.stateStore()).onError(error -> {});
    MigrationProjectionFixture.configure(builder);
    return builder.build();
  }

  @Test void interruptedMigrationResumesWithJoinedProjectionsAndProviderReopen() throws Exception {
    var interrupted = new AtomicBoolean(true);
    var definitions = migrations(interrupted);
    long partialPosition;
    try (var provider = new LocalProvider(directory); var booster = application(provider)) {
      place(booster, "order-1", "customer-old", "Ada", 1000);
      place(booster, "order-2", "customer-old", "Ada", 2000);
      place(booster, "order-3", "customer-other", "Grace", 3000);
      assertEquals("Ada", booster.getReadModel("OrderView", "order-1", request("read-initial")).path("customerName").asText());
      assertEquals("Ada", booster.getReadModel("OrderView", "order-2", request("read-initial-2")).path("customerName").asText());
      assertThrows(BoosterException.class, () -> booster.getReadModel("OrderView", "order-1", RequestContext.anonymous()));
      var engine = new DataMigrations(booster, definitions, OPERATOR);
      assertThrows(BoosterException.class, engine::run);
      assertEquals(DataMigrationStatus.FAILED, engine.status("customer-v2").orElseThrow().lifecycle().status());
      assertTrue(engine.status("customer-v2").orElseThrow().checkpoint().path("firstOrderDone").asBoolean());
      assertTrue(engine.status("reproject-customer-v2").isEmpty());
      assertEquals("customer-new", booster.getReadModel("OrderView", "order-1", request("read-partial")).path("customerId").asText());
      assertEquals("customer-old", booster.getReadModel("OrderView", "order-2", request("read-partial-2")).path("customerId").asText());
      partialPosition = booster.position();
    }
    List<com.fasterxml.jackson.databind.JsonNode> expectedModels;
    long completedPosition;
    try (var provider = new LocalProvider(directory); var booster = application(provider)) {
      assertEquals(partialPosition, booster.position());
      // A command receipt survives provider recreation even after migration changed its entity.
      place(booster, "order-1", "customer-old", "Ada", 1000);
      assertEquals(partialPosition, booster.position());
      var engine = new DataMigrations(booster, definitions, OPERATOR);
      assertTrue(engine.resume());
      assertEquals(List.of(DataMigrationStatus.FINISHED, DataMigrationStatus.FINISHED),
          engine.statuses().stream().map(status -> status.lifecycle().status()).toList());
      assertNull(booster.entity(Customer.class, "customer-old"));
      assertEquals("gold", booster.entity(CustomerV2.class, "customer-new").segment());
      for (String id : List.of("order-1", "order-2")) {
        var model = booster.getReadModel("OrderView", id, request("read-complete-" + id));
        assertEquals("customer-new", model.path("customerId").asText());
        assertEquals("Ada Lovelace", model.path("customerName").asText());
        assertEquals("gold", model.path("segment").asText());
      }
      assertEquals("Grace", booster.getReadModel("OrderView", "order-3", request("read-other")).path("customerName").asText());
      var filter = Json.MAPPER.createObjectNode().set("customerId", Json.MAPPER.createObjectNode().put("eq", "customer-new"));
      var first = booster.listReadModels("OrderView", new QueryOptions(filter,
          List.of(new Sort("totalCents", Sort.Direction.ASC)), 1, null), request("page-1"));
      var second = booster.listReadModels("OrderView", new QueryOptions(filter,
          List.of(new Sort("totalCents", Sort.Direction.ASC)), 1, first.cursor()), request("page-2"));
      assertEquals("order-1", first.items().getFirst().path("id").asText());
      assertEquals("order-2", second.items().getFirst().path("id").asText());
      assertEquals(1000, first.items().getFirst().path("totalCents").asLong());
      assertEquals(2000, second.items().getFirst().path("totalCents").asLong());
      assertEquals(2, booster.history(Order.class, "order-1").size(), "Only one durable relink per order");
      assertEquals(2, booster.history(Order.class, "order-2").size());
      assertEquals("CustomerNamed", booster.history(Customer.class, "customer-old").getFirst().event().type());
      expectedModels = booster.listReadModels("OrderView", QueryOptions.defaults(), request("all")).items();
      completedPosition = booster.position();
    }
    try (var provider = new LocalProvider(directory); var booster = application(provider)) {
      var engine = new DataMigrations(booster, definitions, OPERATOR);
      assertFalse(engine.run());
      assertEquals(completedPosition, booster.position());
      assertEquals(expectedModels, booster.listReadModels("OrderView", QueryOptions.defaults(), request("after-reopen")).items());
      booster.rebuild();
      assertEquals(expectedModels, booster.listReadModels("OrderView", QueryOptions.defaults(), request("after-replay")).items());
      assertEquals(completedPosition, booster.position());
      var lifecycle = booster.history(BoosterDataMigrationEntity.class, "customer-v2");
      assertEquals(2, lifecycle.stream().filter(event -> event.event().type().equals("BoosterDataMigrationStarted")).count());
      assertEquals(1, lifecycle.stream().filter(event -> event.event().type().equals("BoosterDataMigrationFinished")).count());
    }
  }

  private static void place(Booster booster, String id, String customerId, String name, long amount) {
    booster.executeCommand("PlaceOrder", Json.MAPPER.valueToTree(new PlaceOrder(id, customerId, name, amount)), request("place-" + id));
  }
}
