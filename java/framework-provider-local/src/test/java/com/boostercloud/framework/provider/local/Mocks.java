package com.boostercloud.framework.provider.local;

import static com.boostercloud.framework.provider.local.Raw.list;
import static com.boostercloud.framework.provider.local.Raw.map;

import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Port of {@code test/helpers/event-helper.ts} and {@code test/helpers/read-model-helper.ts}: random envelopes (faker
 * is replaced by {@link ThreadLocalRandom}).
 */
public final class Mocks {

  private static final String[] WORDS = {"alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel",
      "india", "juliet", "kilo", "lima", "mike", "november", "oscar", "papa", "quebec", "romeo", "sierra", "tango"};

  private Mocks() {}

  /**
   * @return a random word (with a random suffix so that two calls practically never collide)
   */
  public static String word() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    return WORDS[random.nextInt(WORDS.length)] + random.nextInt(1_000_000);
  }

  /**
   * @return a random UUID string
   */
  public static String uuid() {
    return java.util.UUID.randomUUID().toString();
  }

  /**
   * @return an ISO date in the past year ({@code date.past()})
   */
  public static String pastDate() {
    long now = System.currentTimeMillis();
    return Dates.toIso(now - ThreadLocalRandom.current().nextLong(1, 365L * 24 * 3600 * 1000));
  }

  /**
   * @return {@code createMockNonPersistedEventEnvelop()}
   */
  public static NonPersistedEventEnvelope nonPersistedEvent() {
    return nonPersistedEventForEntity(word(), uuid());
  }

  /**
   * @param entityTypeName the entity name
   * @param entityID the entity id
   * @return {@code createMockNonPersistedEventEnvelopeForEntity(entityTypeName, entityID)}
   */
  public static NonPersistedEventEnvelope nonPersistedEventForEntity(String entityTypeName, String entityID) {
    return NonPersistedEventEnvelope.builder()
        .superKind(SuperKindType.DOMAIN)
        .entityID(UUID.of(entityID))
        .entityTypeName(entityTypeName)
        .value(map("id", uuid()))
        .requestID(UUID.of(uuid()))
        .typeName(word())
        .version(ThreadLocalRandom.current().nextInt(1, 100000))
        .build();
  }

  /**
   * @return {@code createMockEventEnvelope()}
   */
  public static EventEnvelope event() {
    return eventForEntity(word(), uuid());
  }

  /**
   * @param entityTypeName the entity name
   * @param entityID the entity id
   * @return {@code createMockEventEnvelopeForEntity(entityTypeName, entityID)}
   */
  public static EventEnvelope eventForEntity(String entityTypeName, String entityID) {
    return nonPersistedEventForEntity(entityTypeName, entityID).persisted(pastDate());
  }

  /**
   * @param entityTypeName the entity name, or {@code null} for a random one
   * @param entityId the entity id, or {@code null} for a random one
   * @return {@code createMockEntitySnapshotEnvelope(entityTypeName?, entityId?)}
   */
  public static EntitySnapshotEnvelope snapshot(String entityTypeName, String entityId) {
    String snapshottedEventCreatedAt = pastDate();
    return EntitySnapshotEnvelope.builder()
        .superKind(SuperKindType.DOMAIN)
        .entityID(UUID.of(entityId == null ? uuid() : entityId))
        .entityTypeName(entityTypeName == null ? word() : entityTypeName)
        .value(map("id", uuid()))
        .createdAt(snapshottedEventCreatedAt)
        .persistedAt(Dates.toIso(Dates.parse(snapshottedEventCreatedAt).plusSeconds(1)))
        .requestID(UUID.of(uuid()))
        .typeName(word())
        .version(ThreadLocalRandom.current().nextInt(1, 100000))
        .snapshottedEventCreatedAt(snapshottedEventCreatedAt)
        .build();
  }

  /**
   * @return {@code createMockReadModelEnvelope()}: {@code { value: { id, age, foo, bar, boosterMetadata, arr, prop },
   *     typeName }}
   */
  public static Map<String, Object> readModelEnvelope() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    return map(
        "value", map(
            "id", uuid(),
            "age", random.nextInt(0, 41),
            "foo", word(),
            "bar", Math.round(random.nextDouble(0, 99999) * 100) / 100.0,
            "boosterMetadata", map("version", 1, "schemaVersion", 1),
            "arr", list(map("id", uuid(), "name", word()), map("id", uuid(), "name", word())),
            "prop", map("items", list(map("id", uuid(), "name", word()), map("id", uuid(), "name", word())))),
        "typeName", word());
  }

  /**
   * @param envelope a read model envelope
   * @return its value
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> value(Map<String, Object> envelope) {
    return (Map<String, Object>) envelope.get("value");
  }
}
