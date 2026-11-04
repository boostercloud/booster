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
      Function<Object, String> entityId, BiFunction<Object, Object, Object> reducer, int version) {}
  private record CommandRegistration(Operation operation, AccessPolicy policy, CommandHandler<Object> handler) {}
  private record QueryRegistration(Operation operation, AccessPolicy policy, QueryHandler<Object> handler) {}
  private record ProjectionRegistration(ReadModel model, Class<?> source, AccessPolicy policy,
      BiFunction<Object, Object, Object> projection) {}
  private record HandlerRegistration(String name, Class<?> type, BiConsumer<Object, Register> handler) {}
  private final EventStore store;
  private final Map<Class<?>, String> entities;
  private final Map<Class<?>, EventDefinition> events;
  private final Map<String, EventDefinition> eventsByName;
  private final Map<String, CommandRegistration> commands;
  private final Map<String, QueryRegistration> queries;
  private final Map<String, ProjectionRegistration> projections;
  private final Map<Class<?>, Map<Integer, UnaryOperator<JsonNode>>> upcasters;
  private final List<HandlerRegistration> handlers;
  private final List<BiConsumer<String, Register>> beforeHooks;
  private final List<BiConsumer<String, JsonNode>> afterHooks;
  private final Consumer<Throwable> errorHandler;
  private final Map<StreamId, Snapshot> snapshots = new HashMap<>();
  private final Map<String, Map<String, JsonNode>> models = new HashMap<>();
  private final Map<String, SubmissionPublisher<ReadModelChange>> publishers = new HashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
      Thread.ofPlatform().daemon().name("booster-scheduler").factory());
  private long checkpoint;
  private boolean closed;

  private Booster(Builder builder) {
    store = builder.store;
    entities = Map.copyOf(builder.entities); events = Map.copyOf(builder.events);
    eventsByName = new HashMap<>(); events.values().forEach(e -> eventsByName.put(e.name(), e));
    commands = Map.copyOf(builder.commands); queries = Map.copyOf(builder.queries);
    projections = Map.copyOf(builder.projections); handlers = List.copyOf(builder.handlers);
    beforeHooks = List.copyOf(builder.beforeHooks); afterHooks = List.copyOf(builder.afterHooks);
    errorHandler = builder.errorHandler;
    upcasters = new HashMap<>(); builder.upcasters.forEach((type, chain) -> upcasters.put(type, Map.copyOf(chain)));
    projections.keySet().forEach(name -> {
      models.put(name, new TreeMap<>());
      publishers.put(name, new SubmissionPublisher<>(ForkJoinPool.commonPool(), 256));
    });
    try { catchUp(false); } catch (RuntimeException error) { scheduler.shutdownNow(); publishers.values().forEach(SubmissionPublisher::close); throw error; }
  }
  public static Builder builder(EventStore store) { return new Builder(store); }
  public List<Operation> commands() { return commands.values().stream().map(CommandRegistration::operation).sorted(Comparator.comparing(Operation::name)).toList(); }
  public List<Operation> queries() { return queries.values().stream().map(QueryRegistration::operation).sorted(Comparator.comparing(Operation::name)).toList(); }
  public List<ReadModel> readModels() { return projections.values().stream().map(ProjectionRegistration::model).sorted(Comparator.comparing(ReadModel::name)).toList(); }
  public EventStore eventStore() { return store; }
  public synchronized long position() { checkOpen(); catchUp(true); return checkpoint; }
  private void checkOpen() { if (closed) throw new IllegalStateException("Booster runtime is closed"); }

  public JsonNode executeCommand(String name, JsonNode input, RequestContext context) {
    Objects.requireNonNull(context, "Request context is required");
    if (context.requestId().startsWith("__booster_")) throw new BoosterException(BoosterException.Code.VALIDATION, "Request ID uses a reserved namespace");
    var registration = commands.get(name);
    if (registration == null) throw new BoosterException(BoosterException.Code.NOT_FOUND, "Unknown command: " + name);
    authorize(registration.policy(), context);
    String fingerprint = fingerprint(name, input, context);
    try {
      synchronized (this) { checkOpen(); catchUp(true); }
      var cached = store.findCommand(context.requestId());
      if (cached.isPresent()) {
        verifyFingerprint(cached.get(), fingerprint);
        completeCommittedWork();
        return cached.get().result().deepCopy();
      }
      Object command = convert(input, registration.operation().inputType());
      if (command == null) throw new BoosterException(BoosterException.Code.VALIDATION, "Command input is required");
      var register = new Register(this, context);
      beforeHooks.forEach(hook -> hook.accept(name, register));
      Object result = registration.handler().handle(command, register);
      JsonNode jsonResult = Json.MAPPER.valueToTree(result);
      validatePending(register);
      var committed = store.commit(context.requestId(), fingerprint, register.expectedVersions, register.pending, jsonResult);
      completeCommittedWork();
      if (!committed.duplicate()) for (var hook : afterHooks) {
        try { hook.accept(name, committed.receipt().result().deepCopy()); } catch (Exception error) { report(error); }
      }
      return committed.receipt().result().deepCopy();
    } catch (BoosterException error) { report(error); throw error; }
    catch (Exception error) { report(error); throw new BoosterException(BoosterException.Code.INTERNAL, "Command failed: " + name, error); }
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
    authorize(registration.policy(), context);
    synchronized (this) { checkOpen(); catchUp(true); }
    try {
      Object query = convert(input, registration.operation().inputType());
      if (query == null) throw new BoosterException(BoosterException.Code.VALIDATION, "Query input is required");
      return Json.MAPPER.valueToTree(registration.handler().handle(query, new QueryContext(this, context)));
    } catch (BoosterException error) { report(error); throw error; }
    catch (Exception error) { report(error); throw new BoosterException(BoosterException.Code.INTERNAL, "Query failed: " + name, error); }
  }

  public synchronized JsonNode getReadModel(String name, String id, RequestContext context) {
    var registration = requireReadModel(name); authorize(registration.policy(), context);
    checkOpen(); catchUp(true);
    return models.get(name).getOrDefault(id, NullNode.instance).deepCopy();
  }
  public synchronized Page listReadModels(String name, QueryOptions options, RequestContext context) {
    var registration = requireReadModel(name); authorize(registration.policy(), context);
    checkOpen(); catchUp(true);
    return ReadModelSearch.search(models.get(name), options == null ? QueryOptions.defaults() : options);
  }
  private ProjectionRegistration requireReadModel(String name) {
    var registration = projections.get(name);
    if (registration == null) throw new BoosterException(BoosterException.Code.NOT_FOUND, "Unknown read model: " + name);
    return registration;
  }
  public synchronized Flow.Publisher<ReadModelChange> subscribe(String name, String id, RequestContext context) {
    var registration = requireReadModel(name); authorize(registration.policy(), context); checkOpen();
    return subscriber -> publishers.get(name).subscribe(new Flow.Subscriber<>() {
      private Flow.Subscription upstream;
      public void onSubscribe(Flow.Subscription subscription) { upstream = subscription; subscriber.onSubscribe(subscription); }
      public void onNext(ReadModelChange change) {
        if (id == null || id.equals(change.id())) subscriber.onNext(change);
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
    checkOpen();
    if (handlers.isEmpty()) return;
    long cursor = 0;
    int produced = 0;
    while (true) {
      var batch = store.readAll(cursor, 256);
      if (batch.isEmpty()) break;
      for (var stored : batch) {
        Object event = decode(stored.event());
        for (int index = 0; index < handlers.size(); index++) {
          var handler = handlers.get(index);
          if (!handler.type().isInstance(event)) continue;
          String requestId = "__booster_handler:" + stored.event().id() + ":" + handler.name();
          String fingerprint = "handler:" + handler.type().getName() + ":" + handler.name();
          var handled = store.findCommand(requestId);
          if (handled.isPresent()) { verifyFingerprint(handled.get(), fingerprint); continue; }
          if (++produced > 10000) throw new BoosterException(BoosterException.Code.INTERNAL, "Event handler cascade exceeded 10000 invocations");
          var register = new Register(this, new RequestContext(requestId, stored.event().user(), stored.event().metadata()));
          handler.handler().accept(event, register);
          validatePending(register);
          store.commit(requestId, fingerprint, register.expectedVersions, register.pending, NullNode.instance);
          catchUp(true);
        }
        cursor = stored.position();
      }
    }
  }

  private synchronized void validatePending(Register register) {
    checkOpen(); catchUp(true);
    register.expectedVersions.forEach((stream, expected) -> {
      long actual = snapshots.getOrDefault(stream, new Snapshot(0, NullNode.instance)).version();
      if (actual != expected) throw new OptimisticConcurrencyException(stream, expected, actual);
    });
    var stagedStates = new HashMap<StreamId, JsonNode>();
    var stagedModels = new HashMap<String, Map<String, JsonNode>>();
    for (var envelope : register.pending) {
      var definition = eventsByName.get(envelope.type());
      JsonNode previous = stagedStates.containsKey(envelope.stream()) ? stagedStates.get(envelope.stream())
          : snapshots.getOrDefault(envelope.stream(), new Snapshot(0, NullNode.instance)).state();
      JsonNode next = reduce(definition, decode(envelope), previous);
      for (var projection : projections.values()) {
        if (!projection.source().equals(definition.entityType())) continue;
        var staged = stagedModels.computeIfAbsent(projection.model().name(), unused -> new HashMap<>());
        JsonNode before = staged.containsKey(envelope.stream().entityId()) ? staged.get(envelope.stream().entityId())
            : models.get(projection.model().name()).get(envelope.stream().entityId());
        Object projected = projection.projection().apply(convert(next, definition.entityType()), convert(before, projection.model().type()));
        staged.put(envelope.stream().entityId(), Json.MAPPER.valueToTree(projected));
      }
      stagedStates.put(envelope.stream(), next);
    }
  }

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
  List<StoredEvent> history(Class<?> entity, String id) { return store.readStream(stream(entity, id), 0); }
  EventDefinition eventDefinition(Class<?> type) {
    var definition = events.get(type);
    if (definition == null) throw new BoosterException(BoosterException.Code.VALIDATION, "Unregistered event: " + type.getName());
    return definition;
  }
  JsonNode reduce(EventDefinition definition, Object event, JsonNode previous) {
    Object entity = convert(previous, definition.entityType());
    Object state = definition.reducer().apply(event, entity);
    return Json.MAPPER.valueToTree(state);
  }
  <T> T convert(JsonNode node, Class<T> type) {
    if (node == null || node.isNull()) return null;
    try { return Json.MAPPER.treeToValue(node, type); }
    catch (Exception error) { throw new BoosterException(BoosterException.Code.VALIDATION, "Invalid input for " + type.getSimpleName(), error); }
  }
  private Object decode(PendingEvent event) {
    var definition = eventsByName.get(event.type());
    if (definition == null) throw new BoosterException(BoosterException.Code.INTERNAL, "No registered type for stored event " + event.type());
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
    while (true) {
      var batch = store.readAll(checkpoint, 256);
      if (batch.isEmpty()) return;
      for (var stored : batch) {
        if (stored.position() != checkpoint + 1) throw new BoosterException(BoosterException.Code.INTERNAL, "Event journal contains a position gap");
        var envelope = stored.event(); var definition = eventsByName.get(envelope.type());
        Object event = decode(envelope);
        var previous = snapshots.getOrDefault(envelope.stream(), new Snapshot(0, NullNode.instance));
        if (stored.streamVersion() != previous.version() + 1) throw new BoosterException(BoosterException.Code.INTERNAL, "Event stream contains a version gap");
        JsonNode next = reduce(definition, event, previous.state());
        Map<String, JsonNode> updates = new HashMap<>();
        for (var projection : projections.values()) {
          if (!projection.source().equals(definition.entityType())) continue;
          String name = projection.model().name();
          JsonNode before = models.get(name).get(envelope.stream().entityId());
          Object projected = projection.projection().apply(convert(next, definition.entityType()), convert(before, projection.model().type()));
          JsonNode value = Json.MAPPER.valueToTree(projected);
          if (!Objects.equals(before == null ? NullNode.instance : before, value)) updates.put(name, value);
        }
        snapshots.put(envelope.stream(), new Snapshot(stored.streamVersion(), next));
        checkpoint = stored.position();
        updates.forEach((name, value) -> {
          String id = envelope.stream().entityId();
          if (value.isNull()) models.get(name).remove(id); else models.get(name).put(id, value);
          if (notify) publishers.get(name).offer(new ReadModelChange(name, id, value.deepCopy(), checkpoint),
              (subscriber, change) -> { report(new IllegalStateException("Slow subscription dropped update for " + name)); return false; });
        });
      }
    }
  }
  private static void authorize(AccessPolicy policy, RequestContext context) {
    Objects.requireNonNull(context, "Request context is required");
    if (!policy.permits(context.user())) throw new BoosterException(BoosterException.Code.FORBIDDEN, "Operation is not authorized");
  }
  private static void verifyFingerprint(CommandReceipt receipt, String fingerprint) {
    if (!receipt.fingerprint().equals(fingerprint)) throw new BoosterException(BoosterException.Code.CONFLICT, "Request ID was already used for another operation");
  }
  private static String fingerprint(String name, JsonNode input, RequestContext context) {
    var user = context.user();
    return hash(name + "\n" + canonical(input) + "\n" + Objects.toString(user.subject(), "") + "\n" + new TreeSet<>(user.roles())
        + "\n" + canonical(Json.MAPPER.valueToTree(user.claims())) + "\n" + canonical(Json.MAPPER.valueToTree(context.metadata())));
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
    closed = true; scheduler.shutdownNow(); publishers.values().forEach(SubmissionPublisher::close); store.close();
  }

  public static final class Builder {
    private final EventStore store;
    private final Map<Class<?>, String> entities = new LinkedHashMap<>();
    private final Map<Class<?>, EventDefinition> events = new LinkedHashMap<>();
    private final Map<String, CommandRegistration> commands = new LinkedHashMap<>();
    private final Map<String, QueryRegistration> queries = new LinkedHashMap<>();
    private final Map<String, ProjectionRegistration> projections = new LinkedHashMap<>();
    private final Map<Class<?>, Map<Integer, UnaryOperator<JsonNode>>> upcasters = new HashMap<>();
    private final List<HandlerRegistration> handlers = new ArrayList<>();
    private final List<BiConsumer<String, Register>> beforeHooks = new ArrayList<>();
    private final List<BiConsumer<String, JsonNode>> afterHooks = new ArrayList<>();
    private Consumer<Throwable> errorHandler = error -> System.getLogger(Booster.class.getName()).log(System.Logger.Level.WARNING, error.getMessage());
    private Builder(EventStore store) { this.store = Objects.requireNonNull(store); }
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
      if (events.containsKey(type) || events.values().stream().anyMatch(e -> e.name().equals(type.getSimpleName()))) throw new IllegalArgumentException("Duplicate event type");
      entity(entityType);
      events.put(type, new EventDefinition(type.getSimpleName(), type, entityType.getSimpleName(), entityType,
          event -> entityId.apply((E) event), (event, state) -> reducer.apply((E) event, (S) state), version));
      return this;
    }
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
    public <Q,R> Builder query(Class<Q> type, Class<R> output, AccessPolicy policy, QueryHandler<Q> handler) {
      Objects.requireNonNull(policy); Objects.requireNonNull(handler);
      var registration = new QueryRegistration(new Operation(type.getSimpleName(), type, output), policy, (query, context) -> handler.handle((Q) query, context));
      if (queries.putIfAbsent(type.getSimpleName(), registration) != null) throw new IllegalArgumentException("Duplicate query name");
      return this;
    }
    @SuppressWarnings("unchecked")
    public <R,S> Builder readModel(Class<R> type, Class<S> entityType, AccessPolicy policy, BiFunction<S,R,R> projection) {
      Objects.requireNonNull(policy); Objects.requireNonNull(projection); entity(entityType);
      var registration = new ProjectionRegistration(new ReadModel(type.getSimpleName(), type), entityType, policy,
          (entity, previous) -> projection.apply((S) entity, (R) previous));
      if (projections.putIfAbsent(type.getSimpleName(), registration) != null) throw new IllegalArgumentException("Duplicate read model name");
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
      for (var handler : handlers) if (!events.containsKey(handler.type())) throw new IllegalArgumentException("Handler event must be registered");
      for (var event : events.values()) for (int version=1; version<event.version(); version++)
        if (!upcasters.getOrDefault(event.type(), Map.of()).containsKey(version)) throw new IllegalArgumentException("Missing event migration for " + event.name() + " version " + version);
      for (String name : projections.keySet()) if (queries.containsKey(name)) throw new IllegalArgumentException("Read model and query names conflict");
      return new Booster(this);
    }
  }
}
