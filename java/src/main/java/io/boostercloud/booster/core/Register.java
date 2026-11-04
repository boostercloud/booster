package io.boostercloud.booster.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;

/** A command's unit of work: events remain private until its handler succeeds. */
public final class Register extends QueryContext {
  final Map<StreamId, Long> expectedVersions = new LinkedHashMap<>();
  final List<PendingEvent> pending = new ArrayList<>();
  private final Map<StreamId, JsonNode> states = new HashMap<>();
  private final Map<StreamId,Booster.Snapshot> startingStates = new LinkedHashMap<>();
  private final Set<StreamId> liveStreams = new HashSet<>();
  final String operationFingerprint;
  int flushIndex;
  long validatedPosition;
  Register(Booster runtime, RequestContext context, String operationFingerprint) {
    super(runtime, context); this.operationFingerprint = Objects.requireNonNull(operationFingerprint);
    runtime.restoreFlushedPrefix(this);
  }
  /** Persist the current buffer. Later failures cannot undo successfully flushed events. */
  public void flush() { runtime.flush(this); }
  void clearFlushed() {
    pending.clear(); expectedVersions.clear(); states.clear(); startingStates.clear(); flushIndex++;
    runtime.restoreFlushedPrefix(this);
    for (var stream : liveStreams) restore(stream, runtime.snapshot(stream));
  }
  void restore(StreamId stream, Booster.Snapshot snapshot) {
    expectedVersions.put(stream, snapshot.version()); states.put(stream, snapshot.state().deepCopy());
    startingStates.put(stream, new Booster.Snapshot(snapshot.version(), snapshot.state().deepCopy()));
  }
  JsonNode flushContext() {
    var saved = Json.MAPPER.createObjectNode().put("operationFingerprint", operationFingerprint);
    var values = saved.putArray("states");
    startingStates.forEach((stream, snapshot) -> {
      var row = values.addObject().put("entityType", stream.entityType()).put("entityId", stream.entityId()).put("version", snapshot.version());
      row.set("state", snapshot.state());
    });
    return saved;
  }
  /** Live fencing read for orchestration state, exempt from historical partial-flush replay. */
  public <S> S currentEntity(Class<S> type, String id) {
    var stream = runtime.stream(type, id); liveStreams.add(stream);
    var current = runtime.snapshot(stream);
    boolean hasPending = pending.stream().anyMatch(event -> event.stream().equals(stream));
    if (hasPending) {
      long expected = expectedVersions.getOrDefault(stream, current.version());
      if (current.version() != expected) throw new OptimisticConcurrencyException(stream, expected, current.version());
    } else restore(stream, current);
    return runtime.convert(states.get(stream), type);
  }

  /** Replace an entity through durable control events without rewriting its historical events. */
  public void migrateEntity(String oldEntityName, String oldId, Object replacement) {
    Objects.requireNonNull(replacement, "Replacement entity is required");
    JsonNode value = Json.MAPPER.valueToTree(replacement);
    if (!value.path("id").isValueNode() || value.path("id").isNull() || value.path("id").asText().isBlank())
      throw new IllegalArgumentException("Replacement entity requires an id");
    var old = new StreamId(oldEntityName, oldId);
    var target = runtime.stream(replacement.getClass(), value.get("id").asText());
    ensureLoaded(old); ensureLoaded(target);
    if (states.get(old).isNull()) throw new BoosterException(BoosterException.Code.NOT_FOUND, "Source entity is absent");
    if (!old.equals(target) && !states.get(target).isNull())
      throw new BoosterException(BoosterException.Code.CONFLICT, "Migration destination already exists");
    if (!old.equals(target)) control(old, "__booster_entity_delete", 1, com.fasterxml.jackson.databind.node.NullNode.instance);
    control(target, "__booster_entity_replace", runtime.schemaVersion(replacement.getClass()), value);
  }
  public void touch(Class<?> type, String id) {
    var stream = runtime.stream(type, id); ensureLoaded(stream);
    if (states.get(stream).isNull()) throw new BoosterException(BoosterException.Code.NOT_FOUND, "Entity is absent");
    control(stream, "__booster_entity_touch", 1, com.fasterxml.jackson.databind.node.NullNode.instance);
  }
  /** Soft-delete a historical event. Its payload is cleared after the deletion marker commits. */
  public boolean deleteEvent(String eventId) {
    var found = runtime.findStoredEvent(Objects.requireNonNull(eventId));
    if (found.isEmpty() || runtime.eventDeleted(eventId)) return false;
    var event = found.get().event();
    if (Booster.internalEvent(event)) throw new IllegalArgumentException("Framework control events cannot be deleted");
    control(event.stream(), "__booster_event_deleted", 1, Json.MAPPER.createObjectNode().put("eventId", eventId));
    return true;
  }
  public void deleteEntity(Class<?> type, String id) {
    var stream = runtime.stream(type, id); ensureLoaded(stream);
    control(stream, "__booster_entity_delete", 1, com.fasterxml.jackson.databind.node.NullNode.instance);
  }
  private void control(StreamId stream, String type, int schemaVersion, JsonNode value) {
    ensureLoaded(stream);
    var metadata = new HashMap<>(context().metadata());
    metadata.putIfAbsent("correlationId", context().requestId()); metadata.put("requestId", context().requestId());
    var event = new PendingEvent(UUID.randomUUID().toString(), stream, type, schemaVersion, value,
        Instant.now(), context().user(), metadata);
    JsonNode next = runtime.reduceStored(event, states.get(stream));
    pending.add(event); states.put(stream, next);
  }
  @Override public <S> S entity(Class<S> type, String id) {
    var stream = runtime.stream(type, id);
    ensureLoaded(stream);
    return runtime.convert(states.get(stream), type);
  }
  private void ensureLoaded(StreamId stream) {
    if (!expectedVersions.containsKey(stream)) {
      var snapshot = runtime.snapshot(stream);
      restore(stream, snapshot);
    }
  }
  public void emit(Object event) {
    Objects.requireNonNull(event, "Event is required");
    var definition = runtime.eventDefinition(event.getClass());
    var stream = new StreamId(definition.entityName(), definition.entityId().apply(event));
    ensureLoaded(stream);
    JsonNode reduced = runtime.reduce(definition, event, states.get(stream));
    var metadata = new HashMap<>(context().metadata());
    metadata.putIfAbsent("correlationId", metadata.getOrDefault("requestId", context().requestId()));
    metadata.put("requestId", context().requestId());
    pending.add(new PendingEvent(UUID.randomUUID().toString(), stream, definition.name(),
        definition.version(), Json.MAPPER.valueToTree(event), Instant.now(), context().user(), metadata));
    states.put(stream, reduced);
  }
  public int pendingEvents() { return pending.size(); }
}
