package com.boostercloud.framework.provider.azure.library;

import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Conversion between the raw documents of the events container and the envelope records of the provider SPI, plus a
 * weak registry from the envelopes handed to core back to the documents they were read from.
 *
 * <p>TypeScript passes the Cosmos documents themselves around as {@code EventEnvelope}s, so the system properties
 * survive until the provider sees them again: the stream producer publishes the document with its {@code _etag}
 * (the consumer's de-duplication key) and the soft delete replaces the whole document. Java envelopes are closed
 * records, so {@link #register(Object, Map)} remembers the source document of every envelope this provider returned
 * and {@link #documentFor(Object)} finds it again (entries disappear when core drops the envelope).
 */
public final class EventDocuments {

  private static final Map<Object, Map<String, Object>> SOURCES = Collections.synchronizedMap(new WeakHashMap<>());

  private EventDocuments() {}

  /**
   * @param envelope an envelope returned to core
   * @param document the raw document it was read from
   */
  static void register(Object envelope, Map<String, Object> document) {
    SOURCES.put(envelope, document);
  }

  /**
   * @param envelope an envelope core handed back
   * @return the document it was read from, or {@code null} when this provider did not produce it
   */
  static Map<String, Object> documentFor(Object envelope) {
    return SOURCES.get(envelope);
  }

  /**
   * @param document a raw document
   * @return whether its {@code kind} is {@code event}
   */
  static boolean isEvent(Object document) {
    return document instanceof Map<?, ?> map && EventEnvelope.KIND.equals(map.get("kind"));
  }

  /**
   * Converts documents to event envelopes and registers their sources.
   *
   * @param documents raw documents
   * @return the envelopes, in order
   */
  @SuppressWarnings("unchecked")
  static List<EventEnvelope> toEventEnvelopes(List<?> documents) {
    List<EventEnvelope> envelopes = new ArrayList<>();
    for (Object document : documents) {
      Map<String, Object> raw = (Map<String, Object>) BoosterJson.toRaw(document);
      EventEnvelope envelope = BoosterJson.convert(raw, EventEnvelope.class);
      register(envelope, raw);
      envelopes.add(envelope);
    }
    return envelopes;
  }

  /**
   * @param documents raw snapshot documents
   * @return the envelopes, in order
   */
  @SuppressWarnings("unchecked")
  static List<EntitySnapshotEnvelope> toSnapshotEnvelopes(List<?> documents) {
    List<EntitySnapshotEnvelope> envelopes = new ArrayList<>();
    for (Object document : documents) {
      Map<String, Object> raw = (Map<String, Object>) BoosterJson.toRaw(document);
      EntitySnapshotEnvelope envelope = BoosterJson.convert(raw, EntitySnapshotEnvelope.class);
      register(envelope, raw);
      envelopes.add(envelope);
    }
    return envelopes;
  }
}
