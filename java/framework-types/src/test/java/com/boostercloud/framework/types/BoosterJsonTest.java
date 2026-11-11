package com.boostercloud.framework.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.concepts.BoosterMetadata;
import com.boostercloud.framework.types.concepts.DataMigrationStatus;
import com.boostercloud.framework.types.concepts.SequenceKey;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ContextEnvelope;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.EventParametersFilterByEntity;
import com.boostercloud.framework.types.envelope.EventParametersFilterByType;
import com.boostercloud.framework.types.envelope.EventSearchParameters;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.NonPersistedEntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.SubscriptionEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.BoosterError;
import com.boostercloud.framework.types.errors.NotFoundError;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The JSON conventions of the shared mapper and the wire shape of the envelopes. */
class BoosterJsonTest {

  private static final UserEnvelope USER =
      new UserEnvelope(null, "user@example.com", List.of("UserWithEmail"), Map.of(), null);

  record WithOptional(String id, Optional<String> nickname, Instant when) {}

  static final class PlainClass {
    private final String id;
    private int counter = 3;
    private transient String secret = "hidden";

    PlainClass(String id) {
      this.id = id;
    }

    public String getComputed() {
      return id + secret;
    }
  }

  @Nested
  class Conventions {
    @Test
    void omitsAbsentValues() {
      String json = BoosterJson.toJson(new WithOptional("1", Optional.empty(), null));
      assertThat(json).isEqualTo("{\"id\":\"1\"}");
      assertThat(BoosterJson.toJson(new WithOptional("1", Optional.of("n"), null))).isEqualTo("{\"id\":\"1\",\"nickname\":\"n\"}");
    }

    @Test
    void keepsNullValuesInsideRawMaps() {
      Map<String, Object> raw = new LinkedHashMap<>();
      raw.put("a", null);
      raw.put("b", 1);
      assertThat(BoosterJson.toJson(raw)).isEqualTo("{\"a\":null,\"b\":1}");
    }

    @Test
    void serializesClassesFromTheirFieldsNotTheirGetters() {
      assertThat(BoosterJson.toJson(new PlainClass("x"))).isEqualTo("{\"id\":\"x\",\"counter\":3}");
    }

    @Test
    void writesDatesLikeJavaScriptToIsoString() {
      Instant instant = Instant.parse("2024-01-31T10:20:30Z");
      assertThat(BoosterJson.toJson(instant)).isEqualTo("\"2024-01-31T10:20:30.000Z\"");
      assertThat(BoosterJson.toJson(Instant.parse("2024-01-31T10:20:30.123456789Z"))).isEqualTo("\"2024-01-31T10:20:30.123Z\"");
      assertThat(BoosterJson.toJson(Date.from(instant))).isEqualTo("\"2024-01-31T10:20:30.000Z\"");
      assertThat(BoosterJson.toJson(OffsetDateTime.of(2024, 1, 31, 12, 20, 30, 0, ZoneOffset.ofHours(2))))
          .isEqualTo("\"2024-01-31T10:20:30.000Z\"");
    }

    @Test
    void readsInstantsFromIsoStringsAndEpochMillis() {
      assertThat(BoosterJson.convert("2024-01-31T10:20:30.1Z", Instant.class)).isEqualTo(Instant.parse("2024-01-31T10:20:30.100Z"));
      assertThat(BoosterJson.convert("2024-01-31T12:20:30+02:00", Instant.class)).isEqualTo(Instant.parse("2024-01-31T10:20:30Z"));
      assertThat(BoosterJson.convert("2024-01-31", Instant.class)).isEqualTo(Instant.parse("2024-01-31T00:00:00Z"));
      assertThat(BoosterJson.convert(1706696430000L, Instant.class)).isEqualTo(Instant.parse("2024-01-31T10:20:30Z"));
    }

