package io.boostercloud.booster.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;

/** A command's unit of work: events remain private until its handler succeeds. */
public final class Register extends QueryContext {
  final Map<StreamId, Long> expectedVersions = new LinkedHashMap<>();
  final List<PendingEvent> pending = new ArrayList<>();
  private final Map<StreamId, JsonNode> states = new HashMap<>();
  Register(Booster runtime, RequestContext context) { super(runtime, context); }
  @Override public <S> S entity(Class<S> type, String id) {
    var stream = runtime.stream(type, id);
    ensureLoaded(stream);
    return runtime.convert(states.get(stream), type);
  }
  private void ensureLoaded(StreamId stream) {
    if (!expectedVersions.containsKey(stream)) {
      var snapshot = runtime.snapshot(stream);
      expectedVersions.put(stream, snapshot.version());
      states.put(stream, snapshot.state());
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
