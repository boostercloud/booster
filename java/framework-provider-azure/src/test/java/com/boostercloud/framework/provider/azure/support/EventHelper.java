package com.boostercloud.framework.provider.azure.support;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;

import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Port of {@code test/helpers/event-helper.ts}. */
public final class EventHelper {

  private EventHelper() {}

  /** @return an event envelope with random values ({@code createMockEventEnvelopes}) */
  public static EventEnvelope mockEventEnvelope() {
    return EventEnvelope.builder()
        .version(7)
        .entityID(UUID.generate())
        .superKind(SuperKindType.DOMAIN)
        .value(obj("id", UUID.generate().value()))
        .typeName("Type" + System.nanoTime())
        .entityTypeName("Entity" + System.nanoTime())
        .requestID(UUID.generate())
        .createdAt("2025-05-04T10:20:30.123Z")
        .id(UUID.generate().value())
        .build();
  }

  /**
   * @param numOfEvents how many
   * @return copies of one random envelope (TypeScript fills the array with the same object)
   */
  public static List<EventEnvelope> createMockEventEnvelopes(int numOfEvents) {
    EventEnvelope envelope = mockEventEnvelope();
    List<EventEnvelope> envelopes = new ArrayList<>();
    for (int i = 0; i < numOfEvents; i++) {
      envelopes.add(envelope);
    }
    return envelopes;
  }

  /**
   * @param entityTypeName entity
   * @param entityID id
   * @return a non-persisted event
   */
  public static NonPersistedEventEnvelope nonPersisted(String entityTypeName, String entityID) {
    return NonPersistedEventEnvelope.builder()
        .version(1)
        .superKind(SuperKindType.DOMAIN)
        .entityID(UUID.of(entityID))
        .requestID(UUID.of("request-1"))
        .entityTypeName(entityTypeName)
        .typeName(entityTypeName + "Changed")
        .value(obj("entityId", entityID))
        .build();
  }

  /**
   * {@code addMockSystemGeneratedProperties}: the document of an envelope with Cosmos system properties.
   *
   * @param envelope the envelope
   * @return its raw document
   */
  public static Map<String, Object> withSystemProperties(EventEnvelope envelope) {
    Map<String, Object> document = new LinkedHashMap<>(com.boostercloud.framework.types.BoosterJson.toRawMap(envelope));
    document.put("entityTypeName_entityID_kind", envelope.entityTypeName() + "-" + envelope.entityID() + "-event");
    document.put("_rid", "PaYSAPH7qAoCAAAAAAAAAA==");
    document.put("_self", "dbs/PaYSAA==/colls/PaYSAPH7qAo=/docs/PaYSAPH7qAoCAAAAAAAAAA==/");
    document.put("_etag", "\"" + UUID.generate() + "\"");
    document.put("_attachments", "attachments/");
    document.put("_ts", 1762766102);
    return document;
  }
}