    @Test
    void writesNumbersLikeJavaScript() {
      assertThat(BoosterJson.toJson(Map.of("a", 2.0))).isEqualTo("{\"a\":2}");
      assertThat(BoosterJson.toJson(Map.of("a", 2.5))).isEqualTo("{\"a\":2.5}");
      assertThat(BoosterJson.toJson(Map.of("a", 0.1f))).isEqualTo("{\"a\":0.1}");
      assertThat(BoosterJson.toJson(Map.of("a", Double.NaN))).isEqualTo("{\"a\":null}");
      assertThat(BoosterJson.toJson(List.of(-0.0, 1e20))).isEqualTo("[0,100000000000000000000]");
      assertThat(BoosterJson.formatNumber(1e21)).isEqualTo("1e+21");
      assertThat(BoosterJson.formatNumber(1.5e-7)).isEqualTo("1.5e-7");
      assertThat(BoosterJson.formatNumber(0.000123)).isEqualTo("0.000123");
      assertThat(BoosterJson.formatNumber(123456789.25)).isEqualTo("123456789.25");
      assertThat(BoosterJson.formatNumber(Double.POSITIVE_INFINITY)).isEqualTo("Infinity");
    }

    @Test
    void ignoresUnknownPropertiesWhenReading() {
      UserEnvelope user = BoosterJson.fromJson(
          "{\"username\":\"u\",\"roles\":[],\"claims\":{},\"unknown\":true}", UserEnvelope.class);
      assertThat(user.username()).isEqualTo("u");
    }

    @Test
    void convertsToAndFromRawValues() {
      Map<String, Object> raw = BoosterJson.toRawMap(new WithOptional("1", Optional.of("n"), Instant.EPOCH));
      assertThat(raw).containsExactly(Map.entry("id", "1"), Map.entry("nickname", "n"), Map.entry("when", "1970-01-01T00:00:00.000Z"));
      WithOptional back = BoosterJson.convert(raw, WithOptional.class);
      assertThat(back).isEqualTo(new WithOptional("1", Optional.of("n"), Instant.EPOCH));
      assertThat(BoosterJson.parse("[1, 2.5, \"x\", null, true]")).isEqualTo(java.util.Arrays.asList(1, 2.5, "x", null, true));
      assertThat(BoosterJson.toRawList(List.of(UUID.of("a")))).containsExactly("a");
      assertThatThrownBy(() -> BoosterJson.toRawMap("text")).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> BoosterJson.parse("{nope")).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void serializesBoosterErrorsLikeJsonStringify() {
      assertThat(BoosterJson.toJson(new BoosterError("m", "C", Map.of("a", 1)))).isEqualTo("{\"data\":{\"a\":1},\"code\":\"C\"}");
      assertThat(BoosterJson.toJson(new NotFoundError("missing"))).isEqualTo("{\"code\":\"NotFoundError\"}");
      assertThat(BoosterJson.toJson(new IllegalStateException("boom"))).isEqualTo("{\"message\":\"boom\"}");
    }
  }

  @Nested
  class Concepts {
    @Test
    void serializesIdsAsPlainStrings() {
      assertThat(BoosterJson.toJson(UUID.of("cart-42"))).isEqualTo("\"cart-42\"");
      assertThat(BoosterJson.fromJson("\"1234\"", UUID.class)).isEqualTo(UUID.of("1234"));
      assertThat(BoosterJson.toJson(SequenceKey.of("timestamp", "1"))).isEqualTo("{\"name\":\"timestamp\",\"value\":\"1\"}");
    }

    @Test
    void persistsDataMigrationStatusAsANumber() {
      assertThat(BoosterJson.toJson(Map.of("status", DataMigrationStatus.FINISHED))).isEqualTo("{\"status\":1}");
      assertThat(BoosterJson.convert(0, DataMigrationStatus.class)).isEqualTo(DataMigrationStatus.RUNNING);
      assertThat(BoosterJson.convert("1", DataMigrationStatus.class)).isEqualTo(DataMigrationStatus.FINISHED);
      assertThat(DataMigrationStatus.FINISHED.toString()).isEqualTo("1");
    }

