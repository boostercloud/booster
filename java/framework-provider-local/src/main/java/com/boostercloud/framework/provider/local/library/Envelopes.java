package com.boostercloud.framework.provider.local.library;

import com.boostercloud.framework.provider.local.store.Js;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Conversions between stored documents and envelope records. A stored document is the envelope's JSON plus NeDB's
 * {@code _id}; envelopes read back expose that id as {@code id} (TypeScript returns the documents themselves, with
 * {@code _id}, and adds {@code id} only for deletable events/snapshots).
 */
final class Envelopes {

  private Envelopes() {}

  static EventEnvelope toEventEnvelope(Object document) {
    if (document instanceof EventEnvelope envelope) {
      return envelope;
    }
    EventEnvelope envelope = BoosterJson.convert(document, EventEnvelope.class);
    Object id = document instanceof Map<?, ?> map ? map.get("_id") : null;
    if (envelope.id() == null && id != null) {
      envelope = envelope.toBuilder().id(Js.toJsString(id)).build();
    }
    return envelope;
  }

  static List<EventEnvelope> toEventEnvelopes(List<? extends Map<String, Object>> documents) {
    List<EventEnvelope> envelopes = new ArrayList<>(documents.size());
    for (Map<String, Object> document : documents) {
      envelopes.add(toEventEnvelope(document));
    }
    return envelopes;
  }

  static EntitySnapshotEnvelope toSnapshotEnvelope(Object document) {
    if (document instanceof EntitySnapshotEnvelope envelope) {
      return envelope;
    }
    EntitySnapshotEnvelope envelope = BoosterJson.convert(document, EntitySnapshotEnvelope.class);
    Object id = document instanceof Map<?, ?> map ? map.get("_id") : null;
    if (envelope.id() == null && id != null) {
      envelope = envelope.toBuilder().id(Js.toJsString(id)).build();
    }
    return envelope;
  }

  /**
   * @param envelope an envelope record (or any object)
   * @return its stored JSON shape; an {@code id} is only present when the envelope has one (soft-deleted events keep
   *     it, as in TypeScript, quirk Q17), so new documents have {@code _id} as their only database id
   */
  static Map<String, Object> toDocument(Object envelope) {
    return BoosterJson.toRawMap(envelope);
  }
}
