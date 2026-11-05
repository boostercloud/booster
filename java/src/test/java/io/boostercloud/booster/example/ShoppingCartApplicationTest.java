package io.boostercloud.booster.example;

import static org.junit.jupiter.api.Assertions.*;

import io.boostercloud.booster.core.Booster;
import io.boostercloud.booster.core.Json;
import io.boostercloud.booster.core.RequestContext;
import io.boostercloud.booster.store.JdbcEventStore;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShoppingCartApplicationTest {
  @TempDir Path temporary;

  private static void command(Booster app, Object command) {
    app.executeCommand(command.getClass().getSimpleName(), Json.MAPPER.valueToTree(command), RequestContext.anonymous());
  }

  @Test void cartLifecycleUpdatesItsReadModel() {
    try (var app = ShoppingCartApplication.create(JdbcEventStore.inMemory())) {
      command(app, new ShoppingCartApplication.OpenCart("cart"));
      command(app, new ShoppingCartApplication.AddItem("cart", "coffee", 2, 1299));
      command(app, new ShoppingCartApplication.AddItem("cart", "coffee", 1, 1299));
      command(app, new ShoppingCartApplication.AddItem("cart", "tea", 1, 500));
      command(app, new ShoppingCartApplication.RemoveItem("cart", "coffee", 1));
      var view = app.getReadModel("CartView", "cart", RequestContext.anonymous());
      assertEquals(3, view.path("totalQuantity").asInt());
      assertEquals(3098, view.path("totalCents").asLong());
      command(app, new ShoppingCartApplication.CheckoutCart("cart"));
      assertTrue(app.getReadModel("CartView", "cart", RequestContext.anonymous()).path("checkedOut").asBoolean());
      long committed = app.eventStore().lastPosition();
      assertThrows(RuntimeException.class, () -> command(app, new ShoppingCartApplication.AddItem("cart", "tea", 1, 500)));
      assertEquals(committed, app.eventStore().lastPosition());
    }
  }

  @Test void journalRestoresProjectionAcrossRestartAndReplay() {
    Path database = temporary.resolve("cart-events");
    try (var app = ShoppingCartApplication.create(new JdbcEventStore(database))) {
      command(app, new ShoppingCartApplication.OpenCart("persisted"));
      command(app, new ShoppingCartApplication.AddItem("persisted", "book", 2, 1999));
    }
    try (var app = ShoppingCartApplication.create(new JdbcEventStore(database))) {
      var before = app.getReadModel("CartView", "persisted", RequestContext.anonymous());
      assertEquals(3998, before.path("totalCents").asLong());
      app.rebuild();
      assertEquals(before, app.getReadModel("CartView", "persisted", RequestContext.anonymous()));
      command(app, new ShoppingCartApplication.CheckoutCart("persisted"));
      assertEquals(3, app.eventStore().lastPosition());
    }
  }

  @Test void duplicateDeliveryDoesNotDoubleCountItems() {
    try (var app = ShoppingCartApplication.create(JdbcEventStore.inMemory())) {
      command(app, new ShoppingCartApplication.OpenCart("cart"));
      var context = new RequestContext("same-request", null, Map.of());
      var input = Json.MAPPER.valueToTree(new ShoppingCartApplication.AddItem("cart", "book", 2, 500));
      app.executeCommand("AddItem", input, context);
      app.executeCommand("AddItem", input, context);
      assertEquals(2, app.getReadModel("CartView", "cart", RequestContext.anonymous()).path("totalQuantity").asInt());
      assertEquals(2, app.eventStore().lastPosition());
    }
  }

  @Test void invalidBusinessOperationsLeaveJournalUnchanged() {
    try (var app = ShoppingCartApplication.create(JdbcEventStore.inMemory())) {
      command(app, new ShoppingCartApplication.OpenCart("cart"));
      assertThrows(RuntimeException.class, () -> command(app, new ShoppingCartApplication.OpenCart("cart")));
      assertThrows(RuntimeException.class, () -> command(app, new ShoppingCartApplication.CheckoutCart("cart")));
      assertThrows(RuntimeException.class, () -> command(app, new ShoppingCartApplication.AddItem("cart", "book", -1, 500)));
      assertThrows(RuntimeException.class, () -> command(app, new ShoppingCartApplication.AddItem("cart", "book", 1, -1)));
      assertThrows(RuntimeException.class, () -> command(app, new ShoppingCartApplication.RemoveItem("cart", "missing", 1)));
      assertEquals(1, app.eventStore().lastPosition());
      command(app, new ShoppingCartApplication.AddItem("cart", "book", 1, 500));
      assertThrows(RuntimeException.class, () -> command(app, new ShoppingCartApplication.AddItem("cart", "book", 1, 999)));
      assertThrows(RuntimeException.class, () -> command(app, new ShoppingCartApplication.RemoveItem("cart", "book", 2)));
      assertEquals(2, app.eventStore().lastPosition());
      command(app, new ShoppingCartApplication.RemoveItem("cart", "book", 1));
      assertEquals(0, app.getReadModel("CartView", "cart", RequestContext.anonymous()).path("totalQuantity").asInt());
    }
  }
}