    @Test
    void roundTripsBoosterMetadataKeepingUnknownKeys() {
      Map<String, Object> raw = BoosterJson.parseObject("""
          {"version":3,"schemaVersion":1,"optimisticConcurrencyValue":"etag","lastUpdateAt":"2024-11-10T09:15:30.123Z",
           "lastProjectionInfo":{"entityId":"cart-42","entityName":"Cart","entityUpdatedAt":"2024-11-10T09:15:30.001Z",
           "projectionMethod":"CartReadModel.projectCart"},"providerSpecific":{"x":1}}""");
      BoosterMetadata metadata = BoosterJson.convert(raw, BoosterMetadata.class);
      assertThat(metadata.version()).isEqualTo(3);
      assertThat(metadata.lastProjectionInfo().projectionMethod()).isEqualTo("CartReadModel.projectCart");
      assertThat(metadata.additionalProperties()).containsEntry("providerSpecific", Map.of("x", 1));
      assertThat(BoosterJson.toRawMap(metadata)).isEqualTo(raw);
      assertThat(BoosterJson.toJson(BoosterMetadata.of(1, 1))).isEqualTo("{\"version\":1,\"schemaVersion\":1}");
    }
  }

  @Nested
  class Envelopes {
    private final NonPersistedEventEnvelope event = new NonPersistedEventEnvelope(
        1, SuperKindType.DOMAIN, UUID.of("cart-1"), UUID.of("request-1"), USER, "Cart", "CartItemChanged",
        Map.of("cartId", "cart-1"), null);

    @Test
    void writesNonPersistedEventsInTheTypeScriptKeyOrder() {
      assertThat(BoosterJson.toJson(event)).isEqualTo(
          "{\"version\":1,\"kind\":\"event\",\"superKind\":\"domain\",\"entityID\":\"cart-1\",\"requestID\":\"request-1\","
              + "\"currentUser\":{\"username\":\"user@example.com\",\"roles\":[\"UserWithEmail\"],\"claims\":{}},"
              + "\"entityTypeName\":\"Cart\",\"typeName\":\"CartItemChanged\",\"value\":{\"cartId\":\"cart-1\"}}");
    }

    @Test
    void roundTripsPersistedEvents() {
      EventEnvelope persisted = event.persisted("2026-11-10T09:15:27.123Z");
      String json = BoosterJson.toJson(persisted);
      assertThat(json).endsWith("\"createdAt\":\"2026-11-10T09:15:27.123Z\"}");
      EventEnvelope back = BoosterJson.fromJson(json, EventEnvelope.class);
      assertThat(back.kind()).isEqualTo("event");
      assertThat(back.createdAt()).isEqualTo("2026-11-10T09:15:27.123Z");
      assertThat(back.value()).isEqualTo(Map.of("cartId", "cart-1"));
      assertThat(back.isDeleted()).isFalse();
      assertThat(back.toBuilder().deletedAt("2026-11-11T00:00:00.000Z").build().isDeleted()).isTrue();
      assertThat(back.toBuilder().deletedAt("").build().isDeleted()).isFalse();
      assertThat(back.toNonPersisted()).isEqualTo(event);
    }

    @Test
    void persistsSnapshotsWithTheSnapshottedEventTimeAsCreationTime() {
      NonPersistedEntitySnapshotEnvelope snapshot = new NonPersistedEntitySnapshotEnvelope(
          1, SuperKindType.DOMAIN, UUID.of("r"), null, UUID.of("cart-1"), "Cart", "Cart", Map.of("id", "cart-1"),
          "2026-11-10T09:15:27.123Z", null);
      EntitySnapshotEnvelope persisted = snapshot.persisted("2026-11-10T09:15:27.480Z");
      assertThat(persisted.createdAt()).isEqualTo("2026-11-10T09:15:27.123Z");
      assertThat(BoosterJson.toRawMap(persisted)).containsEntry("kind", "snapshot")
          .containsEntry("persistedAt", "2026-11-10T09:15:27.480Z");
      assertThat(persisted.toNonPersisted()).isEqualTo(snapshot);
    }

