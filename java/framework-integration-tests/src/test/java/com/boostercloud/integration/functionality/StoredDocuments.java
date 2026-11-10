package com.boostercloud.integration.functionality;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.integration.helper.AppHelper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reading and checking the raw provider documents the functionality suites get from
 * {@code applicationUnderTest.query()} (event-store and read-model documents as parsed JSON maps).
 *
 * <p>The TypeScript suites run on AWS and Azure, whose event stores add the partition key
 * {@code entityTypeName_entityID_kind} ({@code <entityTypeName>-<entityID>-<kind>}) to every event document. The local
 * provider stores the envelopes as they are (TypeScript and Java alike): its key is made of the three fields
 * {@code entityTypeName}, {@code entityID} and {@code kind}, which is also how the local test helper resolves
 * {@code query().events("Cart-42-event")}. {@link #expectedEventDocument(String, Map)} and
 * {@link #assertEventStoreKey(Map, String)} therefore check the stored field on the cloud providers and the three
 * fields it is made of on the local provider.
 */
final class StoredDocuments {

  /** The partition key field of the AWS and Azure event stores. */
  static final String EVENT_STORE_PARTITION_KEY = "entityTypeName_entityID_kind";

  private static final Pattern STEP = Pattern.compile("\\.?([^.\\[\\]]+)|\\[(\\d+)]");

  private StoredDocuments() {}

  /**
   * A JavaScript object literal: {@code object("id", id, "value", object("quantity", 2))}.
   *
   * @param keysAndValues keys and values, alternately
   * @return a mutable map in that order (values may be {@code null})
   */
  static Map<String, Object> object(Object... keysAndValues) {
    if (keysAndValues.length % 2 != 0) {
      throw new IllegalArgumentException("Keys and values must come in pairs");
    }
    Map<String, Object> object = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      object.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return object;
  }

  /**
   * @return whether the event documents of the tested provider carry {@link #EVENT_STORE_PARTITION_KEY}
   */
  static boolean storesPartitionKey() {
    return !AppHelper.LOCAL.equals(AppHelper.testedProvider());
  }

  /**
   * JavaScript's optional chaining over a parsed JSON tree: {@code at(events, "[0].value.productId")} is
   * {@code events?.[0]?.value?.productId}.
   *
   * @param root a parsed JSON value ({@code Map}, {@code List}, ...)
   * @param path property names separated by dots and {@code [index]} accessors
   * @return the value, or {@code null} when any step is missing
   */
  static Object at(Object root, String path) {
    Object current = root;
    Matcher matcher = STEP.matcher(path);
    int position = 0;
    while (matcher.find()) {
      if (matcher.start() != position) {
        throw new IllegalArgumentException("Malformed path: " + path);
      }
      position = matcher.end();
      if (current == null) {
        return null;
      }
      if (matcher.group(1) != null) {
        current = current instanceof Map<?, ?> map ? map.get(matcher.group(1)) : null;
      } else {
        int index = Integer.parseInt(matcher.group(2));
        current = current instanceof List<?> list && index < list.size() ? list.get(index) : null;
      }
    }
    if (position != path.length()) {
      throw new IllegalArgumentException("Malformed path: " + path);
    }
    return current;
  }

  /**
   * @param root a parsed JSON value
   * @param path see {@link #at(Object, String)}
   * @return the object at that path, or {@code null}
   */
  @SuppressWarnings("unchecked")
  static Map<String, Object> objectAt(Object root, String path) {
    return at(root, path) instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
  }

  /**
   * The expected fields of an event document of the store key {@code <entityTypeName>-<entityID>-<kind>}: the given
   * fields plus, on the providers that store it, {@link #EVENT_STORE_PARTITION_KEY} first (as the TypeScript
   * expectations list it).
   *
   * @param eventStoreKey the key
   * @param fields the other expected fields
   * @return the expected fields
   */
  static Map<String, Object> expectedEventDocument(String eventStoreKey, Map<String, Object> fields) {
    Map<String, Object> expected = new LinkedHashMap<>();
    if (storesPartitionKey()) {
      expected.put(EVENT_STORE_PARTITION_KEY, eventStoreKey);
    }
    expected.putAll(fields);
    return expected;
  }

  /**
   * {@code expect(event.entityTypeName_entityID_kind).to.be.equal(key)}; on the local provider, which does not store
   * that field, the key the event is stored under ({@code entityTypeName}, {@code entityID} and {@code kind}).
   *
   * @param event the stored event document
   * @param eventStoreKey the expected key, {@code <entityTypeName>-<entityID>-<kind>}
   */
  static void assertEventStoreKey(Map<String, Object> event, String eventStoreKey) {
    if (storesPartitionKey()) {
      assertThat(event.get(EVENT_STORE_PARTITION_KEY)).isEqualTo(eventStoreKey);
    } else {
      assertThat(event).doesNotContainKey(EVENT_STORE_PARTITION_KEY);
      assertThat(event.get("entityTypeName") + "-" + event.get("entityID") + "-" + event.get("kind"))
          .as("entityTypeName-entityID-kind").isEqualTo(eventStoreKey);
    }
  }

  /**
   * Chai's {@code expect(actual).to.deep.contain(expected)}: every expected key is present with a deep-equal value;
   * other keys may exist.
   *
   * @param actual the stored document
   * @param expected the expected subset
   */
  static void assertDeepContains(Map<String, Object> actual, Map<String, Object> expected) {
    List<String> missing = new ArrayList<>(expected.keySet());
    missing.removeAll(actual.keySet());
    assertThat(missing).as("keys missing from %s", actual).isEmpty();
    expected.forEach((key, value) -> assertThat(actual.get(key)).as(key).isEqualTo(value));
  }
}
