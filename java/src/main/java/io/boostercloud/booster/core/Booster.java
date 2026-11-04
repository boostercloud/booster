package io.boostercloud.booster.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Provider-independent Java event-sourcing and CQRS runtime. */
public final class Booster implements AutoCloseable {
  record Snapshot(long version, JsonNode state) {}
  record EventDefinition(String name, Class<?> type, String entityName, Class<?> entityType,
      Function<Object, String> entityId, BiFunction<Object, Object, Object> reducer, int version, boolean notification) {}
  private record CommandRegistration(Operation operation, AccessPolicy policy, CommandHandler<Object> handler) {}
  private record QueryRegistration(Operation operation, AccessPolicy policy, QueryHandler<Object> handler) {}
  private record ProjectionBinding(Class<?> source, Join<Object,Object> join,
      Function<ProjectionContext<Object,Object>,ProjectionResult<Object>> project,
      Function<ProjectionContext<Object,Object>,ProjectionResult<Object>> unproject, boolean legacy) {}
  private record ProjectionRegistration(ReadModel model, AccessPolicy policy, List<ProjectionBinding> bindings) {}
  private record HandlerRegistration(String name, Class<?> type, BiConsumer<Object, Register> handler) {}
  private record FunctionRegistration(AccessPolicy policy, RocketFunction handler) {}
  private static final Object SKIPPED_EVENT = new Object();
  private final EventStore store;
  private final StateStore stateStore;
  private final Map<Class<?>, Integer> schemaVersions;
  private final Map<Class<?>, String> entities;
  private final Map<Class<?>, EventDefinition> events;
  private final Map<String, EventDefinition> eventsByName;
  private final Map<String, CommandRegistration> commands;
  private final Map<String, QueryRegistration> queries;
  private final Map<String, FunctionRegistration> functions;
  private final Map<String, ProjectionRegistration> projections;
  private final Map<Class<?>, Map<Integer, UnaryOperator<JsonNode>>> upcasters;
  private final List<HandlerRegistration> handlers;
  private final List<BiConsumer<String, Register>> beforeHooks;
  private final List<BiConsumer<String, JsonNode>> afterHooks;
  private final Consumer<Throwable> errorHandler;
  private final Map<FrameworkError.Stage,Function<FrameworkError,Throwable>> typedErrorHandlers;
  private final Function<FrameworkError,Throwable> globalErrorHandler;
  private final Map<String,List<BeforeHook>> inputHooks;
  private final Map<String,AccessPolicy> historyPolicies;
  private final Map<String,AccessPolicy> administrationPolicies;
  private final Map<String,java.time.Instant> deletedEvents = new HashMap<>();
  private final Set<String> redactedEvents = new HashSet<>();
  private long indexedPosition;
  private boolean deletionsDirty;
  private record SchemaAnchor(String eventId, String namespace, String key, int version, long sourcePosition, JsonNode state, Set<String> sourceTypes) {}
  private final Map<Long,List<SchemaAnchor>> schemaAnchors = new TreeMap<>();
  private record Deletion(StreamId stream, long eventPosition, long markerPosition, java.time.Instant timestamp) {}
  private final Map<String,Deletion> deletions = new HashMap<>();
  private final Set<String> redactedSchemaEvents = new HashSet<>();
  private boolean schemasDirty;
  private final Map<StreamId, Snapshot> snapshots = new HashMap<>();
  private final Map<String, Map<String, JsonNode>> models = new HashMap<>();
  private final Map<String, SubmissionPublisher<ReadModelChange>> publishers = new HashMap<>();
  private static final class RequestLock { private int users; }
  private final ConcurrentHashMap<String, RequestLock> requestLocks = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
      Thread.ofPlatform().daemon().name("booster-scheduler").factory());
  private long checkpoint;
  private boolean closed;

  private Booster(Builder builder) {
    store = builder.store; stateStore = builder.stateStore; schemaVersions = Map.copyOf(builder.schemaVersions);
    entities = Map.copyOf(builder.entities); events = Map.copyOf(builder.events);
    eventsByName = new HashMap<>(); events.values().forEach(e -> eventsByName.put(e.name(), e));
    commands = Map.copyOf(builder.commands); queries = Map.copyOf(builder.queries); functions = Map.copyOf(builder.functions);
    projections = new LinkedHashMap<>();
    builder.projections.forEach((name, projection) -> projections.put(name,
        new ProjectionRegistration(projection.model(), projection.policy(), List.copyOf(projection.bindings()))));
    handlers = List.copyOf(builder.handlers);
    beforeHooks = List.copyOf(builder.beforeHooks); afterHooks = List.copyOf(builder.afterHooks);
    errorHandler = builder.errorHandler; typedErrorHandlers = Map.copyOf(builder.typedErrorHandlers);
    globalErrorHandler = builder.globalErrorHandler; historyPolicies = Map.copyOf(builder.historyPolicies);
    administrationPolicies = Map.copyOf(builder.administrationPolicies);
    inputHooks = new HashMap<>(); builder.inputHooks.forEach((name, hooks) -> inputHooks.put(name, List.copyOf(hooks)));
    upcasters = new HashMap<>(); builder.upcasters.forEach((type, chain) -> upcasters.put(type, Map.copyOf(chain)));
    projections.keySet().forEach(name -> {
      models.put(name, new TreeMap<>());
      publishers.put(name, new SubmissionPublisher<>(ForkJoinPool.commonPool(), 256));
    });
    try { migrateDerivedSchemas(); catchUp(false); } catch (RuntimeException error) { scheduler.shutdownNow(); publishers.values().forEach(SubmissionPublisher::close); throw error; }
    // Recover unfinished handlers after restart without changing the success of the
    // original command. Failures remain observable through the configured error hook.
    completeCommittedWork();
  }
  public static Builder builder(EventStore store) { return new Builder(store); }
  public List<Operation> commands() { return commands.values().stream().map(CommandRegistration::operation).sorted(Comparator.comparing(Operation::name)).toList(); }
  public List<Operation> queries() { return queries.values().stream().map(QueryRegistration::operation).sorted(Comparator.comparing(Operation::name)).toList(); }
  public List<ReadModel> readModels() { return projections.values().stream().map(ProjectionRegistration::model).sorted(Comparator.comparing(ReadModel::name)).toList(); }
  public EventStore eventStore() { return store; }
  public StateStore stateStore() { return stateStore; }
  public <S> S entity(Class<S> type, String id) { return loadEntity(type, id); }
  public <T> JsonNode migrateSchema(Class<T> type, int fromVersion, JsonNode input) {
    int current = schemaVersions.getOrDefault(type, events.containsKey(type) ? events.get(type).version() : 1);
    if (fromVersion < 1 || fromVersion > current)
      throw new BoosterException(BoosterException.Code.VALIDATION, "Invalid or future schema version for " + type.getSimpleName());
    JsonNode result = Objects.requireNonNull(input).deepCopy();
    for (int version = fromVersion; version < current; version++) {
      var migration = upcasters.getOrDefault(type, Map.of()).get(version);
      if (migration == null) throw new BoosterException(BoosterException.Code.VALIDATION, "Missing schema migration for " + type.getSimpleName() + " version " + version);
      result = Objects.requireNonNull(migration.apply(result), "Schema migrations cannot return null");
    }
    return result.deepCopy();
  }
  public JsonNode executeCommand(String name, JsonNode input, int schemaVersion, RequestContext context) {
    var registration = commands.get(name);
    if (registration == null) throw new BoosterException(BoosterException.Code.NOT_FOUND, "Unknown command: " + name);
    return executeCommand(name, migrateSchema(registration.operation().inputType(), schemaVersion, input), context);
  }
  public List<String> entityNames() { return entities.values().stream().sorted().toList(); }
  public List<String> eventNames() { return events.values().stream().filter(event -> !event.notification()).map(EventDefinition::name).sorted().toList(); }
  public synchronized List<StoredEvent> searchEvents(String entity, String eventType, String id,
      java.time.Instant from, java.time.Instant to, int limit, RequestContext context) {
    if ((entity == null) == (eventType == null)) throw new BoosterException(BoosterException.Code.VALIDATION, "Supply exactly one entity or event type");
    if (limit < 1 || limit > 1000 || from != null && to != null && from.isAfter(to))
      throw new BoosterException(BoosterException.Code.VALIDATION, "Invalid event search range or limit");
    String entityName = entity;
    if (eventType != null) {
      var definition = eventsByName.get(eventType);
      if (definition == null || definition.notification()) throw new BoosterException(BoosterException.Code.NOT_FOUND, "Event has no registered entity");
      if (id != null) throw new BoosterException(BoosterException.Code.VALIDATION, "Entity ID requires an entity search");
      entityName = definition.entityName();
    }
    if (!entities.containsValue(entityName)) throw new BoosterException(BoosterException.Code.NOT_FOUND, "Unknown entity");
    var request = Json.MAPPER.createObjectNode().put("entity", entity).put("type", eventType).put("entityID", id);
    authorize(historyPolicies.getOrDefault(entityName, AccessPolicy.deny()), context, request);
    checkOpen(); catchUp(true);
    var found = new ArrayList<StoredEvent>(); long position = 0;
    while (true) {
      var batch = store.readAll(position, 256); if (batch.isEmpty()) break;
      for (var stored : batch) {
        var event = stored.event(); position = stored.position();
        if (event.type().startsWith("__booster_") || deletedEvents.containsKey(event.id())) continue;
        if (!event.stream().entityType().equals(entityName) || eventType != null && !event.type().equals(eventType)
            || id != null && !event.stream().entityId().equals(id)
            || from != null && event.timestamp().isBefore(from) || to != null && event.timestamp().isAfter(to)) continue;
        found.add(stored);
      }
    }
    return found.stream().sorted(Comparator.comparing((StoredEvent event) -> event.event().timestamp()).thenComparingLong(StoredEvent::position).reversed()).limit(limit).toList();
  }
  public synchronized EntityIdPage entitiesIDs(String entityName, int limit, String cursor, RequestContext context) {
    if (!entities.containsValue(entityName)) throw new BoosterException(BoosterException.Code.NOT_FOUND, "Unknown entity");
    if (limit < 1 || limit > 1000) throw new BoosterException(BoosterException.Code.VALIDATION, "Invalid entity ID page limit");
    authorize(historyPolicies.getOrDefault(entityName, AccessPolicy.deny()), context, Json.MAPPER.createObjectNode().put("entity", entityName));
    checkOpen(); catchUp(true); String after = null;
    if (cursor != null) {
      try {
        if (cursor.length() > 8192) throw new IllegalArgumentException();
        var decoded = Json.MAPPER.readTree(Base64.getUrlDecoder().decode(cursor));
        if (!decoded.path("entity").asText().equals(entityName) || !decoded.path("after").isTextual()) throw new IllegalArgumentException();
        after = decoded.path("after").asText();
      } catch (Exception error) { throw new BoosterException(BoosterException.Code.VALIDATION, "Invalid entity ID cursor", error); }
    }
    var ids = new TreeSet<String>(); long position = 0;
    while (true) {
      var batch = store.readAll(position, 256); if (batch.isEmpty()) break;
      for (var stored : batch) { position = stored.position(); var event = stored.event();
        if (!event.type().startsWith("__booster_") && !deletedEvents.containsKey(event.id()) && event.stream().entityType().equals(entityName)) ids.add(event.stream().entityId());
      }
    }
    var remaining = after == null ? ids : ids.tailSet(after, false);
    var items = remaining.stream().limit(limit).toList(); String next = null;
    if (remaining.size() > items.size()) next = Base64.getUrlEncoder().withoutPadding().encodeToString(
        Json.MAPPER.createObjectNode().put("entity", entityName).put("after", items.getLast()).toString().getBytes(StandardCharsets.UTF_8));
    return new EntityIdPage(items, items.size(), next);
  }
  public synchronized long position() { checkOpen(); catchUp(true); return checkpoint; }
  private void checkOpen() { if (closed) throw new IllegalStateException("Booster runtime is closed"); }

  public JsonNode executeCommand(String name, JsonNode input, RequestContext context) {
    var registration = commands.get(name);
    if (registration == null) throw new BoosterException(BoosterException.Code.NOT_FOUND, "Unknown command: " + name);
    return executeRegistered(name, name, registration, input, context, false);
  }

  /** Execute a provider/rocket action through the same authorization, unit-of-work and receipt contract. */
  public JsonNode executeAction(String name, JsonNode input, RequestContext context, AccessPolicy policy, CommandHandler<JsonNode> handler) {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("Action name is required");
    Objects.requireNonNull(policy); Objects.requireNonNull(handler);
    var registration = new CommandRegistration(new Operation(name, JsonNode.class, JsonNode.class), policy,
        (value, register) -> handler.handle((JsonNode) value, register));
    return executeRegistered(name, "action:" + name, registration, input, context, false);
  }

  public JsonNode executeSystemAction(String name, JsonNode input, RequestContext context, AccessPolicy policy, CommandHandler<JsonNode> handler) {
    Objects.requireNonNull(context); Objects.requireNonNull(policy); Objects.requireNonNull(handler);
    if (name == null || name.isBlank() || !context.requestId().startsWith("__booster_")) throw new IllegalArgumentException("System actions require a reserved request ID and action name");
    var registration = new CommandRegistration(new Operation(name, JsonNode.class, JsonNode.class), policy,
        (value, register) -> handler.handle((JsonNode)value, register));
    return executeRegistered(name, "system:" + name, registration, input, context, true);
  }

  /** Scheduled handlers have their own explicitly configurable error boundary. */
  public JsonNode executeScheduledAction(String name, JsonNode input, RequestContext context, AccessPolicy policy, CommandHandler<JsonNode> handler) {
    return executeScheduledAction(name, input, context, policy, handler, () -> {});
  }
  /** Orchestration guards are outside suppressible application errors, including explicit register flushes. */
  public JsonNode executeScheduledAction(String name, JsonNode input, RequestContext context, AccessPolicy policy,
      CommandHandler<JsonNode> handler, Runnable commitGuard) {
    Objects.requireNonNull(context); Objects.requireNonNull(policy); Objects.requireNonNull(handler); Objects.requireNonNull(commitGuard);
    if (name == null || name.isBlank() || !context.requestId().startsWith("__booster_")) throw new IllegalArgumentException("Scheduled actions require a reserved request ID and name");
    var registration = new CommandRegistration(new Operation(name, JsonNode.class, JsonNode.class), policy,
        (value, register) -> handler.handle((JsonNode)value, register));
    return executeRegistered(name, "system:" + name, registration, input, context, true, FrameworkError.Stage.SCHEDULE, commitGuard);
  }

  public List<String> functions() { return functions.keySet().stream().sorted().toList(); }
  public Object executeFunction(String name, Object request, RequestContext context) {
    if (name == null || name.isBlank()) throw new BoosterException(BoosterException.Code.VALIDATION, "Rocket function name is required");
    var function = functions.get(name);
    if (function == null) throw new BoosterException(BoosterException.Code.NOT_FOUND, "Unknown rocket function: " + name);
    authorize(function.policy(), context, request instanceof JsonNode json ? json : null);
    synchronized (this) { checkOpen(); }
    try { return function.handler().invoke(this, request, context); }
    catch (BoosterException error) { report(error); throw error; }
    catch (Exception error) { report(error); throw new BoosterException(BoosterException.Code.INTERNAL, "Rocket function failed: " + name, error); }
  }

  private JsonNode executeRegistered(String name, String identity, CommandRegistration registration, JsonNode input, RequestContext context, boolean trusted) {
    return executeRegistered(name, identity, registration, input, context, trusted, null);
  }
  private JsonNode executeRegistered(String name, String identity, CommandRegistration registration, JsonNode input, RequestContext context, boolean trusted, FrameworkError.Stage boundary) {
    return executeRegistered(name, identity, registration, input, context, trusted, boundary, () -> {});
  }
  private JsonNode executeRegistered(String name, String identity, CommandRegistration registration, JsonNode input, RequestContext context,
      boolean trusted, FrameworkError.Stage boundary, Runnable commitGuard) {
    Objects.requireNonNull(context, "Request context is required");
    if (!trusted && context.requestId().startsWith("__booster_")) throw new BoosterException(BoosterException.Code.VALIDATION, "Request ID uses a reserved namespace");
    var lock = requestLocks.compute(context.requestId(), (key, existing) -> {
      var value = existing == null ? new RequestLock() : existing;
      value.users++; return value;
    });
    try { synchronized (lock) { return executeCommandOnce(name, identity, registration, input, context, boundary, commitGuard); } }
    finally { requestLocks.compute(context.requestId(), (key, existing) -> --existing.users == 0 ? null : existing); }
  }

  private JsonNode executeCommandOnce(String name, String identity, CommandRegistration registration, JsonNode input, RequestContext context,
      FrameworkError.Stage boundary, Runnable commitGuard) {
    authorize(registration.policy(), context, input);
    String fingerprint = fingerprint(identity, input, context);
    try {
      synchronized (this) { checkOpen(); catchUp(true); }
      var cached = store.findCommand(context.requestId());
      if (cached.isPresent()) {
        verifyFingerprint(cached.get(), fingerprint);
        completeCommittedWork();
        return cached.get().result().deepCopy();
      }
      var register = new Register(this, context, fingerprint, commitGuard);
      boolean commandOperation = !identity.startsWith("action:") && !identity.startsWith("system:");
      Object result = null;
      commitGuard.run();
      try {
        Object command = convert(commandOperation ? applyHooks("command", name, input, context) : input, registration.operation().inputType());
        if (command == null) throw new BoosterException(BoosterException.Code.VALIDATION, "Command input is required");
        if (commandOperation) beforeHooks.forEach(hook -> hook.accept(name, register));
        result = registration.handler().handle(command, register);
      } catch (Exception error) {
        if (!commandOperation && boundary == null) throw asException(error, "Action failed: " + name);
        Throwable replacement = dispatchError(new FrameworkError(boundary == null ? FrameworkError.Stage.COMMAND : boundary, name, input, context, error));
        if (replacement != null) throw asException(replacement, "Command failed: " + name);
      }
      JsonNode jsonResult = serializeResult(result, registration.operation());
      var committed = commitRegister(context.requestId(), fingerprint, register, jsonResult);
      completeCommittedWork();
      if (commandOperation && !committed.duplicate()) for (var hook : afterHooks) {
        try { hook.accept(name, committed.receipt().result().deepCopy()); } catch (Exception error) { report(error); }
      }
      return committed.receipt().result().deepCopy();
    } catch (BoosterException error) {
      var completed = recoverConcurrentCommit(context, fingerprint);
      if (completed.isPresent()) return completed.get();
      report(error); throw error;
    } catch (Exception error) {
      var completed = recoverConcurrentCommit(context, fingerprint);
      if (completed.isPresent()) return completed.get();
      report(error); throw new BoosterException(BoosterException.Code.INTERNAL, "Command failed: " + name, error);
    }
  }

  private Optional<JsonNode> recoverConcurrentCommit(RequestContext context, String fingerprint) {
    var receipt = store.findCommand(context.requestId());
    if (receipt.isEmpty()) return Optional.empty();
    verifyFingerprint(receipt.get(), fingerprint);
    completeCommittedWork();
    return Optional.of(receipt.get().result());
  }

  private void completeCommittedWork() {
    // A durable commit remains successful if derived work fails; querying/rebuilding
    // surfaces projection errors, and resumeHandlers retries unfinished durable receipts.
    try { synchronized (this) { catchUp(true); } resumeHandlers(); }
    catch (RuntimeException error) { report(error); }
  }

  public JsonNode executeQuery(String name, JsonNode input, RequestContext context) {
    var registration = queries.get(name);
    if (registration == null) throw new BoosterException(BoosterException.Code.NOT_FOUND, "Unknown query: " + name);
    authorize(registration.policy(), context, input);
    synchronized (this) { checkOpen(); catchUp(true); }
    try {
      Object query = convert(applyHooks("query", name, input, context), registration.operation().inputType());
      if (query == null) throw new BoosterException(BoosterException.Code.VALIDATION, "Query input is required");
      return serializeResult(registration.handler().handle(query, new QueryContext(this, context)), registration.operation());
    } catch (Exception error) {
      Throwable replacement = dispatchError(new FrameworkError(FrameworkError.Stage.QUERY, name, input, context, error));
      if (replacement == null) return NullNode.instance;
      report(replacement); throw asException(replacement, "Query failed: " + name);
    }
  }

  public synchronized JsonNode getReadModel(String name, String id, RequestContext context) {
    return getReadModel(name, id, null, context);
  }
  public synchronized JsonNode getReadModel(String name, String id, Object sequence, RequestContext context) {
    var registration = requireReadModel(name);
    var request = Json.MAPPER.createObjectNode().put("id", id).set("sequence", Json.MAPPER.valueToTree(sequence));
    authorize(registration.policy(), context, request);
    JsonNode changed = applyHooks("readModel", name, request, context);
    String selectedId = changed.path("id").asText();
    if (selectedId.isBlank()) throw new BoosterException(BoosterException.Code.VALIDATION, "Read-model ID is required");
    Object selectedSequence = changed.hasNonNull("sequence") ? changed.get("sequence") : null;
    checkOpen(); catchUp(true);
    if (registration.model().sequenceKey() == null) {
      if (selectedSequence != null) throw new BoosterException(BoosterException.Code.VALIDATION, "Read model has no sequence key");
      return models.get(name).getOrDefault(selectedId, NullNode.instance).deepCopy();
    }
    JsonNode wanted = selectedSequence == null ? null : Json.MAPPER.valueToTree(selectedSequence);
    var matches = models.get(name).values().stream().filter(value -> value.path("id").asText().equals(selectedId))
        .filter(value -> wanted == null || sameSequence(value.get(registration.model().sequenceKey()), wanted)).toList();
    if (matches.size() > 1) throw new BoosterException(BoosterException.Code.VALIDATION, "Multiple read models match; provide sequence key " + registration.model().sequenceKey());
    return matches.isEmpty() ? NullNode.instance : matches.getFirst().deepCopy();
  }
  public synchronized Page listReadModels(String name, QueryOptions options, RequestContext context) {
    var registration = requireReadModel(name);
    JsonNode request = Json.MAPPER.valueToTree(options == null ? QueryOptions.defaults() : options);
    authorize(registration.policy(), context, request);
    QueryOptions selected = convert(applyHooks("readModel", name, request, context), QueryOptions.class);
    checkOpen(); catchUp(true);
    return ReadModelSearch.search(models.get(name), selected);
  }
  private JsonNode serializeResult(Object result, Operation operation) {
    if (operation.outputType() == JsonNode.class) return Json.MAPPER.valueToTree(result);
    try { return Json.MAPPER.readTree(Json.MAPPER.writerFor(Json.MAPPER.constructType(operation.outputSchemaType())).writeValueAsBytes(result)); }
    catch (Exception error) { throw new BoosterException(BoosterException.Code.INTERNAL, "Invalid output for " + operation.name(), error); }
  }
  private ProjectionRegistration requireReadModel(String name) {
    var registration = projections.get(name);
    if (registration == null) throw new BoosterException(BoosterException.Code.NOT_FOUND, "Unknown read model: " + name);
    return registration;
  }
  public synchronized Flow.Publisher<ReadModelChange> subscribe(String name, String id, RequestContext context) {
    return subscribe(name, id, null, context);
  }
  public synchronized Flow.Publisher<ReadModelChange> subscribe(String name, String id, Object sequence, RequestContext context) {
    return subscribe(name, id, sequence, null, context);
  }
  public synchronized Flow.Publisher<ReadModelChange> subscribe(String name, String id, Object sequence, JsonNode filter, RequestContext context) {
    return subscribe(name, id, sequence, filter, context, null);
  }
  /** Apply identical authorization, before hooks and matching to a local or provider-delivered source. */
  public synchronized Flow.Publisher<ReadModelChange> subscribe(String name, String id, Object sequence, JsonNode filter,
      RequestContext context, Flow.Publisher<ReadModelChange> source) {
    var registration = requireReadModel(name); checkOpen();
    var request = Json.MAPPER.createObjectNode().put("id", id);
    request.set("sequence", Json.MAPPER.valueToTree(sequence)); request.set("filter", filter == null ? NullNode.instance : filter);
    authorize(registration.policy(), context, request);
    JsonNode transformed = applyHooks("readModel", name, request, context);
    String wantedId = transformed.hasNonNull("id") ? transformed.path("id").asText() : null;
    JsonNode wantedSequence = transformed.hasNonNull("sequence") ? transformed.get("sequence") : null;
    JsonNode wantedFilter = transformed.get("filter");
    Filters.matches(NullNode.instance, wantedFilter);
    if (wantedSequence != null && registration.model().sequenceKey() == null) throw new BoosterException(BoosterException.Code.VALIDATION, "Read model has no sequence key");
    Flow.Publisher<ReadModelChange> selectedSource = source == null ? publishers.get(name) : source;
    return subscriber -> selectedSource.subscribe(new Flow.Subscriber<>() {
      private Flow.Subscription upstream;
      public void onSubscribe(Flow.Subscription subscription) { upstream = subscription; subscriber.onSubscribe(subscription); }
      public void onNext(ReadModelChange change) {
        boolean matchesFilter = wantedFilter == null || wantedFilter.isNull()
            || change.value() != null && !change.value().isNull() && Filters.matches(change.value(), wantedFilter);
        if (name.equals(change.readModel()) && (wantedId == null || wantedId.equals(change.id())) && (wantedSequence == null || sameSequence(change.sequenceValue(), wantedSequence)) && matchesFilter) subscriber.onNext(change);
        else upstream.request(1);
      }
      public void onError(Throwable error) { subscriber.onError(error); }
      public void onComplete() { subscriber.onComplete(); }
    });
  }
  public synchronized void rebuild() {
    checkOpen(); snapshots.clear(); models.values().forEach(Map::clear); checkpoint = 0; catchUp(false);
  }

  /** Retry committed events' handlers. Durable handler receipts prevent repeating successful work. */
  public synchronized void resumeHandlers() {
    checkOpen(); catchUp(true);
    if (handlers.isEmpty()) return;
    long cursor = 0;
    int produced = 0;
    while (true) {
      var batch = store.readAll(cursor, 256);
      if (batch.isEmpty()) break;
      for (var stored : batch) {
        if (internalEvent(stored.event()) || deletedEvents.containsKey(stored.event().id())) { cursor = stored.position(); continue; }
        Object event = decode(stored.event());
        if (event == SKIPPED_EVENT) { cursor = stored.position(); continue; }
        for (int index = 0; index < handlers.size(); index++) {
          var handler = handlers.get(index);
          if (!handler.type().isInstance(event)) continue;
          String requestId = "__booster_handler:" + stored.event().id() + ":" + handler.name();
          String fingerprint = "handler:" + handler.type().getName() + ":" + handler.name();
          var handled = store.findCommand(requestId);
          if (handled.isPresent()) { verifyFingerprint(handled.get(), fingerprint); continue; }
          if (++produced > 10000) throw new BoosterException(BoosterException.Code.INTERNAL, "Event handler cascade exceeded 10000 invocations");
          var register = new Register(this, new RequestContext(requestId, stored.event().user(), stored.event().metadata()), fingerprint);
          try { handler.handler().accept(event, register); }
          catch (Exception error) {
            Throwable replacement = dispatchError(new FrameworkError(FrameworkError.Stage.EVENT_HANDLER, handler.name(), stored.event().payload(), register.context(), error));
            if (replacement != null) throw asException(replacement, "Event handler failed: " + handler.name());
          }
          commitRegister(requestId, fingerprint, register, NullNode.instance);
          catchUp(true);
        }
        cursor = stored.position();
      }
    }
  }

  private synchronized void validatePending(Register register) {
    checkOpen(); catchUp(true);
    register.validatedPosition = checkpoint;
    register.expectedVersions.forEach((stream, expected) -> {
      long actual = snapshots.getOrDefault(stream, new Snapshot(0, NullNode.instance)).version();
      if (actual != expected) throw new OptimisticConcurrencyException(stream, expected, actual);
    });
    var deleting = register.pending.stream().filter(event -> event.type().equals("__booster_event_deleted")).map(event -> event.payload().path("eventId").asText()).toList();
    if (!deleting.isEmpty()) { validateDeletionReplay(register.pending, new HashSet<>(deleting), register.validatedPosition); return; }
    var stagedStates = new HashMap<StreamId, JsonNode>();
    var stagedModels = copyModels();
    for (var envelope : register.pending) {
      JsonNode previous = stagedStates.containsKey(envelope.stream()) ? stagedStates.get(envelope.stream())
          : snapshots.getOrDefault(envelope.stream(), new Snapshot(0, NullNode.instance)).state();
      JsonNode next = reduceStored(envelope, previous);
      project(envelope.stream(), previous, next, stagedModels);
      stagedStates.put(envelope.stream(), next);
    }
  }

  private CommitResult commitRegister(String requestId, String fingerprint, Register register, JsonNode result) {
    for (int attempt = 0; attempt < 32; attempt++) {
      register.commitGuard.run();
      try { validatePending(register); }
      catch (OptimisticConcurrencyException conflict) {
        var existing = store.findCommand(requestId);
        if (existing.isPresent()) { verifyFingerprint(existing.get(), fingerprint); return new CommitResult(true, existing.get()); }
        throw conflict;
      }
      register.commitGuard.run();
      try { return store.commitAtPosition(requestId, fingerprint, register.expectedVersions, register.pending, result, register.validatedPosition); }
      catch (OptimisticConcurrencyException conflict) {
        // Re-evaluate only deterministic reducers/projections against the newly committed journal.
        // Application handlers and their external effects are never rerun here.
        var existing = store.findCommand(requestId);
        if (existing.isPresent()) { verifyFingerprint(existing.get(), fingerprint); return new CommitResult(true, existing.get()); }
        if (attempt == 31) throw conflict;
      }
    }
    throw new IllegalStateException("Unreachable commit retry boundary");
  }

  void flush(Register register) {
    if (register.pending.isEmpty()) return;
    String requestId = flushReceiptId(register);
    var payload = Json.MAPPER.createArrayNode();
    register.pending.forEach(event -> payload.add(Json.MAPPER.createObjectNode()
        .put("streamType", event.stream().entityType()).put("streamId", event.stream().entityId())
        .put("type", event.type()).put("schemaVersion", event.schemaVersion()).set("payload", event.payload())));
    String fingerprint = hash(register.operationFingerprint + ":" + canonical(payload));
    var existing = store.findCommand(requestId);
    if (existing.isPresent()) verifyFingerprint(existing.get(), fingerprint);
    else {
      commitRegister(requestId, fingerprint, register, register.flushContext());
    }
    register.clearFlushed();
    synchronized (this) { catchUp(true); }
  }

  private static String flushReceiptId(Register register) {
    return "__booster_flush:" + hash(register.context().requestId()) + ":" + register.flushIndex;
  }
  void restoreFlushedPrefix(Register register) {
    var receipt = store.findCommand(flushReceiptId(register));
    if (receipt.isEmpty()) return;
    JsonNode saved = receipt.get().result();
    if (!saved.path("operationFingerprint").asText().equals(register.operationFingerprint))
      throw new BoosterException(BoosterException.Code.CONFLICT, "A partially flushed request belongs to another operation");
    if (!saved.path("states").isArray()) throw new BoosterException(BoosterException.Code.INTERNAL, "Missing partial-flush recovery state");
    for (var state : saved.path("states")) register.restore(new StreamId(state.path("entityType").asText(), state.path("entityId").asText()),
        new Snapshot(state.path("version").asLong(), state.get("state")));
  }

  private Map<String, Map<String,JsonNode>> copyModels() {
    var copy = new LinkedHashMap<String,Map<String,JsonNode>>();
    models.forEach((name, values) -> copy.put(name, new TreeMap<>(values)));
    return copy;
  }

  private record ProjectionTarget(String id, JsonNode sequence) {}
  private void project(StreamId stream, JsonNode previousEntity, JsonNode entity,
      Map<String,Map<String,JsonNode>> target) {
    Class<?> source = entityClass(stream.entityType());
    if (source == null) return;
    Object current = convert(entity, source), previous = convert(previousEntity, source);
    for (var registration : projections.values()) for (var binding : registration.bindings()) {
      if (!binding.source().equals(source)) continue;
      var values = target.get(registration.model().name());
      String sequenceField = registration.model().sequenceKey();
      JsonNode beforeSequence = sequenceOf(previousEntity, sequenceField), afterSequence = sequenceOf(entity, sequenceField);
      if (binding.legacy() && current == null) afterSequence = beforeSequence;
      var beforeKeys = projectionTargets(binding, previous, values, registration.model(), stream.entityId(), beforeSequence);
      var afterKeys = projectionTargets(binding, current, values, registration.model(), stream.entityId(), afterSequence);
      if (binding.legacy() && current == null) afterKeys = beforeKeys;
      var all = new TreeSet<String>(); all.addAll(beforeKeys.keySet()); all.addAll(afterKeys.keySet());
      for (String key : all) {
        boolean removed = !afterKeys.containsKey(key);
        var identity = (removed ? beforeKeys : afterKeys).get(key);
        Object before = convert(values.get(key), registration.model().type());
        var context = new ProjectionContext<>(identity.id(), current, previous, before, removed);
        ProjectionResult<Object> outcome;
        try { outcome = Objects.requireNonNull((removed ? binding.unproject() : binding.project()).apply(context), "Projection outcome required"); }
        catch (Exception error) {
          Throwable replacement = dispatchError(new FrameworkError(FrameworkError.Stage.PROJECTION, registration.model().name(), entity, null, error));
          if (replacement != null) throw asException(replacement, "Projection failed: " + registration.model().name());
          outcome = ProjectionResult.nothing();
        }
        if (outcome.action() == ProjectionResult.Action.NOTHING) continue;
        JsonNode next = outcome.action() == ProjectionResult.Action.DELETE ? NullNode.instance : Json.MAPPER.valueToTree(outcome.value());
        validateIdentity(next, identity.id(), "Read model");
        if (!next.isNull() && sequenceField != null && !sameSequence(sequenceOf(next, sequenceField), identity.sequence()))
          throw new BoosterException(BoosterException.Code.VALIDATION, "Read model sequence must match projection sequence");
        if (next.isNull()) values.remove(key); else values.put(key, next);
      }
    }
  }
  private Map<String,ProjectionTarget> projectionTargets(ProjectionBinding binding, Object entity,
      Map<String,JsonNode> values, ReadModel model, String streamId, JsonNode sequence) {
    if (entity == null) return Map.of();
    var typed = new TreeMap<String,Object>();
    values.forEach((key, value) -> {
      if (model.sequenceKey() == null || sameSequence(sequenceOf(value, model.sequenceKey()), sequence))
        typed.put(model.sequenceKey() == null ? key : value.path("id").asText(), convert(value, model.type()));
    });
    var keys = binding.legacy() ? Set.of(streamId) : projectionKeys(binding.join().keys(entity, Collections.unmodifiableMap(typed)));
    var result = new LinkedHashMap<String,ProjectionTarget>();
    for (String id : keys) result.put(model.sequenceKey() == null ? id : Join.composite(id, sequence), new ProjectionTarget(id, sequence));
    return result;
  }
  private static JsonNode sequenceOf(JsonNode value, String field) {
    if (field == null || value == null || value.isNull()) return null;
    JsonNode sequence = value.get(field);
    if (sequence == null || sequence.isNull() || !(sequence.isNumber() || sequence.isTextual() || sequence.isBoolean()))
      throw new BoosterException(BoosterException.Code.VALIDATION, "Sequence key " + field + " requires a scalar value");
    return sequence;
  }
  private static boolean sameSequence(JsonNode first, JsonNode second) {
    if (first == null || second == null) return first == second;
    return first.isNumber() && second.isNumber() ? first.decimalValue().compareTo(second.decimalValue()) == 0 : first.equals(second);
  }
  private static Set<String> projectionKeys(Collection<String> keys) {
    if (keys == null) throw new IllegalArgumentException("Projection join returned null keys");
    var result = new TreeSet<String>();
    for (String key : keys) {
      if (key == null || key.isBlank() || key.length() > 2048) throw new IllegalArgumentException("Invalid projection join key");
      result.add(key);
    }
    if (result.size() > 10000) throw new IllegalArgumentException("Projection fan-out exceeds 10000 keys");
    return result;
  }
  private Class<?> entityClass(String name) {
    return entities.entrySet().stream().filter(entry -> entry.getValue().equals(name)).map(Map.Entry::getKey).findFirst().orElse(null);
  }
  static boolean internalEvent(PendingEvent event) { return event.type().startsWith("__booster_"); }
  JsonNode reduceStored(PendingEvent envelope, JsonNode previous) {
    JsonNode next;
    switch (envelope.type()) {
      case "__booster_entity_delete" -> next = NullNode.instance;
      case "__booster_entity_touch", "__booster_event_deleted", "__booster_schema_upgraded" -> next = previous.deepCopy();
      case "__booster_entity_replace" -> {
        var type = entityClass(envelope.stream().entityType());
        if (type == null) throw new BoosterException(BoosterException.Code.INTERNAL, "Migrated entity type is not registered");
        next = migrateSchema(type, envelope.schemaVersion(), envelope.payload());
        convert(next, type);
      }
      default -> {
        Object decoded = decode(envelope);
        next = decoded == SKIPPED_EVENT ? previous.deepCopy() : reduce(eventsByName.get(envelope.type()), decoded, previous);
      }
    }
    validateIdentity(next, envelope.stream().entityId(), "Entity");
    return next;
  }
  int schemaVersion(Class<?> type) { return type == null ? 1 : schemaVersions.getOrDefault(type, 1); }

  public AutoCloseable schedule(String name, Object command, Duration interval, UserContext user) {
    Objects.requireNonNull(command); Objects.requireNonNull(interval); Objects.requireNonNull(user);
    if (name == null || name.isBlank() || interval.isNegative() || interval.isZero() || interval.toMillis() < 1)
      throw new IllegalArgumentException("Schedule name and positive interval are required");
    String commandName = command.getClass().getSimpleName();
    var registration = commands.get(commandName);
    if (registration == null) throw new IllegalArgumentException("Unregistered scheduled command");
    authorize(registration.policy(), new RequestContext(null, user, Map.of()));
    synchronized (this) { checkOpen(); }
    JsonNode input = Json.MAPPER.valueToTree(command);
    var future = scheduler.scheduleAtFixedRate(() -> {
      try { executeCommand(commandName, input, new RequestContext(null, user, Map.of("schedule", name))); }
      catch (Exception error) { report(error); }
    }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    return () -> future.cancel(false);
  }

  StreamId stream(Class<?> type, String id) {
    var name = entities.get(type);
    if (name == null) throw new IllegalArgumentException("Unregistered entity: " + type.getName());
    return new StreamId(name, id);
  }
  synchronized Snapshot snapshot(StreamId stream) {
    checkOpen(); catchUp(true);
    var cached = snapshots.getOrDefault(stream, new Snapshot(0, NullNode.instance));
    return new Snapshot(cached.version(), cached.state().deepCopy());
  }
  <S> S loadEntity(Class<S> type, String id) { return convert(snapshot(stream(type, id)).state(), type); }
  public List<StoredEvent> history(Class<?> entity, String id) { return store.readStream(stream(entity, id), 0); }
  EventDefinition eventDefinition(Class<?> type) {
    var definition = events.get(type);
    if (definition == null) throw new BoosterException(BoosterException.Code.VALIDATION, "Unregistered event: " + type.getName());
    return definition;
  }
  JsonNode reduce(EventDefinition definition, Object event, JsonNode previous) {
    if (definition.notification()) return previous.deepCopy();
    Object entity = convert(previous, definition.entityType());
    Object state;
    try { state = definition.reducer().apply(event, entity); }
    catch (Exception error) {
      Throwable replacement = dispatchError(new FrameworkError(FrameworkError.Stage.REDUCER, definition.name(), Json.MAPPER.valueToTree(event), null, error));
      // A reducer has no meaningful fallback state; explicit suppression is not supported here.
      throw asException(replacement == null ? error : replacement, "Reducer failed: " + definition.name());
    }
    JsonNode result = Json.MAPPER.valueToTree(state);
    validateIdentity(result, definition.entityId().apply(event), "Entity");
    return result;
  }
  private static void validateIdentity(JsonNode state, String id, String kind) {
    if (state == null || state.isNull() || !state.has("id")) return;
    JsonNode identity = state.get("id");
    if (identity.isNull() || !identity.isValueNode() || !identity.asText().equals(id))
      throw new BoosterException(BoosterException.Code.VALIDATION, kind + " id must match its event stream ID");
  }
  <T> T convert(JsonNode node, Class<T> type) {
    if (node == null || node.isNull()) return null;
    try { validateIntegralInput(node, type); return Json.MAPPER.treeToValue(node, type); }
    catch (Exception error) { throw new BoosterException(BoosterException.Code.VALIDATION, "Invalid input for " + type.getSimpleName(), error); }
  }
  private static void validateIntegralInput(JsonNode node, java.lang.reflect.Type target) {
    if (target instanceof java.lang.reflect.ParameterizedType parameterized) {
      var raw = parameterized.getRawType();
      if (raw instanceof Class<?> rawClass && Collection.class.isAssignableFrom(rawClass) && node != null && node.isArray())
        for (var value : node) validateIntegralInput(value, parameterized.getActualTypeArguments()[0]);
      else if (raw instanceof Class<?> rawClass && Map.class.isAssignableFrom(rawClass) && node != null && node.isObject())
        for (var value : node) validateIntegralInput(value, parameterized.getActualTypeArguments()[1]);
      return;
    }
    if (!(target instanceof Class<?> type)) return;
    boolean integral = type == byte.class || type == Byte.class || type == short.class || type == Short.class
        || type == int.class || type == Integer.class || type == long.class || type == Long.class || type == java.math.BigInteger.class;
    if (node == null || node.isNull()) {
      if (type.isPrimitive()) throw new IllegalArgumentException("A primitive input field is required");
      return;
    }
    if (integral) {
      if (!node.isNumber()) throw new IllegalArgumentException("An integer input must be numeric");
      java.math.BigInteger value = node.decimalValue().toBigIntegerExact();
      if (type == byte.class || type == Byte.class) value.byteValueExact();
      else if (type == short.class || type == Short.class) value.shortValueExact();
      else if (type == int.class || type == Integer.class) value.intValueExact();
      else if (type == long.class || type == Long.class) value.longValueExact();
    } else if (type.isRecord() && node.isObject()) {
      for (var component : type.getRecordComponents()) validateIntegralInput(node.get(component.getName()), component.getGenericType());
    } else if (type.isArray() && node.isArray()) {
      for (var value : node) validateIntegralInput(value, type.getComponentType());
    }
  }
  private Object decode(PendingEvent event) {
    var definition = eventsByName.get(event.type());
    if (definition == null) {
      var error = new BoosterException(BoosterException.Code.INTERNAL, "No registered type for stored event " + event.type());
      Throwable replacement = dispatchError(new FrameworkError(FrameworkError.Stage.EVENT, event.type(), event.payload(),
          new RequestContext(event.metadata().get("requestId"), event.user(), event.metadata()), error));
      if (replacement == null) return SKIPPED_EVENT;
      throw asException(replacement, "Invalid stored event " + event.type());
    }
    if (!event.stream().entityType().equals(definition.entityName())) throw new BoosterException(BoosterException.Code.INTERNAL, "Stored event has incompatible entity type");
    if (event.schemaVersion() > definition.version()) throw new BoosterException(BoosterException.Code.INTERNAL, "Stored event schema is newer than this application");
    JsonNode payload = event.payload().deepCopy();
    for (int version = event.schemaVersion(); version < definition.version(); version++) {
      var upgrade = upcasters.getOrDefault(definition.type(), Map.of()).get(version);
      if (upgrade == null) throw new BoosterException(BoosterException.Code.INTERNAL, "Missing migration for " + event.type() + " version " + version);
      payload = Objects.requireNonNull(upgrade.apply(payload), "An event migration returned null");
    }
    Object decoded = convert(payload, definition.type());
    if (!Objects.equals(definition.entityId().apply(decoded), event.stream().entityId()))
      throw new BoosterException(BoosterException.Code.INTERNAL, "Migration changed stored event entity ID");
    return decoded;
  }
  private void catchUp(boolean notify) {
    assertSchemaCompatibility(); indexDeletions();
    boolean rebuilding = deletionsDirty || schemasDirty;
    Map<String,Map<String,JsonNode>> beforeRebuild = rebuilding ? copyModels() : null;
    if (rebuilding) { snapshots.clear(); models.values().forEach(Map::clear); checkpoint = 0; }
    while (true) {
      var batch = store.readAll(checkpoint, 256);
      if (batch.isEmpty()) break;
      for (var stored : batch) {
        if (stored.position() != checkpoint + 1) throw new BoosterException(BoosterException.Code.INTERNAL, "Event journal contains a position gap");
        var envelope = stored.event();
        var previous = snapshots.getOrDefault(envelope.stream(), new Snapshot(0, NullNode.instance));
        if (stored.streamVersion() != previous.version() + 1) throw new BoosterException(BoosterException.Code.INTERNAL, "Event stream contains a version gap");
        boolean deleted = deletedEvents.containsKey(envelope.id()) || envelope.type().equals("__booster_event_deleted");
        JsonNode next = deleted ? previous.state().deepCopy() : reduceStored(envelope, previous.state());
        var projected = copyModels();
        var stagedSnapshots = new HashMap<>(snapshots);
        stagedSnapshots.put(envelope.stream(), new Snapshot(stored.streamVersion(), next));
        var transitions = new LinkedHashMap<StreamId,JsonNode>();
        if (!deleted && (internalEvent(envelope) || eventsByName.containsKey(envelope.type()))) transitions.put(envelope.stream(), next);
        applyProjectionsAndSchemas(stored.position(), snapshots, stagedSnapshots, projected, transitions, deletions.values());
        var updates = new ArrayList<ReadModelChange>();
        projected.forEach((name, values) -> {
          var keys = new TreeSet<String>(); keys.addAll(models.get(name).keySet()); keys.addAll(values.keySet());
          for (String id : keys) {
            JsonNode before = models.get(name).getOrDefault(id, NullNode.instance), after = values.getOrDefault(id, NullNode.instance);
            if (!before.equals(after)) {
              persistDerived("readModels:" + name, id, stored.position(), schemaVersion(projections.get(name).model().type()), after);
              var model = projections.get(name).model();
              JsonNode identity = after.isNull() ? before : after;
              String logicalId = model.sequenceKey() == null ? id : identity.path("id").asText();
              updates.add(new ReadModelChange(name, logicalId, after, stored.position(), model.sequenceKey(), sequenceOf(identity, model.sequenceKey())));
            }
          }
        });
        var changedStreams = new HashSet<>(transitions.keySet()); changedStreams.add(envelope.stream());
        for (var stream : changedStreams) {
          if (entityClass(stream.entityType()) != null) persistDerived("snapshots:" + stream.entityType(), stream.entityId(), stored.position(),
              schemaVersion(entityClass(stream.entityType())), stagedSnapshots.get(stream).state());
        }
        snapshots.clear(); snapshots.putAll(stagedSnapshots);
        models.clear(); models.putAll(projected);
        checkpoint = stored.position();
        if (notify && !rebuilding) for (var update : updates) publishers.get(update.readModel()).offer(update,
            (subscriber, change) -> { report(new IllegalStateException("Slow subscription dropped update for " + update.readModel())); return false; });
      }
    }
    if (rebuilding) {
      refreshDerivedAfterDeletion();
      if (notify) publishDifference(beforeRebuild, models);
      deletionsDirty = false; schemasDirty = false;
    }
  }

  private void indexDeletions() {
    while (true) {
      var page = store.readAll(indexedPosition, 256); if (page.isEmpty()) break;
      for (var stored : page) {
        if (stored.event().type().equals("__booster_event_deleted")) {
          String id = stored.event().payload().path("eventId").asText();
          if (id.isBlank()) throw new BoosterException(BoosterException.Code.INTERNAL, "Invalid deletion marker");
          if (deletedEvents.putIfAbsent(id, stored.event().timestamp()) == null) deletionsDirty = true;
          var target = findStoredEvent(id).orElseThrow(() -> new BoosterException(BoosterException.Code.INTERNAL, "Deletion references an absent event"));
          deletions.putIfAbsent(id, new Deletion(target.event().stream(), target.position(), stored.position(), stored.event().timestamp()));
        } else if (stored.event().type().equals("__booster_schema_upgraded")) {
          if (stored.event().payload().isObject() && stored.event().payload().isEmpty()
              && stored.event().metadata().containsKey("deletedAt") && stored.event().metadata().containsKey("__booster_schemaDescriptor")) {
            redactedSchemaEvents.add(stored.event().id()); indexedPosition = stored.position(); continue;
          }
          JsonNode value = stored.event().payload();
          String namespace = value.path("namespace").asText(), key = value.path("key").asText();
          long source = value.path("sourcePosition").asLong(-1); int version = value.path("schemaVersion").asInt(0);
          if ((!namespace.startsWith("snapshots:") && !namespace.startsWith("readModels:")) || key.isBlank() || source < 1 || source >= stored.position() || version < 1 || !value.has("state"))
            throw new BoosterException(BoosterException.Code.INTERNAL, "Invalid schema boundary record");
          var sources = new TreeSet<String>(); value.path("sourceTypes").forEach(type -> sources.add(type.asText()));
          schemaAnchors.computeIfAbsent(source, ignored -> new ArrayList<>()).add(new SchemaAnchor(stored.event().id(), namespace, key, version, source, value.get("state"), Set.copyOf(sources)));
          schemasDirty = true;
        }
        indexedPosition = stored.position();
      }
    }
    for (var deleted : deletedEvents.entrySet()) if (!redactedEvents.contains(deleted.getKey())) {
      if (!store.redactEventPayload(deleted.getKey(), deleted.getValue()))
        throw new BoosterException(BoosterException.Code.INTERNAL, "Deletion references an absent event");
      redactedEvents.add(deleted.getKey());
    }
    for (var anchors : schemaAnchors.values()) for (var anchor : anchors) {
      if (validAnchor(anchor) || redactedSchemaEvents.contains(anchor.eventId())) continue;
      var invalidatedAt = deletions.values().stream().filter(deletion -> invalidates(anchor, deletion))
          .map(Deletion::timestamp).min(java.time.Instant::compareTo).orElseThrow();
      if (!store.redactEventPayload(anchor.eventId(), invalidatedAt)) throw new BoosterException(BoosterException.Code.INTERNAL, "Schema boundary is absent");
      redactedSchemaEvents.add(anchor.eventId());
    }
  }
  private void applyProjectionsAndSchemas(long position, Map<StreamId,Snapshot> beforeSnapshots,
      Map<StreamId,Snapshot> stagedSnapshots, Map<String,Map<String,JsonNode>> projected,
      Map<StreamId,JsonNode> transitions, Collection<Deletion> effectiveDeletions) {
    for (var anchor : schemaAnchors.getOrDefault(position, List.of())) {
      if (!anchor.namespace().startsWith("snapshots:") || !validAnchor(anchor, effectiveDeletions)) continue;
      String typeName = anchor.namespace().substring("snapshots:".length());
      Class<?> type = entityClass(typeName); if (type == null) continue;
      var stream = new StreamId(typeName, anchor.key());
      JsonNode upgraded = anchor.state().isNull() ? NullNode.instance : migrateSchema(type, anchor.version(), anchor.state());
      validateIdentity(upgraded, anchor.key(), "Migrated entity");
      long version = stagedSnapshots.getOrDefault(stream, new Snapshot(0, NullNode.instance)).version();
      stagedSnapshots.put(stream, new Snapshot(version, upgraded)); transitions.put(stream, upgraded);
    }
    transitions.forEach((stream, value) -> project(stream, beforeSnapshots.getOrDefault(stream, new Snapshot(0, NullNode.instance)).state(), value, projected));
    for (var anchor : schemaAnchors.getOrDefault(position, List.of())) {
      if (!anchor.namespace().startsWith("readModels:") || !validAnchor(anchor, effectiveDeletions)) continue;
      String name = anchor.namespace().substring("readModels:".length()); var registration = projections.get(name); if (registration == null) continue;
      JsonNode upgraded = anchor.state().isNull() ? NullNode.instance : migrateSchema(registration.model().type(), anchor.version(), anchor.state());
      if (upgraded.isNull()) projected.get(name).remove(anchor.key()); else projected.get(name).put(anchor.key(), upgraded);
    }
  }

  private boolean validAnchor(SchemaAnchor anchor) {
    return validAnchor(anchor, deletions.values());
  }
  private boolean validAnchor(SchemaAnchor anchor, Collection<Deletion> effectiveDeletions) {
    return effectiveDeletions.stream().noneMatch(deletion -> invalidates(anchor, deletion));
  }
  private boolean invalidates(SchemaAnchor anchor, Deletion deletion) {
    // An artifact rebuilt at or after the marker already excludes the deleted event.
    if (deletion.eventPosition() > anchor.sourcePosition() || deletion.markerPosition() <= anchor.sourcePosition()) return false;
    if (anchor.namespace().startsWith("snapshots:"))
      return deletion.stream().equals(new StreamId(anchor.namespace().substring("snapshots:".length()), anchor.key()));
    return anchor.sourceTypes().contains(deletion.stream().entityType());
  }
  private Set<String> anchorSources(String namespace) {
    if (namespace.startsWith("snapshots:")) return Set.of(namespace.substring("snapshots:".length()));
    var registration = projections.get(namespace.substring("readModels:".length()));
    var result = new TreeSet<String>();
    if (registration != null) registration.bindings().forEach(binding -> result.add(entities.get(binding.source())));
    return Set.copyOf(result);
  }
  private void appendSchemaAnchor(String namespace, String key, int sourceVersion, int targetVersion, long position, JsonNode value) {
    if (position < 1 || position > store.lastPosition()) throw new BoosterException(BoosterException.Code.VALIDATION, "Persisted state references an absent journal position");
    var payload = Json.MAPPER.createObjectNode().put("namespace", namespace).put("key", key)
        .put("sourceVersion", sourceVersion).put("schemaVersion", targetVersion).put("sourcePosition", position);
    payload.set("state", value); payload.set("sourceTypes", Json.MAPPER.valueToTree(anchorSources(namespace)));
    String identity = namespace + ":" + key + ":" + sourceVersion + ":" + targetVersion + ":" + position;
    String requestId = "__booster_schema:" + hash(identity), fingerprint = hash(canonical(payload));
    var stream = new StreamId("__booster_schema", hash(namespace + ":" + key));
    for (int attempt = 0; attempt < 32; attempt++) {
      var existing = store.findCommand(requestId);
      if (existing.isPresent()) { verifyFingerprint(existing.get(), fingerprint); return; }
      long head = store.lastPosition();
      var history = store.readStream(stream, 0); long expected = history.isEmpty() ? 0 : history.getLast().streamVersion();
      var event = new PendingEvent(UUID.randomUUID().toString(), stream, "__booster_schema_upgraded", 1, payload,
          java.time.Instant.now(), UserContext.anonymous(), Map.of("requestId", requestId));
      try { store.commitAtPosition(requestId, fingerprint, Map.of(stream, expected), List.of(event), NullNode.instance, head); return; }
      catch (OptimisticConcurrencyException conflict) { if (attempt == 31) throw conflict; }
    }
  }

  private void refreshDerivedAfterDeletion() {
    for (var projection : projections.values()) {
      String name = projection.model().name(), namespace = "readModels:" + name;
      var ids = new TreeSet<>(models.get(name).keySet()); String cursor = null;
      while (true) {
        var page = stateStore.scan(namespace, cursor, 256); if (page.isEmpty()) break;
        page.forEach(document -> ids.add(document.key())); cursor = page.getLast().key();
      }
      for (String id : ids) persistDerived(namespace, id, checkpoint, schemaVersion(projection.model().type()), models.get(name).getOrDefault(id, NullNode.instance));
    }
    snapshots.forEach((stream, snapshot) -> {
      if (entityClass(stream.entityType()) != null) persistDerived("snapshots:" + stream.entityType(), stream.entityId(), checkpoint,
          schemaVersion(entityClass(stream.entityType())), snapshot.state());
    });
  }
  private void publishDifference(Map<String,Map<String,JsonNode>> before, Map<String,Map<String,JsonNode>> after) {
    for (var registration : projections.values()) {
      String name = registration.model().name(); var keys = new TreeSet<String>(); keys.addAll(before.get(name).keySet()); keys.addAll(after.get(name).keySet());
      for (String key : keys) {
        JsonNode oldValue = before.get(name).getOrDefault(key, NullNode.instance), value = after.get(name).getOrDefault(key, NullNode.instance);
        if (oldValue.equals(value)) continue;
        JsonNode identity = value.isNull() ? oldValue : value;
        var model = registration.model();
        var change = new ReadModelChange(name, model.sequenceKey() == null ? key : identity.path("id").asText(), value, checkpoint,
            model.sequenceKey(), sequenceOf(identity, model.sequenceKey()));
        publishers.get(name).offer(change, (subscriber, dropped) -> { report(new IllegalStateException("Slow subscription dropped update for " + name)); return false; });
      }
    }
  }
  Optional<StoredEvent> findStoredEvent(String eventId) {
    long cursor = 0;
    while (true) {
      var page = store.readAll(cursor, 256); if (page.isEmpty()) return Optional.empty();
      for (var stored : page) { if (stored.event().id().equals(eventId)) return Optional.of(stored); cursor = stored.position(); }
    }
  }
  boolean eventDeleted(String id) { synchronized (this) { catchUp(true); return deletedEvents.containsKey(id); } }
  private void validateDeletionReplay(List<PendingEvent> pending, Set<String> removing, long untilPosition) {
    var omitted = new HashSet<>(deletedEvents.keySet()); omitted.addAll(removing);
    var effectiveDeletions = new HashMap<>(deletions);
    record ReplayEntry(long position, PendingEvent event) {}
    var all = new ArrayList<ReplayEntry>(); long cursor = 0;
    while (true) {
      var page = store.readAll(cursor, 256); if (page.isEmpty()) break;
      for (var stored : page) {
        if (stored.position() <= untilPosition) {
          all.add(new ReplayEntry(stored.position(), stored.event()));
          if (removing.contains(stored.event().id())) effectiveDeletions.putIfAbsent(stored.event().id(),
              new Deletion(stored.event().stream(), stored.position(), untilPosition + 1, java.time.Instant.EPOCH));
        }
        cursor = stored.position();
      }
      if (cursor >= untilPosition) break;
    }
    long position = untilPosition;
    for (var event : pending) all.add(new ReplayEntry(++position, event));
    var states = new HashMap<StreamId,Snapshot>(); var projected = new LinkedHashMap<String,Map<String,JsonNode>>();
    projections.keySet().forEach(name -> projected.put(name, new TreeMap<>()));
    for (var item : all) {
      var event = item.event(); var previous = states.getOrDefault(event.stream(), new Snapshot(0, NullNode.instance));
      boolean deleted = omitted.contains(event.id()) || event.type().equals("__booster_event_deleted");
      JsonNode next = deleted ? previous.state() : reduceStored(event, previous.state());
      var staged = new HashMap<>(states); staged.put(event.stream(), new Snapshot(previous.version() + 1, next));
      var transitions = new LinkedHashMap<StreamId,JsonNode>();
      if (!deleted && (internalEvent(event) || eventsByName.containsKey(event.type()))) transitions.put(event.stream(), next);
      applyProjectionsAndSchemas(item.position(), states, staged, projected, transitions, effectiveDeletions.values());
      states = staged;
    }
  }
  public boolean deleteEvent(String entityType, String entityId, java.time.Instant createdAt, RequestContext context) {
    if (!entities.containsValue(entityType)) throw new BoosterException(BoosterException.Code.NOT_FOUND, "Unknown entity");
    Objects.requireNonNull(createdAt);
    var input = Json.MAPPER.createObjectNode().put("entity", entityType).put("entityID", entityId).put("createdAt", createdAt.toString());
    return executeAction("deleteEvent", input, context, administrationPolicies.getOrDefault(entityType, AccessPolicy.deny()), (value, register) -> {
      boolean deleted = false;
      for (var event : store.readStream(new StreamId(entityType, entityId), 0))
        if (event.event().timestamp().equals(createdAt) && !internalEvent(event.event())) deleted |= register.deleteEvent(event.event().id());
      return deleted;
    }).asBoolean();
  }

  private Map<String,Integer> applicationSchemas() {
    var result = new TreeMap<String,Integer>();
    entities.forEach((type, name) -> result.put("entity:" + name, schemaVersion(type)));
    events.values().forEach(event -> result.put("event:" + event.name(), event.version()));
    projections.forEach((name, registration) -> result.put("readModel:" + name, schemaVersion(registration.model().type())));
    commands.forEach((name, registration) -> result.put("command:" + name, schemaVersion(registration.operation().inputType())));
    return result;
  }
  private void assertSchemaCompatibility() {
    var current = stateStore.get("__booster_schema", "registry");
    if (current.isEmpty()) return;
    JsonNode versions = current.get().value();
    applicationSchemas().forEach((name, version) -> {
      if (versions.path(name).asInt(1) > version) throw new BoosterException(BoosterException.Code.VALIDATION, "Application schema is stale for " + name);
    });
  }
  private void publishSchemaRegistry() {
    var application = applicationSchemas();
    for (int attempt = 0; attempt < 32; attempt++) {
      var current = stateStore.get("__booster_schema", "registry");
      var versions = current.isEmpty() ? Json.MAPPER.createObjectNode() : (com.fasterxml.jackson.databind.node.ObjectNode) current.get().value();
      boolean changed = false;
      for (var type : application.entrySet()) {
        int prior = versions.path(type.getKey()).asInt(0);
        if (prior > type.getValue()) throw new BoosterException(BoosterException.Code.VALIDATION, "Application schema is stale for " + type.getKey());
        if (prior < type.getValue()) { versions.put(type.getKey(), type.getValue()); changed = true; }
      }
      if (!changed && current.isPresent()) return;
      try { stateStore.put("__booster_schema", "registry", current.map(StateDocument::version).orElse(0L), versions); return; }
      catch (OptimisticConcurrencyException conflict) { if (attempt == 31) throw conflict; }
    }
  }

  private void migrateDerivedSchemas() {
    indexDeletions(); publishSchemaRegistry();
    var types = new LinkedHashMap<String,Class<?>>();
    entities.forEach((type, name) -> types.put("snapshots:" + name, type));
    projections.forEach((name, registration) -> types.put("readModels:" + name, registration.model().type()));
    for (var entry : types.entrySet()) {
      String cursor = null;
      while (true) {
        var page = stateStore.scan(entry.getKey(), cursor, 256);
        if (page.isEmpty()) break;
        for (var stored : page) {
          for (int attempt = 0; attempt < 32; attempt++) {
            var current = stateStore.get(stored.namespace(), stored.key()).orElseThrow();
            if (current.deleted()) break;
            JsonNode document = current.value();
            if (!document.isObject() || !document.has("state")) throw new BoosterException(BoosterException.Code.VALIDATION, "Invalid persisted state envelope");
            int from = document.path("schemaVersion").asInt(1), target = schemaVersion(entry.getValue());
            if (from < 1 || from > target) throw new BoosterException(BoosterException.Code.VALIDATION, "Invalid or future persisted schema for " + entry.getKey());
            if (from == target) break;
            var proposed = new SchemaAnchor("", current.namespace(), current.key(), target, document.path("position").asLong(-1), document.path("state"), anchorSources(current.namespace()));
            if (!validAnchor(proposed)) { deletionsDirty = true; break; }
            JsonNode previousState = document.path("state");
            JsonNode migrated = previousState.isNull() ? NullNode.instance : migrateSchema(entry.getValue(), from, previousState);
            if (previousState.hasNonNull("id")) validateIdentity(migrated, previousState.path("id").asText(), "Migrated state");
            convert(migrated, entry.getValue());
            if (entry.getKey().startsWith("readModels:") && !previousState.isNull()) {
              var model = projections.get(entry.getKey().substring("readModels:".length())).model();
              if (model.sequenceKey() != null && !sameSequence(sequenceOf(previousState, model.sequenceKey()), sequenceOf(migrated, model.sequenceKey())))
                throw new BoosterException(BoosterException.Code.VALIDATION, "Schema migration changed a read-model sequence key");
            }
            appendSchemaAnchor(current.namespace(), current.key(), from, target, document.path("position").asLong(-1), migrated);
            var next = (com.fasterxml.jackson.databind.node.ObjectNode) document.deepCopy();
            next.put("schemaVersion", target); next.set("state", migrated);
            try { stateStore.put(current.namespace(), current.key(), current.version(), next); break; }
            catch (OptimisticConcurrencyException conflict) { if (attempt == 31) throw conflict; }
          }
        }
        cursor = page.getLast().key();
      }
    }
  }

  private void persistDerived(String namespace, String key, long position, int schemaVersion, JsonNode value) {
    try { persistDerivedUnchecked(namespace, key, position, schemaVersion, value); }
    catch (Exception error) {
      Throwable replacement = dispatchError(new FrameworkError(FrameworkError.Stage.SNAPSHOT, namespace + "/" + key, value, null, error));
      if (replacement != null) throw asException(replacement, "Persisting derived state failed");
    }
  }
  private void persistDerivedUnchecked(String namespace, String key, long position, int schemaVersion, JsonNode value) {
    for (int attempt = 0; attempt < 32; attempt++) {
      var previous = stateStore.get(namespace, key);
      JsonNode existing = previous.map(StateDocument::value).orElse(NullNode.instance);
      if (existing.path("schemaVersion").asInt(1) > schemaVersion) throw new BoosterException(BoosterException.Code.VALIDATION, "A newer application schema already owns derived state " + namespace);
      if (existing.path("position").asLong(-1) > position) return;
      if (existing.path("position").asLong(-1) == position && existing.path("schemaVersion").asInt() >= schemaVersion) return;
      var next = Json.MAPPER.createObjectNode().put("position", position).put("schemaVersion", schemaVersion);
      next.set("state", value);
      if (namespace.startsWith("readModels:")) {
        var model = projections.get(namespace.substring("readModels:".length())).model();
        JsonNode identity = value.isNull() ? existing.path("state") : value;
        next.put("id", model.sequenceKey() == null ? key : identity.path("id").asText(existing.path("id").asText()));
        if (model.sequenceKey() != null) {
          next.put("sequenceKey", model.sequenceKey());
          next.set("sequenceValue", value.isNull() ? existing.path("sequenceValue") : sequenceOf(value, model.sequenceKey()));
        }
      }
      try { stateStore.put(namespace, key, previous.map(StateDocument::version).orElse(0L), next); return; }
      catch (OptimisticConcurrencyException conflict) { if (attempt == 31) throw conflict; }
    }
  }

  private static void authorize(AccessPolicy policy, RequestContext context) {
    Objects.requireNonNull(context, "Request context is required");
    if (!policy.permits(context, null)) throw new BoosterException(BoosterException.Code.FORBIDDEN, "Operation is not authorized");
  }
  private static void authorize(AccessPolicy policy, RequestContext context, JsonNode input) {
    Objects.requireNonNull(context, "Request context is required");
    if (!policy.permits(context, input)) throw new BoosterException(BoosterException.Code.FORBIDDEN, "Operation is not authorized");
  }
  private JsonNode applyHooks(String kind, String name, JsonNode input, RequestContext context) {
    JsonNode result = input == null ? NullNode.instance : input.deepCopy();
    for (var hook : inputHooks.getOrDefault(kind + ":" + name, List.of()))
      result = Objects.requireNonNull(hook.apply(result, context), "Before hook returned null");
    return result;
  }
  private Throwable dispatchError(FrameworkError error) {
    try {
      Throwable replacement = typedErrorHandlers.containsKey(error.stage()) ? typedErrorHandlers.get(error.stage()).apply(error) : error.error();
      if (replacement == null || globalErrorHandler == null) return replacement;
      return globalErrorHandler.apply(new FrameworkError(error.stage(), error.operation(), error.input(), error.context(), replacement));
    } catch (Exception failure) { return failure; }
  }
  private static RuntimeException asException(Throwable error, String message) {
    return error instanceof RuntimeException runtime ? runtime : new BoosterException(BoosterException.Code.INTERNAL, message, error);
  }
  private static void verifyFingerprint(CommandReceipt receipt, String fingerprint) {
    if (!receipt.fingerprint().equals(fingerprint)) throw new BoosterException(BoosterException.Code.CONFLICT, "Request ID was already used for another operation");
  }
  private static String fingerprint(String name, JsonNode input, RequestContext context) {
    var user = context.user();
    var identity = Json.MAPPER.createObjectNode();
    identity.put("operation", name); identity.set("input", input == null ? NullNode.instance : input);
    identity.put("subject", user.subject()); identity.set("roles", Json.MAPPER.valueToTree(new TreeSet<>(user.roles())));
    if ((user.subject() == null || user.subject().isBlank()) && user.authenticated()) identity.put("authenticated", true);
    identity.set("claims", Json.MAPPER.valueToTree(user.claims())); identity.set("metadata", Json.MAPPER.valueToTree(context.metadata()));
    var ignoredHeaders = Set.of("authorization", "proxy-authorization", "host", "content-length", "connection", "transfer-encoding", "user-agent", "accept-encoding", "idempotency-key", "x-request-id", "traceparent", "tracestate", "date");
    var headers = new TreeMap<String,List<String>>();
    context.request().headers().forEach((headerName, values) -> { if (!ignoredHeaders.contains(headerName.toLowerCase(Locale.ROOT))) headers.put(headerName.toLowerCase(Locale.ROOT), values); });
    if (!headers.isEmpty()) identity.put("requestHeadersHash", hash(canonical(Json.MAPPER.valueToTree(headers))));
    if (!context.request().body().isNull()) identity.put("requestBodyHash", hash(canonical(context.request().body())));
    return hash(canonical(identity));
  }
  static String canonical(JsonNode value) {
    if (value == null) return "null";
    if (value.isObject()) {
      var sorted = Json.MAPPER.createObjectNode();
      var names = new TreeSet<String>(); value.fieldNames().forEachRemaining(names::add);
      names.forEach(name -> { try { sorted.set(name, Json.MAPPER.readTree(canonical(value.get(name)))); } catch (Exception error) { throw new IllegalStateException(error); } });
      return sorted.toString();
    }
    if (value.isArray()) {
      var result = Json.MAPPER.createArrayNode();
      value.forEach(item -> { try { result.add(Json.MAPPER.readTree(canonical(item))); } catch (Exception error) { throw new IllegalStateException(error); } });
      return result.toString();
    }
    return value.toString();
  }
  static String hash(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (Exception error) { throw new IllegalStateException(error); }
  }
  private void report(Throwable error) { try { errorHandler.accept(error); } catch (Exception ignored) { /* Reporting must not mask the original error. */ } }
  @Override public synchronized void close() {
    if (closed) return;
    closed = true; scheduler.shutdownNow(); publishers.values().forEach(SubmissionPublisher::close); store.close(); stateStore.close();
  }

  public static final class Builder {
    private final EventStore store;
    private StateStore stateStore = new InMemoryStateStore();
    private final Map<Class<?>, Integer> schemaVersions = new HashMap<>();
    private final Map<Class<?>, String> entities = new LinkedHashMap<>();
    private final Map<Class<?>, EventDefinition> events = new LinkedHashMap<>();
    private final Map<String, CommandRegistration> commands = new LinkedHashMap<>();
    private final Map<String, QueryRegistration> queries = new LinkedHashMap<>();
    private final Map<String, FunctionRegistration> functions = new LinkedHashMap<>();
    private final Map<String, ProjectionRegistration> projections = new LinkedHashMap<>();
    private final Map<Class<?>, Map<Integer, UnaryOperator<JsonNode>>> upcasters = new HashMap<>();
    private final List<HandlerRegistration> handlers = new ArrayList<>();
    private final List<BiConsumer<String, Register>> beforeHooks = new ArrayList<>();
    private final List<BiConsumer<String, JsonNode>> afterHooks = new ArrayList<>();
    private final Map<FrameworkError.Stage,Function<FrameworkError,Throwable>> typedErrorHandlers = new EnumMap<>(FrameworkError.Stage.class);
    private Function<FrameworkError,Throwable> globalErrorHandler;
    private final Map<String,List<BeforeHook>> inputHooks = new HashMap<>();
    private final Map<String,AccessPolicy> historyPolicies = new HashMap<>();
    private final Map<String,AccessPolicy> administrationPolicies = new HashMap<>();
    private Consumer<Throwable> errorHandler = error -> System.getLogger(Booster.class.getName()).log(System.Logger.Level.WARNING, error.getMessage());
    private Builder(EventStore store) { this.store = Objects.requireNonNull(store); }
    public Builder stateStore(StateStore stateStore) { this.stateStore = Objects.requireNonNull(stateStore); return this; }
    public Builder schemaVersion(Class<?> type, int version) {
      if (version < 1) throw new IllegalArgumentException("Schema version must be positive");
      schemaVersions.put(Objects.requireNonNull(type), version); return this;
    }
    public <S> Builder entity(Class<S> type) {
      if (entities.containsValue(type.getSimpleName()) && !entities.containsKey(type)) throw new IllegalArgumentException("Duplicate entity name");
      entities.put(type, type.getSimpleName()); return this;
    }
    public <E,S> Builder event(Class<E> type, Function<E,String> entityId, Class<S> entityType, BiFunction<E,S,S> reducer) {
      return event(type, entityId, entityType, reducer, 1);
    }
    @SuppressWarnings("unchecked")
    public <E,S> Builder event(Class<E> type, Function<E,String> entityId, Class<S> entityType, BiFunction<E,S,S> reducer, int version) {
      Objects.requireNonNull(entityId); Objects.requireNonNull(reducer);
      if (version < 1) throw new IllegalArgumentException("Schema version must be positive");
      if (type.getSimpleName().startsWith("__booster_")) throw new IllegalArgumentException("Reserved event name");
      if (events.containsKey(type) || events.values().stream().anyMatch(e -> e.name().equals(type.getSimpleName()))) throw new IllegalArgumentException("Duplicate event type");
      entity(entityType);
      events.put(type, new EventDefinition(type.getSimpleName(), type, entityType.getSimpleName(), entityType,
          event -> entityId.apply((E) event), (event, state) -> reducer.apply((E) event, (S) state), version, false));
      return this;
    }
    public <N> Builder notification(Class<N> type) {
      return notification(type, "defaultTopic", value -> {
        JsonNode partition = Json.MAPPER.valueToTree(value).path("partitionId");
        return partition.isTextual() && !partition.asText().isBlank() ? partition.asText() : "default";
      }, 1);
    }
    @SuppressWarnings("unchecked")
    public <N> Builder notification(Class<N> type, String topic, Function<N,String> partition, int version) {
      if (topic == null || topic.isBlank() || version < 1 || type.getSimpleName().startsWith("__booster_")) throw new IllegalArgumentException("Invalid notification registration");
      Objects.requireNonNull(partition);
      if (events.containsKey(type) || events.values().stream().anyMatch(event -> event.name().equals(type.getSimpleName()))) throw new IllegalArgumentException("Duplicate event type");
      events.put(type, new EventDefinition(type.getSimpleName(), type, topic, null, value -> partition.apply((N)value), null, version, true));
      return this;
    }
    public Builder function(String name, RocketFunction handler) { return function(name, AccessPolicy.deny(), handler); }
    public Builder function(String name, AccessPolicy policy, RocketFunction handler) {
      if (name == null || name.isBlank()) throw new IllegalArgumentException("Rocket function name is required");
      if (functions.putIfAbsent(name, new FunctionRegistration(Objects.requireNonNull(policy), Objects.requireNonNull(handler))) != null)
        throw new IllegalArgumentException("Duplicate rocket function name");
      return this;
    }
    public Builder eventHistoryAccess(Class<?> entity, AccessPolicy policy) {
      entity(entity); historyPolicies.put(entity.getSimpleName(), Objects.requireNonNull(policy)); return this;
    }
    public Builder eventAdministrationAccess(Class<?> entity, AccessPolicy policy) {
      entity(entity); administrationPolicies.put(entity.getSimpleName(), Objects.requireNonNull(policy)); return this;
    }
    public Builder beforeCommand(String name, BeforeHook hook) { return before("command", name, hook); }
    public Builder beforeQuery(String name, BeforeHook hook) { return before("query", name, hook); }
    public Builder beforeReadModel(String name, BeforeHook hook) { return before("readModel", name, hook); }
    private Builder before(String kind, String name, BeforeHook hook) {
      if (name == null || name.isBlank()) throw new IllegalArgumentException("Operation name required");
      inputHooks.computeIfAbsent(kind + ":" + name, unused -> new ArrayList<>()).add(Objects.requireNonNull(hook)); return this;
    }
    public Builder onError(FrameworkError.Stage stage, Function<FrameworkError,Throwable> handler) {
      typedErrorHandlers.put(Objects.requireNonNull(stage), Objects.requireNonNull(handler)); return this;
    }
    public Builder globalErrorHandler(Function<FrameworkError,Throwable> handler) { globalErrorHandler = Objects.requireNonNull(handler); return this; }
    public Builder globalEventHandler(String name, BiConsumer<Object,Register> handler) { return eventHandler(name, Object.class, handler); }
    public <C> Builder command(Class<C> type, CommandHandler<C> handler) { return command(type, JsonNode.class, AccessPolicy.deny(), handler); }
    public <C> Builder command(Class<C> type, AccessPolicy policy, CommandHandler<C> handler) { return command(type, JsonNode.class, policy, handler); }
    @SuppressWarnings("unchecked")
    public <C,R> Builder command(Class<C> type, Class<R> output, AccessPolicy policy, CommandHandler<C> handler) {
      Objects.requireNonNull(policy); Objects.requireNonNull(handler);
      var registration = new CommandRegistration(new Operation(type.getSimpleName(), type, output), policy, (command, register) -> handler.handle((C) command, register));
      if (commands.putIfAbsent(type.getSimpleName(), registration) != null) throw new IllegalArgumentException("Duplicate command name");
      return this;
    }
    @SuppressWarnings("unchecked")
    public <C,R> Builder command(Class<C> type, com.fasterxml.jackson.core.type.TypeReference<R> output, AccessPolicy policy, CommandHandler<C> handler) {
      Objects.requireNonNull(output); Objects.requireNonNull(policy); Objects.requireNonNull(handler);
      var registration = new CommandRegistration(new Operation(type.getSimpleName(), type, Json.MAPPER.constructType(output.getType()).getRawClass(), output.getType()), policy,
          (command, register) -> handler.handle((C)command, register));
      if (commands.putIfAbsent(type.getSimpleName(), registration) != null) throw new IllegalArgumentException("Duplicate command name");
      return this;
    }
    @SuppressWarnings("unchecked")
    public <Q,R> Builder query(Class<Q> type, com.fasterxml.jackson.core.type.TypeReference<R> output, AccessPolicy policy, QueryHandler<Q> handler) {
      Objects.requireNonNull(output); Objects.requireNonNull(policy); Objects.requireNonNull(handler);
      var registration = new QueryRegistration(new Operation(type.getSimpleName(), type, Json.MAPPER.constructType(output.getType()).getRawClass(), output.getType()), policy,
          (query, context) -> handler.handle((Q)query, context));
      if (queries.putIfAbsent(type.getSimpleName(), registration) != null) throw new IllegalArgumentException("Duplicate query name");
      return this;
    }
    @SuppressWarnings("unchecked")
    public <Q,R> Builder query(Class<Q> type, Class<R> output, AccessPolicy policy, QueryHandler<Q> handler) {
      Objects.requireNonNull(policy); Objects.requireNonNull(handler);
      var registration = new QueryRegistration(new Operation(type.getSimpleName(), type, output), policy, (query, context) -> handler.handle((Q) query, context));
      if (queries.putIfAbsent(type.getSimpleName(), registration) != null) throw new IllegalArgumentException("Duplicate query name");
      return this;
    }
    @SuppressWarnings("unchecked")
    public <R,S> Builder readModel(Class<R> type, Class<S> entityType, AccessPolicy policy, BiFunction<S,R,R> projection) {
      readModel(type, policy); entity(entityType); Objects.requireNonNull(projection);
      projections.get(type.getSimpleName()).bindings().add(new ProjectionBinding(entityType, null, context -> {
        R result = projection.apply((S) context.entity(), (R) context.previous());
        return result == null ? ProjectionResult.delete() : ProjectionResult.store(result);
      }, context -> ProjectionResult.delete(), true));
      return this;
    }
    public <R> Builder readModel(Class<R> type, AccessPolicy policy) {
      Objects.requireNonNull(type); Objects.requireNonNull(policy);
      if (projections.putIfAbsent(type.getSimpleName(), new ProjectionRegistration(new ReadModel(type.getSimpleName(), type), policy, new ArrayList<>())) != null)
        throw new IllegalArgumentException("Duplicate read model name");
      return this;
    }
    @SuppressWarnings("unchecked")
    public <R,S> Builder projection(Class<R> model, Class<S> source, Join<S,R> join,
        Function<ProjectionContext<S,R>,ProjectionResult<R>> project,
        Function<ProjectionContext<S,R>,ProjectionResult<R>> unproject) {
      var registration = projections.get(model.getSimpleName());
      if (registration == null || !registration.model().type().equals(model)) throw new IllegalArgumentException("Declare the read model first");
      entity(source); Objects.requireNonNull(join); Objects.requireNonNull(project); Objects.requireNonNull(unproject);
      registration.bindings().add(new ProjectionBinding(source, (Join<Object,Object>)(Join<?,?>)join,
          (Function<ProjectionContext<Object,Object>,ProjectionResult<Object>>)(Function<?,?>)project,
          (Function<ProjectionContext<Object,Object>,ProjectionResult<Object>>)(Function<?,?>)unproject, false));
      return this;
    }
    public <R,S> Builder projection(Class<R> model, Class<S> source, Join<S,R> join,
        Function<ProjectionContext<S,R>,ProjectionResult<R>> project) {
      return projection(model, source, join, project, context -> ProjectionResult.nothing());
    }
    public Builder sequenceKey(Class<?> modelType, String field) {
      var registration = projections.get(modelType.getSimpleName());
      if (registration == null || !registration.model().type().equals(modelType)) throw new IllegalArgumentException("Declare the read model first");
      if (field == null || field.isBlank() || field.equals("id")) throw new IllegalArgumentException("A distinct sequence field is required");
      if (modelType.isRecord() && Arrays.stream(modelType.getRecordComponents()).noneMatch(component -> component.getName().equals(field)))
        throw new IllegalArgumentException("Sequence field is not present on the read model");
      projections.put(modelType.getSimpleName(), new ProjectionRegistration(new ReadModel(modelType.getSimpleName(), modelType, field), registration.policy(), registration.bindings()));
      return this;
    }
    public <E> Builder upcaster(Class<E> type, int fromVersion, UnaryOperator<JsonNode> migration) {
      if (fromVersion < 1) throw new IllegalArgumentException("Migration version must be positive");
      if (upcasters.computeIfAbsent(type, unused -> new HashMap<>()).putIfAbsent(fromVersion, Objects.requireNonNull(migration)) != null)
        throw new IllegalArgumentException("Duplicate migration version");
      return this;
    }
    public <E> Builder eventHandler(Class<E> type, BiConsumer<E,Register> handler) {
      return eventHandler(type.getName() + "#" + handlers.stream().filter(h -> h.type().equals(type)).count(), type, handler);
    }
    @SuppressWarnings("unchecked")
    public <E> Builder eventHandler(String name, Class<E> type, BiConsumer<E,Register> handler) {
      if (name == null || name.isBlank() || handlers.stream().anyMatch(h -> h.name().equals(name))) throw new IllegalArgumentException("Handler name must be unique and nonempty");
      Objects.requireNonNull(handler); handlers.add(new HandlerRegistration(name, type, (event, register) -> handler.accept((E) event, register))); return this;
    }
    public Builder beforeCommand(BiConsumer<String,Register> hook) { beforeHooks.add(Objects.requireNonNull(hook)); return this; }
    public Builder afterCommand(BiConsumer<String,JsonNode> hook) { afterHooks.add(Objects.requireNonNull(hook)); return this; }
    public Builder onError(Consumer<Throwable> handler) { errorHandler = Objects.requireNonNull(handler); return this; }
    public Builder extension(Consumer<Builder> extension) { extension.accept(this); return this; }
    public Booster build() {
      for (var handler : handlers) if (handler.type() != Object.class && !events.containsKey(handler.type())) throw new IllegalArgumentException("Handler event must be registered");
      for (var event : events.values()) if (event.notification() && entities.containsValue(event.entityName())) throw new IllegalArgumentException("Notification topic conflicts with an entity name");
      for (var event : events.values()) for (int version=1; version<event.version(); version++)
        if (!upcasters.getOrDefault(event.type(), Map.of()).containsKey(version)) throw new IllegalArgumentException("Missing event migration for " + event.name() + " version " + version);
      for (var schema : schemaVersions.entrySet()) for (int version=1; version<schema.getValue(); version++)
        if (!upcasters.getOrDefault(schema.getKey(), Map.of()).containsKey(version)) throw new IllegalArgumentException("Missing schema migration for " + schema.getKey().getSimpleName() + " version " + version);
      for (String name : projections.keySet()) if (queries.containsKey(name)) throw new IllegalArgumentException("Read model and query names conflict");
      return new Booster(this);
    }
  }
}