    @Test
    void neverSerializesTheRawContextNorTheReadModelClass() {
      ContextEnvelope context = ContextEnvelope.of(Map.of("authorization", "Bearer x"), Map.of(), new Object());
      ReadModelRequestEnvelope request = ReadModelRequestEnvelope.builder()
          .requestID(UUID.of("r")).context(context).type(String.class).className("CartReadModel").version(1)
          .filters(Map.of("id", Map.of("eq", "1"))).key(new ReadModelRequestEnvelope.Key(UUID.of("1"), null)).build();
      Map<String, Object> raw = BoosterJson.toRawMap(request);
      assertThat(raw).doesNotContainKey("type").containsEntry("className", "CartReadModel");
      assertThat(raw.get("context")).isEqualTo(Map.of("request", Map.of("headers", Map.of("authorization", "Bearer x"), "body", Map.of())));
      SubscriptionEnvelope subscription = SubscriptionEnvelope.of(request, 1794472800L, "connection",
          new GraphQLOperation("subscription { x }", "1", null, null));
      Map<String, Object> rawSubscription = BoosterJson.toRawMap(subscription);
      assertThat(rawSubscription).containsEntry("expirationTime", 1794472800).containsEntry("connectionID", "connection");
      SubscriptionEnvelope back = BoosterJson.convert(rawSubscription, SubscriptionEnvelope.class);
      assertThat(back.type()).isNull();
      assertThat(back.className()).isEqualTo("CartReadModel");
      assertThat(back.operation().id()).isEqualTo("1");
      assertThat(back.toReadModelRequestEnvelope().filters()).isEqualTo(Map.of("id", Map.of("eq", "1")));
    }

    @Test
    void deducesTheEventSearchKindFromThePresentKeys() {
      assertThat(BoosterJson.fromJson("{\"entity\":\"Cart\",\"entityID\":\"1\",\"limit\":5}", EventSearchParameters.class))
          .isEqualTo(new EventParametersFilterByEntity("Cart", "1", null, null, 5));
      assertThat(BoosterJson.fromJson("{\"type\":\"CartItemChanged\"}", EventSearchParameters.class))
          .isEqualTo(new EventParametersFilterByType("CartItemChanged", null, null, null));
      assertThat(EventSearchParameters.kindOf(Map.of())).isEqualTo(EventSearchParameters.Kind.NONE);
      assertThat(EventSearchParameters.kindOf(Map.of("entity", "a", "type", "b"))).isEqualTo(EventSearchParameters.Kind.BOTH);
      Map<String, Object> withNull = new LinkedHashMap<>();
      withNull.put("entity", null);
      assertThat(EventSearchParameters.kindOf(withNull)).isEqualTo(EventSearchParameters.Kind.BY_ENTITY);
      assertThat(EventSearchParameters.fromMap(Map.of("type", "E", "from", "2024-01-01T00:00:00.000Z", "limit", 2)))
          .isEqualTo(new EventParametersFilterByType("E", "2024-01-01T00:00:00.000Z", null, 2));
      assertThatThrownBy(() -> EventSearchParameters.fromMap(Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renamesFieldsInEventSearchResponses() {
      EventSearchResponse response = EventSearchResponse.fromEventEnvelope(event.persisted("2026-11-10T09:15:27.123Z"));
      assertThat(BoosterJson.toRawMap(response).keySet())
          .containsExactly("type", "entity", "entityID", "requestID", "user", "createdAt", "value");
      assertThat(response.type()).isEqualTo("CartItemChanged");
      assertThat(response.entity()).isEqualTo("Cart");
      assertThat(response.user()).isEqualTo(USER);
    }
  }
}
