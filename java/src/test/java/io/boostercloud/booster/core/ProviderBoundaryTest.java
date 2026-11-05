package io.boostercloud.booster.core;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ProviderBoundaryTest {
  record Item(String id, int amount) {}
  record Changed(String id, int amount) {}
  private static RequestContext request(String id) { return new RequestContext(id, UserContext.anonymous(), Map.of()); }
  private Booster.Builder builder(EventStore store) {
    return Booster.builder(store).onError(error -> {}).event(Changed.class, Changed::id, Item.class,
        (event, previous) -> new Item(event.id(), event.amount()));
  }
  private InMemoryEventStore unknownEvent() {
    var store = new InMemoryEventStore(); var stream = new StreamId("Item", "a");
    var event = new PendingEvent("unknown", stream, "Unregistered", 1, Json.MAPPER.createObjectNode().put("id", "a"), Instant.now(), UserContext.anonymous(), Map.of());
    store.commit("import", "import", Map.of(stream, 0L), List.of(event), Json.MAPPER.nullNode()); return store;
  }
  @Test void invalidEventBoundaryDefaultsToFailureAndSupportsReplacementOrExplicitSkip() {
    assertThrows(BoosterException.class, () -> builder(unknownEvent()).build());
    var replacement = assertThrows(BoosterException.class, () -> builder(unknownEvent())
        .onError(FrameworkError.Stage.EVENT, failure -> new BoosterException(BoosterException.Code.VALIDATION, "unknown event replacement")).build());
    assertEquals("unknown event replacement", replacement.getMessage());
    var dispatched = new AtomicInteger();
    try (var runtime = builder(unknownEvent()).onError(FrameworkError.Stage.EVENT, failure -> null)
        .globalEventHandler("must-not-see-skipped-event", (event, register) -> dispatched.incrementAndGet()).build()) {
      assertEquals(1, runtime.position()); assertNull(runtime.entity(Item.class, "a")); assertEquals(0, dispatched.get());
      runtime.rebuild(); assertEquals(1, runtime.position()); assertEquals(0, dispatched.get());
    }
  }
  @Test void eventErrorSuppressionDoesNotHideFutureSchemas() {
    var store = new InMemoryEventStore(); var stream = new StreamId("Item", "a");
    store.commit("future", "future", Map.of(stream, 0L), List.of(new PendingEvent("future", stream, "Changed", 2,
        Json.MAPPER.valueToTree(new Changed("a", 1)), Instant.now(), UserContext.anonymous(), Map.of())), Json.MAPPER.nullNode());
    assertThrows(BoosterException.class, () -> builder(store).onError(FrameworkError.Stage.EVENT, failure -> null).build());
  }
  @Test void namedRawFunctionsPreserveProviderValuesContextAndExplicitAuthorization() {
    var raw = new byte[] {1, 2, 3}; var calls = new AtomicInteger();
    var configured = builder(new InMemoryEventStore())
        .function("binary", AccessPolicy.publicAccess(), (runtime, request, context) -> {
          calls.incrementAndGet(); assertSame(raw, request); context.response().set("X-Function", "yes"); return raw;
        }).function("private", (runtime, request, context) -> { fail("default policy must deny"); return null; });
    assertThrows(IllegalArgumentException.class, () -> configured.function("binary", (runtime, request, context) -> null));
    try (var runtime = configured.build()) {
      var context = request("function");
      assertSame(raw, runtime.executeFunction("binary", raw, context));
      assertEquals("yes", context.response().snapshot().get("X-Function"));
      assertSame(raw, runtime.executeFunction("binary", raw, context));
      assertEquals(2, calls.get()); assertTrue(runtime.eventStore().findCommand("function").isEmpty());
      assertEquals(List.of("binary", "private"), runtime.functions());
      assertThrows(BoosterException.class, () -> runtime.executeFunction("private", raw, context));
      assertThrows(BoosterException.class, () -> runtime.executeFunction("missing", raw, context));
    }
  }
}
