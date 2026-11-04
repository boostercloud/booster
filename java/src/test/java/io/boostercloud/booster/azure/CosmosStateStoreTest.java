package io.boostercloud.booster.azure;

import io.boostercloud.booster.core.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CosmosStateStoreTest {
  @Test void compareAndSetAndTombstonesPreventAba() {
    var data = new TestCosmosDocuments(); var first = new CosmosStateStore(data); var second = new CosmosStateStore(data);
    var item = first.put("projections", "a", 0, Json.MAPPER.createObjectNode().put("total", 1));
    assertEquals(1, item.version());
    assertThrows(OptimisticConcurrencyException.class, () -> second.put("projections", "a", 0, Json.MAPPER.nullNode()));
    second.delete("projections", "a", 1);
    assertTrue(first.get("projections", "a").orElseThrow().deleted());
    assertThrows(OptimisticConcurrencyException.class, () -> first.put("projections", "a", 0, Json.MAPPER.nullNode()));
    assertEquals(3, first.put("projections", "a", 2, Json.MAPPER.valueToTree("restored")).version());
  }
  @Test void keyPaginationAndNamespaceIsolation() {
    var state = new CosmosStateStore(new TestCosmosDocuments());
    for (String key : new String[]{"c", "a", "b"}) state.put("one", key, 0, Json.MAPPER.valueToTree(key));
    state.put("two", "b", 0, Json.MAPPER.nullNode());
    assertEquals(java.util.List.of("a", "b"), state.scan("one", null, 2).stream().map(StateDocument::key).toList());
    assertEquals(java.util.List.of("c"), state.scan("one", "b", 2).stream().map(StateDocument::key).toList());
  }
  @Test void etagRaceFailsWithoutOverwritingAndDeletedNotificationsRetainPosition() {
    var data = new TestCosmosDocuments(); var state = new CosmosStateStore(data);
    state.put("jobs", "one", 0, Json.MAPPER.valueToTree("running")); data.rejectBatches = 1;
    assertThrows(OptimisticConcurrencyException.class, () -> state.put("jobs", "one", 1, Json.MAPPER.valueToTree("finished")));
    assertEquals("running", state.get("jobs", "one").orElseThrow().value().asText());
    var envelope = Json.MAPPER.createObjectNode().put("position", 42).put("schemaVersion", 2); envelope.putNull("state");
    var document = new StateDocument("readModels:View", "id", 2, envelope);
    var raw = Json.MAPPER.createObjectNode().put("namespace", document.namespace()); raw.set("state", Json.MAPPER.valueToTree(document));
    var change = AzureSubscriptionNotifier.toChange(raw).orElseThrow();
    assertEquals(42, change.position()); assertEquals("View", change.readModel()); assertTrue(change.value().isNull());
  }
}
