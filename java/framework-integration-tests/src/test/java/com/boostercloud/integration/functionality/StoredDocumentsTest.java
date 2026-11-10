package com.boostercloud.integration.functionality;

import static com.boostercloud.integration.functionality.StoredDocuments.assertDeepContains;
import static com.boostercloud.integration.functionality.StoredDocuments.assertEventStoreKey;
import static com.boostercloud.integration.functionality.StoredDocuments.at;
import static com.boostercloud.integration.functionality.StoredDocuments.expectedEventDocument;
import static com.boostercloud.integration.functionality.StoredDocuments.object;
import static com.boostercloud.integration.functionality.StoredDocuments.objectAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.integration.helper.AppHelper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The raw-document helpers of the functionality suites. */
class StoredDocumentsTest {

  private static final List<Map<String, Object>> EVENTS = List.of(object(
      "entityTypeName", "Cart", "entityID", "c1", "kind", "event",
      "value", object("cartId", "c1", "items", List.of(object("productId", "p1")))));

  @AfterEach
  void tearDown() {
    EnvironmentVariables.unset(AppHelper.TESTED_PROVIDER);
  }

  @Test
  void navigatesParsedJsonLikeOptionalChaining() {
    assertThat(at(EVENTS, "[0].value.cartId")).isEqualTo("c1");
    assertThat(at(EVENTS, "[0].value.items[0].productId")).isEqualTo("p1");
    assertThat(objectAt(EVENTS, "[0].value")).containsEntry("cartId", "c1");
    assertThat(at(EVENTS, "[1].value")).isNull();
    assertThat(at(EVENTS, "[0].missing.deeper")).isNull();
    assertThat(at(null, "[0]")).isNull();
    assertThat(objectAt(EVENTS, "[0].kind")).isNull();
    assertThatThrownBy(() -> at(EVENTS, "[0]..kind")).hasMessage("Malformed path: [0]..kind");
    assertThatThrownBy(() -> at(EVENTS, "[x]")).hasMessage("Malformed path: [x]");
  }

  @Test
  void buildsObjectLiteralsInOrder() {
    assertThat(object("b", 1, "a", null).keySet()).containsExactly("b", "a");
    assertThatThrownBy(() -> object("a")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deepContainsComparesTheExpectedKeysOnly() {
    Map<String, Object> event = EVENTS.getFirst();

    assertDeepContains(event, object("kind", "event", "value", object("cartId", "c1",
        "items", List.of(object("productId", "p1")))));
    assertThatThrownBy(() -> assertDeepContains(event, object("kind", "snapshot"))).isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertDeepContains(event, object("value", object("cartId", "c1"))))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertDeepContains(event, object("createdAt", null))).isInstanceOf(AssertionError.class);
  }

  @Test
  void theLocalProviderStoresNoPartitionKey() {
    EnvironmentVariables.set(AppHelper.TESTED_PROVIDER, AppHelper.LOCAL);

    assertThat(StoredDocuments.storesPartitionKey()).isFalse();
    assertThat(expectedEventDocument("Cart-c1-event", object("kind", "event"))).containsOnlyKeys("kind");
    assertEventStoreKey(EVENTS.getFirst(), "Cart-c1-event");
    assertThatThrownBy(() -> assertEventStoreKey(EVENTS.getFirst(), "Cart-c2-event"))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void theCloudProvidersStoreThePartitionKey() {
    EnvironmentVariables.set(AppHelper.TESTED_PROVIDER, AppHelper.AZURE);
    Map<String, Object> stored = object(StoredDocuments.EVENT_STORE_PARTITION_KEY, "Cart-c1-event", "kind", "event");

    assertThat(StoredDocuments.storesPartitionKey()).isTrue();
    assertThat(expectedEventDocument("Cart-c1-event", object("kind", "event")))
        .containsExactly(Map.entry(StoredDocuments.EVENT_STORE_PARTITION_KEY, "Cart-c1-event"),
            Map.entry("kind", "event"));
    assertEventStoreKey(stored, "Cart-c1-event");
    assertThatThrownBy(() -> assertEventStoreKey(EVENTS.getFirst(), "Cart-c1-event"))
        .isInstanceOf(AssertionError.class);
  }
}
