# booster-framework-core (`com.boostercloud.framework.core`)

Java port of `packages/framework-core`: the annotations a Booster application is written with, the `Booster`
facade that boots it, the dispatchers of commands, queries, scheduled commands and rockets, authorization and
token verification, the global error handler, tracing and schema migrations, the event pipeline (event store,
snapshots, event handlers, event streams, event search), the read models (projections, reader, subscriptions and their
notification), data migrations, the health sensor and the GraphQL API (schema generation, dispatcher, websocket
protocol) — plus the entry points the providers call (`UserApp`).

```java
@Command(authorize = All.class)
public record ChangeCartItem(UUID cartId, UUID productId, double quantity) {
  public static void handle(ChangeCartItem command, Register register) {
    register.events(new CartItemChanged(command.cartId(), command.productId(), command.quantity()));
  }
}

public class Main {
  public static void main(String[] args) { Booster.run(Main.class, args); }
}
```

The complete user-facing API is ARCHITECTURE.md §6b; `UserFacingApiTest` compiles an application written that
way against this module, boots it and dispatches a command and a query, and the integration tests
(`src/test/java/.../integration`, see [Tests](#tests)) run a whole store application end to end on an in-memory
provider.

## How it fits together

**Start-up** (`Booster.start(rootClass)`, `booster.ts` + `importer.ts`):

1. `Booster.config()` is created for the environment of `booster.env` / `BOOSTER_ENV`.
2. `config.userProjectRootPath` = the project directory (the root class's `target/classes` without that suffix).
3. `Importer` scans the root class's package tree (directories and jars, sorted by class-file path = TypeScript's
   directory walk), runs every `BoosterConfigurator` (the `src/config/config.ts` files) and then applies the
   annotations of every class with `Decorators.registerClass` (members first, then the class annotations, as the
   TypeScript decorator order). Each class is imported once per process.
4. The built-in concepts are registered (`BoosterEntityMigrated`, `BoosterDataMigrationStarted/Finished` and their
   reducers on `BoosterDataMigrationEntity`, `BoosterEntityTouched`).
5. The legacy JWKS verifier is added when `BOOSTER_JWT_ISSUER`, `BOOSTER_JWKS_URI` and `BOOSTER_ROLES_CLAIM` are
   set; `config.validate()` checks the schema migrations.
6. A `BoosterUserApp` is registered in `UserAppRegistry`; providers call its entry points.

**Entry points** (`index.ts` → `BoosterUserApp`, each a thin delegate on the global configuration):

| `UserApp` method | Delegate |
| --- | --- |
| `boosterServeGraphQL` | `new BoosterGraphQLDispatcher(config).dispatch(raw)` |
| `boosterEventDispatcher` | `BoosterEventDispatcher.dispatch(raw, config)` |
| `boosterConsumeEventStream` / `boosterProduceEventStream` | `BoosterEventStreamConsumer.consume` / `BoosterEventStreamProducer.produce` |
| `boosterNotifySubscribers` | `new BoosterSubscribersNotifier(config).dispatch(raw)` |
| `boosterHealth` | `new BoosterHealthService(config).boosterHealth(raw)` |
| `boosterTriggerScheduledCommands` (+ deprecated alias) | `new BoosterScheduledCommandDispatcher(config).dispatch(raw)` |
| `boosterRocketDispatcher` | `new BoosterRocketDispatcher(config).dispatch(raw)` |

**Commands** (GraphQL mutation → `BoosterCommandDispatcher`): version check, handler lookup, authorizer, schema
migration (`SchemaMigrator`), `before` hooks (`FilterHelpers`), instance creation (`Instances.createInstance`),
static `handle(command, register)`, global error handler, `RegisterHandler.handle` (wraps the registered events and
stores them with retry/backoff). Queries follow the same path without migration and register.

**Events** (spec 06; `booster-event-dispatcher.ts` and friends):

1. The provider calls `boosterEventDispatcher(raw)` with a page of its event store change feed
   (`provider.events().rawToEnvelopes(raw)`), or `boosterConsumeEventStream(raw)` with stream messages
   (`dedupEventStream(config, raw)`, then `rawStreamToEnvelopes(config, raw, dedup)`). Both entry points, and
   `boosterProduceEventStream` (which hands each entity's events to `provider.events().produce`), **never throw**: every
   failure is logged.
2. `RawEventsParser.streamPerEntityEvents` drops snapshots and soft-deleted events, groups the page by
   `(entityTypeName, entityID)` (page order kept inside a group) and calls the per-entity callback for every group.
3. `BoosterEventProcessor` (the callback): `EventStore.storeDispatchedEvent` marks every event as dispatched
   (`provider.events().storeDispatched`, retried; a mark that cannot be stored means "process it") and drops the ones
   already seen; then the **event handlers** of the remaining events run (handlers of the type, then the
   `@GlobalEventHandler`s; one event instance per event, bound from the stored, un-migrated value; a fresh `Register`
   per handler with the event's request id and user, stored with `RegisterHandler.handle` after the handler) together
   with — unless the entity name is a notification topic — **the snapshot and the projection**:
   `EventStore.fetchEntitySnapshot` then `ReadModelStore.project`.
4. `EventStore.fetchEntitySnapshot(entity, id)` loads the latest snapshot (schema-migrated), the events stored after
   its `snapshottedEventCreatedAt` (or `1970-01-01T00:00:00.000Z`), reduces them one by one in provider order
   (`BoosterEntityTouched`/`BoosterEntityMigrated` by the framework, every other event schema-migrated and passed with
   the current entity, `null` at first, to its static `@Reduces` method) and stores one snapshot for the final state
   with retry/backoff. Unregistered events and reducer failures go to the global error handler (the class Javadoc has
   the exact skip/abort rules). `Booster.entity` and the data migrations use the same method.

Other entry points: `Booster.events` → `BoosterEventSearch.eventSearch` (values bound to their event/notification
classes), `BoosterEventsReader` (GraphQL `eventsByEntity`/`eventsByType`: validation and per-entity authorization,
then `Booster.events`), `Booster.deleteEvent` → `BoosterDeleteEventDispatcher` (un-project and delete the snapshots
of each event, then the events), `BoosterTouchEntityHandler.touchEntity` (stores a `BoosterEntityTouched` event so the
snapshot is rebuilt and the read models re-projected).

**Execution model of the event pipeline.** TypeScript awaits the entity groups, the handlers-versus-projection pair,
the events of a group and the handlers of an event concurrently on its single thread, never failing fast. Java runs
each of those on its own **virtual thread** through the foundation's `Promises` helpers with `allSettled` semantics:
everything is joined before the call returns (serverless hosts rely on that), and a failure is collected (and logged
or rethrown as a `PromisesError` exactly where TypeScript does) only after every sibling finished. Guarantees kept:
the reduction of an entity is strictly sequential in provider order, with one `fetchEntitySnapshot` per entity per
page; one entity's failure never affects another; the events handed to the handlers keep the page order.
Non-guarantees, as in TypeScript: the order in which handlers (of one event, or of the events of a batch) complete,
and the order between handlers and projections. The difference is real parallelism: user handlers, reducers of
different entities and projections may run at the same time on several threads, so state they share must be
thread-safe, and handlers must not mutate the event instance they share with the other handlers of the event (records
are immutable).

**Read models, write side** (`services.ReadModelStore`, called by the event processor with every new snapshot and by
the delete-event dispatcher with `deleteEvent = true`): the projections (or un-projections) of the entity type run
concurrently; each finds the affected read models (`{ id: { in: ids } }` for an entity-property join key, the filter of
a join-key function otherwise) without authorization or hooks, schema-migrates them and runs one task per read model
with its own optimistic-concurrency retry loop (`Retrier.retryIfError`, 1000 attempts, re-fetch from the second). The
static projection method receives the entity instance, the current read model instance (or `null`) and a
`ProjectionInfo`; its result is stored with a fresh `boosterMetadata` (`version + 1`, expected version = previous),
deleted (`ReadModelAction.DELETE`) or ignored (`NOTHING`). Projection errors go to the global error handler.

**Read models, read side** (`BoosterReadModelsReader`): `findById`/`search`/`subscribe` (the GraphQL path) validate the
version, the read model, the authorizer and (by id) the sequence key, then apply the `before` hooks; `readModelSearch`/
`finderByIdFunction` (the `Booster.readModel(...)` searcher) go straight to the provider. Searches return migrated
instances, or plain objects of the selected paths (calculated fields included, their `dependsOn` paths fetched too).
`subscribe` stores `{ ...request, expirationTime, connectionID, operation }`. `ReadModelSchemaMigrator` upgrades raw
documents by `boosterMetadata.schemaVersion` and batch-migrates with `migrateAll`.

**Subscriptions** (`BoosterSubscribersNotifier`, the provider's `boosterNotifySubscribers`): the changed read models
from `rawToEnvelopes`, the stored subscriptions of their types, and every stored GraphQL document executed again with
`GraphQLExecution` (the dispatcher's execution, so resolver errors read the same) against a `FilteredReadModelPubSub` of
the changed read models; each event is sent as a `GraphQLData` message to the subscription's connection. Failures are
logged, never thrown.

**Data migrations** (`BoosterDataMigrations.run()`, called by user code): the `@DataMigration` classes by `order`; a
migration without `BoosterDataMigrationEntity` gets a `BoosterDataMigrationStarted` event and its static
`start(register)` runs; a `RUNNING` one is reported but not started again. `migrateEntity` emits
`BoosterEntityMigrated`.

**Health** (`sensor.health.BoosterHealthService`, the provider's `boosterHealth`): token verification and the global
health authorizer, then the indicator tree derived from the `/`-separated ids (`HealthUtils`), resolved depth-first
with the `enabled`/`details`/`showChildren` flags, and `healthRequestResult(nodes, isHealthy)`.

**GraphQL** (`booster-graphql-dispatcher.ts`, `services/graphql/**`): `boosterServeGraphQL` creates a
`BoosterGraphQLDispatcher` per request. It converts the request with `provider.graphQL().rawToEnvelope`, answers
`CONNECT` with the `graphql-ws` sub-protocol header, deletes the connection data and subscriptions on `DISCONNECT`, and
for `MESSAGE` verifies the token, hands websocket messages to `GraphQLWebsocketHandler` (the legacy
`subscriptions-transport-ws` protocol: `connection_init`/`start`/`stop`/`connection_terminate`) and runs HTTP
operations: parse, validate, introspection toggle, operation selection, then execution on graphql-java with a
`GraphQLResolverContext` (whose `responseHeaders` reach `handleResult`). The schema is built once per process by
`GraphQLGenerator.generateSchema(config)` from the registered metadata: `GraphQLQueryGenerator` (by id with sequence
keys, deprecated plural filters, paginated `List*` queries with `*Connection`, `eventsByEntity`/`eventsByType`, user
queries), `GraphQLMutationGenerator` (commands) and `GraphQLSubscriptionGenerator` (by id and filtered), sharing one
`GraphQLTypeInformer` and one cache of filter types. The resolvers build the envelopes of the other layers
(`BoosterReadModelsReader`, `BoosterCommandDispatcher`, `BoosterQueryDispatcher`, `BoosterEventsReader`). Results are
JSON maps with graphql-js shapes: `{errors?, data?}`, errors `{message, locations?, path?, extensions: {code?,
data?}}` (`GraphQLExecution`). `GraphQLSchemaSdlTest` pins the SDL of a representative application
(`src/test/resources/graphql/test-app.graphql`).

**Running** (`Booster.run(main, args)`, the contract of `java/cli/README.md`): `start [--port N]` (blocks while the
local server runs), `synth`, `deploy`, `nuke` through `config.provider().infrastructure()`; with
`BOOSTER_CLI_HOOK=true`, the `config.injectable` commands. Exit codes: 0 success, 1 failure (also when the provider
does not support the operation), 2 usage error.

## Module layout

TypeScript file → Java type. Every row is implemented; the area stages that ported them were merged into this
module (core-1 registration and dispatch, core-2a events, core-2b read models and health, core-3 GraphQL).

| TypeScript (`packages/framework-core/src`) | Java (`com.boostercloud.framework.core`) | Status |
| --- | --- | --- |
| `booster.ts` | `Booster` (+ `BoosterRunner` for `run`, Java addition `BoosterConfigurator`) | done |
| `importer.ts` | `Importer` | done |
| `index.ts` (entry functions) | `BoosterUserApp` | done |
| `decorators/*.ts`, `instrumentation/decorator/trace.ts` | `decorators.*` annotations, `decorators.All`, `decorators.Decorators` (registrars) | done |
| `instrumentation/trace-notifier.ts` | `instrumentation.TraceNotifier`, `instrumentation.Tracer` | done |
| `booster-authorizer.ts` | `BoosterAuthorizer` | done |
| `booster-command-dispatcher.ts`, `booster-query-dispatcher.ts` | `BoosterCommandDispatcher`, `BoosterQueryDispatcher` | done |
| `booster-register-handler.ts` | `RegisterHandler` | done |
| `booster-token-verifier.ts`, `services/token-verifiers/*` | `BoosterTokenVerifier`, `services.tokenverifiers.*` | done |
| `booster-global-error-dispatcher.ts` | `BoosterGlobalErrorDispatcher` | done |
| `booster-scheduled-command-dispatcher.ts`, `booster-rocket-dispatcher.ts` | same names | done |
| `schema-migrator.ts` | `SchemaMigrator` | done |
| `services/filter-helpers.ts`, `services/read-model-searcher.ts` | `services.FilterHelpers`, `services.ReadModelSearcher` | done |
| `services/graphql/common.ts` (`GraphQLResolverContext`) | `services.graphql.GraphQLResolverContext` | done |
| `sensor/health/health-indicators/default-booster-health-indicators.ts` | `sensor.health.healthindicators.DefaultBoosterHealthIndicators` | done |
| `core-concepts/**` | `coreconcepts.datamigration.events.*`, `coreconcepts.touchentity.events.*`, `coreconcepts.datamigration.entities.BoosterDataMigrationEntity` | done |
| `services/event-store.ts`, `services/raw-events-parser.ts`, `booster-event-dispatcher.ts`, `booster-event-processor.ts`, `booster-event-stream-consumer.ts`, `booster-event-stream-producer.ts`, `booster-delete-event-dispatcher.ts`, `booster-touch-entity-handler.ts`, `booster-events-reader.ts`, `booster-event-search.ts` | `services.EventStore`, `services.RawEventsParser`, `BoosterEventDispatcher`, `BoosterEventProcessor`, `BoosterEventStreamConsumer`, `BoosterEventStreamProducer`, `BoosterDeleteEventDispatcher`, `BoosterTouchEntityHandler`, `BoosterEventsReader`, `BoosterEventSearch` (+ Java addition `services.IgnoredReducerError`) | done |
| `services/read-model-store.ts`, `booster-read-models-reader.ts`, `booster-subscribers-notifier.ts`, `read-model-schema-migrator.ts`, `booster-data-migrations.ts` | `services.ReadModelStore` (+ `ProjectionFunction`), `BoosterReadModelsReader`, `BoosterSubscribersNotifier` (+ `GraphQLErrors`), `ReadModelSchemaMigrator`, `BoosterDataMigrations` | done |
| `services/pub-sub/read-model-pub-sub.ts`, `noop-read-model-pub-sub.ts` (`iterall.createAsyncIterator`) | `services.pubsub.ReadModelPubSub` (+ `toPublisher`, which returns the GraphQL `IteratorPublisher`), `FilteredReadModelPubSub`, `NoopReadModelPubSub` | done |
| `sensor/health/booster-health-service.ts`, `health-utils.ts`, the indicators, `booster-version.ts`, `os-info.ts` | `sensor.health.BoosterHealthService`, `sensor.health.HealthUtils`, `sensor.health.healthindicators.*` (+ `RawHealthIndicatorResult`; `OsInfo.OsInfoResult`/`OsInfoCpuResult`/`OsInfoMemoryResult` records) | done |
| `booster-graphql-dispatcher.ts`, `services/graphql/**` | `BoosterGraphQLDispatcher`, `services.graphql.*` (generators, type informer, `Common`, `TargetTypeMetadata`, `ResolverBuilder`; Java additions `GraphQLFieldResolver`, `GraphQLFieldConfig`, `GeneratedObjectType`, `GraphQLExecution`, `PropertyResolver`, `IteratorPublisher`, `Inflection`), `services.graphql.querygenerators.*`, `services.graphql.queryhelpers.*`, `services.graphql.websocketprotocol.*` | done |
| `injectable/index.ts` | `com.boostercloud.framework.types.injectable.Injectable` / `InjectableCommand` (foundation) | foundation |
| `decorators/metadata.ts` (`getClassMetadata`, `getFunctionArguments`) | `com.boostercloud.metadata.ClassMetadata.of` (foundation); argument names come from reflection | not needed |

### Internal contracts between the areas (`internal`, `instrumentation`)

* `internal.UserCode` — calls user code like TypeScript's `cls[methodName](...)`: `invokeStatic`, `findStatic`,
  `findInstance`, `invoke(method, target, args)` (prefix-arity matching, `@Trace` wrapping, `CompletionStage`
  awaiting, errors propagated unchanged), `newInstance`, `rethrow`, `await`. Use it for every user method (handlers,
  reducers, projections, health indicators, data migrations) so that `@Trace` works everywhere.
* `instrumentation.Tracer.trace(actionType, declaringClass, methodName, args, body)` — for the framework methods that
  TypeScript decorates with `@Trace` (`EventStore`, readers, dispatchers...).
* `internal.Js` — JavaScript truthiness and string conversion (`Error: message` for plain errors).
* `BoosterGlobalErrorDispatcher.dispatch(container)` returns the error to throw or `null`; rethrow with
  `UserCode.rethrow`.
* `services.ReadModelSearcher` is wired to `BoosterReadModelsReader::readModelSearch` and `::finderByIdFunction`;
  keep their signatures (they implement `SearcherFunction` / `FinderByKeyFunction`).
* `new ReadModelStore(config).project(snapshot)` / `.project(snapshot, true)` (delete) for the event pipeline; it
  throws a `PromisesError` (after every projection finished) when a projection failed, and `IllegalStateException`
  for missing un-projections.
* `BoosterReadModelsReader.findById`/`search`/`subscribe`/`unsubscribe`/`unsubscribeAll` for the GraphQL resolvers
  and websocket handler; `search` returns a `List` or, for `paginatedVersion`, a `ReadModelListResult`.
* Resolvers read the `GraphQLResolverContext` with `Common.resolverContext(environment)` (local context, or the
  `GraphQLContext` entry under `GraphQLResolverContext.class`); `GraphQLExecution.execute` sets both, and it is the only
  way the dispatcher and the notifier run documents (it also keeps subscription events ordered). Subscription resolvers
  return an `IteratorPublisher` over `context.pubSub().asyncIterator(envelope, config)` (`ReadModelPubSub.toPublisher`
  returns the same class); `NoopReadModelPubSub` yields an empty iterator, and its instances are equal to each other.
* The event pipeline calls `ReadModelStore.project` once per entity per page; GraphQL's event resolver calls the typed
  `BoosterEventsReader.fetch(EventSearchRequest)` (the schema already makes `entity`/`type` exclusive).

## Differences from TypeScript

**Registration**

* Decorators are annotations applied by a package scan at `Booster.start(Class)` (TypeScript: `Booster.start(__dirname)`
  `require`s every compiled file). Configurators run before any annotation is applied, so `@HealthSensor` always sees
  the final `sensorConfiguration` (TypeScript depends on the file order). Class order is the class-file path order
  (nested classes right after their enclosing class); inside a class, methods in declaration order, then the properties
  it declares in declaration order (TypeScript applies constructor-parameter decorators in reverse index order), then
  the class annotations in a fixed order (`@Role`, `@Event`, `@Notification`, `@Entity`, `@Command`, `@Query`,
  `@ReadModel`, `@ScheduledCommand`, `@EventHandler`, `@GlobalEventHandler`, `@GlobalErrorHandler`, `@DataMigration`,
  `@SchemaMigration`, `@HealthSensor`). Classes are loaded without running static initialisers; each is imported once
  per process (`Booster.reset()` forgets them).
* `authorize`/`authorizeReadEvents` are `Class<?>[]`: `{}` (default) denies, `{All.class}` allows everybody, a list of
  role classes checks roles, and one class implementing the authorizer interface is a custom authorizer (instantiated
  with its no-argument constructor). An explicit empty list cannot be told from the default (both deny, as in
  TypeScript). `All` or an authorizer mixed with other classes is rejected. The built-in authorizers are the shared
  objects `BoosterAuthorizer.ALLOW_ACCESS`/`DENY_ACCESS`; `authorizeRoles(roles)` is a `RolesAuthorizer` record.
* Function-valued attributes are classes with a no-argument constructor: `before` hooks, `@Projects(joinKeyFunction)`.
  `@Projects(unProject = "method")` names a static method of the same read model (validated); `@Projects`,
  `@Reduces` and `@EventHandler` are `@Repeatable`. `@Projects` must name exactly one of `joinKey`/`joinKeyFunction`.
* `@ToVersion(toVersion = n, fromSchema = A.class, toSchema = B.class)` must be on an instance method; the
  declaration order comes from the class file. `@Role(auth = @RoleAuth(...))`: an empty `signUpMethods` and
  `skipConfirmation = false` are stored as absent (`{ auth: {} }`). `@ScheduledCommand` fields default to `""` =
  absent. `@HealthSensor` requires a public `health` instance method.
* Java has no compile-time check of the decorator signatures, so registration validates what TypeScript's type
  checker enforces: commands, queries, scheduled commands and event handlers need a static `handle`, data migrations a
  static `start`, reducers and projections must be static.
* `@NonExposed` works on record components, fields, constructor parameters of properties and methods, registering the
  name under the **declaring** class; the TypeScript quirk that rejects the first constructor parameter is not ported.
  `@PartitionKey`/`@SequencedBy` take the property name from reflection (TypeScript parses the constructor source).
* `@CalculatedField(dependsOn)` is read by the metadata module; `ReadModelMetadata.properties` already carry the
  dependencies (empty for other properties).
* `@Returns` prints the deprecation notice when its class is registered (TypeScript: when the decorator is evaluated).

**Facade and start-up**

* `Booster.config()` is created lazily; without `booster.env`/`BOOSTER_ENV` every access throws the TypeScript message
  (TypeScript throws when the module is loaded); the foundation trims the environment name. `Booster.findConfig()` is
  the non-failing form (used by tracing). `Booster.reset()` is a test hook.
* `userProjectRootPath` is the project directory with a trailing separator, computed from the root class's code
  source (`target/classes` stripped) instead of stripping `dist`.
* `Booster.entity` returns an `Optional`. `Booster.app()` is the `BoosterApp` view of the static facade.
* `BOOSTER_CLI_HOOK` is handled by `Booster.run` (TypeScript: inside `start`, running `@effect/cli`). Injected commands
  are the foundation's `InjectableCommand`s; the help output is Booster's own, the version is the main class's
  `Implementation-Version` (TypeScript: `package.json`). `start`/`synth`/`deploy`/`nuke` are Java additions that replace
  the CLI loading the project in-process.
* The deprecation notices (`loadTokenVerifierFromEnv`, `@Returns`, `config.setProvider`) go to the standard error.

**Dispatch**

* User methods are found by name at call time with JavaScript-like arity: a method may declare fewer parameters than
  the framework passes (`handle(command)` works). Overloads resolve to the most specific match. Results that are
  `CompletionStage`/`Future` are awaited. Checked exceptions of user code propagate unchanged.
* Handler failures are caught as `Exception` for the global error handler; `java.lang.Error`s are not routed to it.
* `if (!envelope.version)` is `version == 0` (the envelope version is an `int`).
* Global error handler hooks are static methods found by name (fewer parameters allowed); they return the `Throwable`
  to propagate, `null` (or an empty `Optional`) to swallow it; any other return value is an error, and so is a hook
  whose parameters cannot take the arguments (both are logged and returned like an error thrown by the hook).
* `RegisterHandler`: the event's `entityID()` is looked up reflectively; when it is missing the friendly "has no
  specification for the entity ID" error is thrown (TypeScript fails with `entityID is not a function`). Entity ids are
  `UUID`s (other values are stringified); a partition key or `partitionId` must be a `String` or `UUID`.
* `SchemaMigrator` has one `migrate` per envelope type. The value is bound to `fromSchema` with `Instances`
  (recursive, unknown properties dropped — TypeScript's `Object.assign` keeps them); a migration method is called on a
  fresh instance (TypeScript calls it unbound); a command's migrated value is converted back to its raw input map.
* `FilterHelpers.applyBeforeFunctions` (commands) and `applyQueryBeforeFunctions` (queries) are two methods because
  the hook interfaces are distinct types. A hook whose `apply` carries `@Trace` is traced.
* `@Trace` takes effect on methods the framework calls (it cannot intercept arbitrary calls); framework trace points are
  explicit `Tracer.trace` calls. `TraceInfo.internal` is `(target, Method)` (TypeScript: target and property
  descriptor); a `null` `onStart`/`onEnd` does nothing; `elapsedInvocationMillis` uses `Dates` (replaceable clock).
* `BoosterRocketDispatcher` and other plain TypeScript `Error`s are `IllegalStateException`s with the same messages.

**Events**

* `EventStore.fetchEntitySnapshot` returns an `Optional` (empty when the entity has no state, or when the new snapshot
  could not be stored after every attempt — TypeScript's `undefined`). The loaded snapshot reaches the reducers as a
  `NonPersistedEntitySnapshotEnvelope` (its persistence fields dropped; TypeScript keeps them on the same object), and
  that is what is stored again in the TypeScript quirk where pending entries exist but none changed the state.
* `throw undefined` (a global error handler that returns nothing for a reducer error) is the Java exception
  `services.IgnoredReducerError`, with the reducer error as cause: as in TypeScript, the reduction of the entity stops
  and nothing is stored. The double dispatch of a missing reducer (first from the reduction, then from the loop) is
  kept.
* Reducers are resolved with `UserCode.findStatic(entityClass, methodName, event, entity)`: a reducer whose parameters
  cannot take the arguments is an `IllegalStateException` (TypeScript: a `TypeError` calling `undefined`) handled as a
  reducer error. A reducer returning a `CompletionStage` is awaited (TypeScript would store the `Promise`). A missing
  entity registration while reducing, and an event type registered as neither event nor notification when calling its
  handlers, are `IllegalStateException`s with explicit messages (TypeScript: `TypeError`s), on the same paths.
* The values of the built-in `BoosterEntityTouched`/`BoosterEntityMigrated` events are read from raw maps (from
  storage) or from the records themselves (in-process providers); `BoosterEntityMigrated.newEntity` is used as is.
* `loadEventStreamSince` returns `List<? extends EventStoreEntryEnvelope>` so that the "skip what is not an event"
  check stays meaningful; a `null` provider list counts as empty. `storeDispatchedEvent` returns `boolean`.
* Event handlers: `eventHandlerMetadata` of `onDispatchEventHandlerError` is always `null` (TypeScript reads
  `e.eventHandlerMetadata`, which nothing sets). Failures of user reducers and handlers are routed to the global error
  handler when they are `Exception`s (as in the other dispatchers); the entry points and the per-entity isolation
  catch every `Throwable`.
* `BoosterDeleteEventDispatcher` passes `SnapshotDeleteParameters(entityID, entityTypeName, createdAt)` built from
  each event (TypeScript passes the event envelope, from which providers read those three fields).
* `eventSearch` returns new `EventSearchResponse` records with the bound values (TypeScript mutates the provider's
  objects); results of unknown types are returned as received.
* `BoosterEventsReader`: the typed `EventSearchParameters` cannot hold both `entity` and `type`, and `null`
  parameters are the "none" case. `fetch(requestID, currentUser, rawParameters)` is a Java addition that classifies
  raw parameters (GraphQL arguments) by key presence, as TypeScript's `'entity' in parameters`, with the exact
  TypeScript errors. An entity registered with a `null` authorizer is denied (TypeScript would fail calling it).
* `RawEventsParser` groups by a composite `(entityTypeName, entityID)` key (TypeScript's `` `${name}-${id}` `` string
  can collide). The raw events are passed to the debug log as objects (TypeScript renders them with `util.inspect`
  eagerly). The snapshot-storage error message renders the date like JavaScript's `Date.prototype.toString()` in the
  system time zone.
* `BoosterDataMigrationEntity.finished`: an unparsable `duration.start` gives a `null` `elapsedMilliseconds`
  (JavaScript `NaN`, written as `null`); without a current state it throws a `NullPointerException` with a message
  (TypeScript: `TypeError`). The wall clock comes from `Dates` (replaceable in tests).
* Test seams (the TypeScript tests replace private and static members with sinon): `EventStore`'s steps are
  package-private, non-final methods; `BoosterEventProcessor`'s steps are package-private methods of an instance
  (`callback`, `filterDispatched`, `snapshotAndUpdateReadModels`, `dispatchEntityEventsToEventHandlers`,
  `getEventInstance`, `callEventHandler`, `handleRegister`), because Mockito static mocks only apply to the calling
  thread and the pipeline runs on virtual threads; `BoosterEventDispatcher.dispatch`,
  `BoosterEventStreamConsumer.consume` and `BoosterDeleteEventDispatcher.deleteEvent` have package-private overloads
  that take the stores.

**Read models**

* Read models are handled as raw documents (`Map<String, Object>` with `boosterMetadata`) between the provider, the
  schema migrator and the store, so the stored version and provider-owned metadata (`optimisticConcurrencyValue`)
  survive classes that do not declare them; projections and searches see instances of the read model class (a class
  may declare a `BoosterMetadata boosterMetadata` property to receive it). The projection's result is converted to a
  new raw document (TypeScript mutates the returned object to add `boosterMetadata`). `ReadModelSchemaMigrator.migrate`
  takes and returns raw documents; migrated values are bound to `fromSchema` (unknown properties dropped).
* Projections return `ProjectionResult` (`ProjectionResult.of(x)`, `ReadModelAction.DELETE`/`NOTHING`) or a plain
  object. `getProjectionFunction` returns a `ProjectionFunction` that finds the static method by name when called
  (TypeScript returns the function); a missing method fails inside the projection error handling, like calling
  `undefined`. Join keys: an entity-property join key whose value is a collection or array is the TypeScript array case.
  An `Optional` join key or sequence key (the Java form of `T | undefined`) is read as its value: an empty one skips the
  projection with the `Couldn't find the joinKey` warning, like an undefined TypeScript key, and an `Optional` list is
  an array join key.
* Decisions on the TypeScript latent defects (spec 07 §7): an array join key with several existing read models
  schedules every id once — with its read model, or without one when none exists — instead of `existing × ids` tasks
  (D3; the pinned single-read-model case and the one-task-per-row behaviour of sequenced read models are unchanged);
  a projection error is dispatched to the global error handler once, not twice for synchronous failures (D5); when the
  handler swallows it the read model is skipped (D4; TypeScript then fails with a `TypeError`); a projection returning
  `null` fails with an explanation (TypeScript fails storing `undefined`); the warning for a missing join key prints
  the key (D7). Kept as in TypeScript: no id validation of the result (D11), the retry of a join-key function without
  read model (D6), the stored operation name not passed to graphql-java (D9), no expiry/authorization check at
  notification time (D10), `select: []` selecting nothing (D13).
* `findById` with a list result (sequenced read models) returns every element migrated, in order — the documented
  behaviour (all the items of the id, or the exact match); TypeScript's `Object.assign` turns the array into one object
  and migrates it once, which its test pins (D1). Not found returns `null`.
* Strings TypeScript throws are `IllegalStateException`s with the same text (`Tried to run a findById operation without
  providing a key...`, `Got multiple objects for a request by Id...`); unregistered entities or read models fail with
  an explanation instead of a `TypeError`. GraphQL error arrays thrown by the notifier are
  `BoosterSubscribersNotifier.GraphQLErrors`.
* A search with `select` returns maps of the selected paths holding the instance values (`UUID`s, not strings); without
  it, instances. Non-paginated searches return the provider page's `items` (the provider always returns a
  `ReadModelListResult`); `finderByIdFunction` returns the raw documents. `readModelSearchDocuments` is a Java addition
  (the documents behind a search, for the store).
* Concurrency: projections, read model tasks, item migrations, selections, subscription fetches and notifications run on
  virtual threads with the TypeScript failure semantics (`allSettled` → `PromisesError`, `Promise.all` → first
  failure); user code must not share unsynchronised mutable state (JavaScript interleaves on one thread). The
  notifications of one subscription are sent one after the other, in the order of its results (TypeScript starts its
  `sendMessage` calls in that order).
* The pub-sub keeps the TypeScript in-memory filter (first top-level property only, `includes` and nested filters decide
  alone, `isDefined` ignored, combinators fail); values are compared in their JSON form and regular expressions use the
  Java dialect. Its iterator is a plain `Iterator`; `ReadModelPubSub.toPublisher` adapts it for graphql-java.
* `BoosterSubscribersNotifier` executes the stored document with `GraphQLExecution` on graphql-java (TypeScript:
  `graphql.subscribe`), with `KEEP_SUBSCRIPTION_EVENTS_ORDERED`; a second constructor takes the schema (tests and
  embedders). As in TypeScript, searches with a selection (every GraphQL list query) return the stored properties
  without schema migration: a property added by a migration is `null` there until the read model is stored again
  (`MigrationsIntegrationTest` pins it); by-id queries and `Booster.readModel(...).search()` are migrated.

**Data migrations and health**

* `BoosterDataMigrations.run()` reads the status of a snapshot value given as an instance or as a raw document; the
  migration's static `start(register)` is found by name (fewer parameters allowed). `order` is an `int`.
* The health response nodes are raw JSON objects (`Map<String, Object>`) built like the TypeScript object spread, so
  extra properties of an indicator's result are kept. Indicators are instantiated with their no-argument constructor
  and their `health(config, metadata)` is found by name (it may return a `HealthIndicatorResult`, a
  `HealthIndicatorsResult` or any serialisable object). `booster/database/events` and `booster/database/readmodels`
  return `RawHealthIndicatorResult` because provider details may be arrays (Azure); the other three built-ins
  implement `HealthIndicatorInterface`. An error caught by a built-in indicator becomes its JSON form as details
  (`{ message }`, or `{ code, data }` for a `BoosterError`; TypeScript serialises a plain `Error` as `{}`). The
  service catches `Exception`s, not `java.lang.Error`s. An indicator returning `null` is skipped (or fails, without
  details, as the TypeScript `TypeError`).
* `boosterVersion` is the version of the framework-core artifact (`Implementation-Version`, then the Maven
  `pom.properties`) instead of the `package.json` dependency; it is not cached, and `""` with a warning when unknown.
  `fromDependencySpecification` keeps the TypeScript parsing of npm specifications.
* `osInfo()` reads `/proc/stat` and `/proc/cpuinfo` on Linux (like Node); elsewhere it reports one entry per logical
  processor with model `os.arch`, speed `0` and times estimated from the system CPU load over the JVM uptime. Memory
  is the physical memory of the platform `OperatingSystemMXBean`.
* JSON key order of the `rockets` nodes follows the `HealthIndicatorsResult` record (`status, name, id, ...`;
  TypeScript writes `name, id, status`).

**Token verification**

* `jsonwebtoken` and `jwks-rsa` are replaced by JDK code (`Utilities`, `JwksClient`, `PemKeys`) that keeps their check
  order, error classes (`JsonWebTokenError`, `TokenExpiredError`, `NotBeforeError`, `JwksError`,
  `SigningKeyNotFoundError`) and messages. Only RS256 is accepted, no clock tolerance, no audience check. A payload that
  is not a JSON object is `invalid token` (jsonwebtoken may keep a string payload). PEM keys: SPKI, PKCS#1, X.509
  certificates and RSA private keys; any other text is a "secret", rejected for RS256.
* `getKeyWithClient(client, header)` returns the PEM key (TypeScript: a callback); `verifyJWT` accepts a PEM string, a
  `PublicKey` or a `KeyResolver`. `JwksUriTokenVerifier` keeps its JWKS client (and its 15-minute key cache) for its
  lifetime — TypeScript creates a client per verification; results are identical. `PublicKeyTokenVerifier` takes a
  `CompletionStage<String>` (TypeScript `Promise<string>`) or a PEM string.
* Reasons are joined with JavaScript error texts: `TokenExpiredError: jwt expired`, `Error: <message>` for
  `IllegalStateException`/`IllegalArgumentException` (TypeScript plain `Error`s), `<SimpleClassName>: <message>`
  otherwise. JavaScript can throw strings; Java verifiers throw exceptions.

**GraphQL**

* graphql-java replaces graphql-js. graphql-java keeps resolvers in the schema's code registry, so the generators
  return `GraphQLFieldConfig`s (graphql-js `GraphQLFieldConfig`) and the root generators a `GeneratedObjectType` (the
  type plus its field configurations); resolvers are `GraphQLFieldResolver`s, graphql-java `DataFetcher`s callable as
  `(parent, args, context, info)`. The `GraphQLResolverContext` travels in the `GraphQLContext` under the key
  `GraphQLResolverContext.class` (and as local context). The self references of filter types (`and`/`or`/`not`, the
  graphql-js field thunks) are `GraphQLTypeReference`s. Field, argument and type names and orders are those of
  TypeScript, including the first-creation-wins type cache, `@NonExposed` honoured only for the class passed to
  `generateGraphQLTypeForClass` (and looked up by property name for nested filters/sorts), the plural query replacing
  the by-id query when the plural equals the name, and `inflected@2.1.0` pluralisation (`Inflection`). The type aliases
  of `common.ts` have no class of their own: `TargetTypesMap` is `Map<String, TargetTypeMetadata>` and
  `GraphQLNonInputType` is graphql-java's `GraphQLOutputType`; `DateScalar` is `Common.dateScalar()`.
* graphql-java validates the schema when it is built (graphql-js on the first request): `generateSchema` throws (and
  caches) graphql-java's `InvalidSchemaException` (empty enums, object or input types without fields...); the
  dispatcher still answers `CONNECT`/`DISCONNECT`/websocket messages and returns the failure as the error of every
  operation, like TypeScript. A union cached as an output type and reused in an input position fails generation
  with `The GraphQL type <U> cannot be used as an input type` (TypeScript builds an invalid schema).
* A class that refers to itself (directly or through other classes) is supported through type references in object,
  input, filter and sort types; TypeScript overflows the stack.
* Enum property filters type `eq`/`ne` with the nullable enum; TypeScript keeps the non-null wrapper of a non-nullable
  property, which makes both operators mandatory.
* A `void` `handle` maps to `JSON!` — what the current TypeScript metadata produces (`void` is not `never`), although
  the docs say `Boolean` — and the mutation resolver returns `true` for `null` results (`result ?? true`). Queries
  without `handle` fail with `Unexpected void return type for Query`.
* Type mapping comes from `ClassMetadata` (TypeScript-compatible names): every Java number is `Float`, `UUID` is `ID`,
  dates (`Instant`, `Date`, `OffsetDateTime`, `ZonedDateTime`) are `Date`, `Map`s and JDK/Booster classes (`TimeKey`,
  `Duration`...) are `JSON`, sealed types of records/classes are unions (resolved by the value's simple class name),
  generic records are `JSON` with `<Name>_<arg>_PropertyFilter` filters, `@CalculatedField` methods are fields (not
  filtered nor sorted).
* Results handed to `handleResult` are raw JSON maps (`errors` first when present, then `data`) instead of
  `ExecutionResult` objects; websocket `data` payloads are the same maps. The websocket `error` message of a `start`
  without query carries `{message}` per error (framework-types `GraphQLErrorMessage`); TypeScript serialises its
  `new Error(...)` with `JSON.stringify`, which sends `{}`. Unknown event types answer
  `{errors: [{message: "Unknown message type <type>"}]}` (TypeScript: an `Error` object); `handleResult(undefined)` is
  the one-argument `handleResult(null)`.
* Resolver errors keep the thrown error's message (graphql-java's default handler would write `Exception while
  fetching data ...`) with the field location and path. Syntax, validation, variable coercion and non-null errors keep
  graphql-java's wording, which differs from graphql-js. Every error returned by an operation carries
  `extensions` (`{code, data}` of a `BoosterError`, `{}` otherwise); the errors of a subscription that cannot be set
  up have no `extensions`, as in TypeScript. Documents are parsed with graphql-java's default parser limits.
* The validation errors graphql-js throws as an array travel in a private exception carrying the list.
* Documents are validated with the rules of the GraphQL specification only, like graphql-js `validate`
  (`GraphQLExecution.validate`, used by the dispatcher and the subscribers notifier): graphql-java's "good faith
  introspection" rule (which rejects a `__Type.fields`/`inputFields` selected inside another one, as the schema suites
  of the integration tests do) and its query complexity limits are not applied.
* `Date` scalar: values are written with milliseconds and `Z` from the Java date types and from ISO strings
  (TypeScript only accepts `Date` objects); inputs parse with `Dates.parse` (ISO instants, offsets, local date-times
  taken as UTC — JavaScript takes them as local time —, plain dates as UTC midnight; RFC 2822 is not accepted) and
  become the normalised ISO **string** (TypeScript: a `Date` object), so filters and inputs stay raw values.
  `JSON` is Booster's own scalar (graphql-scalars is not available): outputs go through `BoosterJson.toRaw`.
* Subscription resolvers return a `Flow.Publisher` (`IteratorPublisher`) over the pub-sub's `Iterator`; graphql-java
  uses each published item as the field value (TypeScript `resolve: (source) => source`). An exhausted iterator
  completes on subscription, without demand. The websocket handler takes
  a `Flow.Publisher`, a reactive-streams `Publisher` or an `Iterator` as the result of a subscription (TypeScript
  `'next' in result`). Over HTTP, `runGraphQLOperation` returns that publisher too.
* Fields without resolver are read by `PropertyResolver`: map entries for raw documents, otherwise the
  `ClassMetadata` property (record components, fields of any visibility, calculated-field methods), falling back to
  getters; `Optional`s are unwrapped (the `select` projection of list queries unwraps them too, at every level).
  `getFields` (the `select` projection) is ported over graphql-java's AST with the
  same rules (aliases ignored, unknown fields skipped, fragments inlined, `[]` for lists of objects).
* The event search arguments become `EventSearchParameters` (`fromMap`), by-id arguments a Booster `UUID` and a
  `TimeKey` sequence value (`null` when omitted); commands without input get a `null` value.
* `GraphQLGenerator.reset()` (also called by `Booster.reset()`) forgets the process-wide schema; the static
  collaborators are created by the first `generateSchema` and resolvers fail with an explicit error before it.

## Not implemented here

Nothing in the layout table is left as a skeleton. The documented differences above are the deliberate ones; the
graphql-js wording of syntax, validation and coercion errors is not reproduced (graphql-java's is used).

## Tests

`mvn -B -pl framework-core -am verify` from `java/`. Surefire runs with `booster.env=test` (TypeScript:
`BOOSTER_ENV=test`); tests that touch the global state extend `BoosterTestSupport`.

[PARITY.md](PARITY.md) is the parity ledger: every behaviour pinned by a TypeScript test of this package (specs
04–09) and every core-relevant item of the documentation checklist, with the Java test that pins it, or why it differs
or lives in another module. Keep it current when a behaviour or a test changes.

**Unit tests** are named after the TypeScript test files (`booster-command-dispatcher.test.ts` →
`BoosterCommandDispatcherTest`) and port them (Mockito replaces sinon). `ImporterTest` compiles and scans a jar,
`BoosterTokenVerifierTest` uses an in-process JWKS server (`JwtTestKeys`, replacing `mock-jwks`), and
`UserFacingApiTest` compiles and boots an application written with the documented API. Tests without a TypeScript
counterpart cover the stream consumer/producer (`BoosterEventStreamTest`), event deletion, touching, the data-migration
reducers and the notifier (`BoosterSubscribersNotifierTest`, with a small schema following the resolver contract).
`BoosterEventDispatcherTest` runs the event pipeline with a mocked provider; the GraphQL tests run real operations on
graphql-java against `GraphQLTestApp` with the dispatchers and readers mocked (`mockConstruction`, the counterpart of
the TypeScript prototype replacements).

**Integration tests** (`integration/*IntegrationTest`) drive the real core with nothing mocked:

* `integration.inmemory.InMemoryProvider` is a complete `ProviderLibrary` in test sources (loaded through
  `config.providerPackage`, `Provider.provider(rockets)`): events and snapshots stored as raw JSON with the local
  provider's query rules (soft deletes, latest snapshot, searches newest first, entity id pages), dispatched-event
  deduplication, read models with optimistic concurrency on `boosterMetadata.version` (and injectable conflicts),
  filters/sorting/projections/cursors evaluated in memory, subscriptions, websocket connections and their messages,
  scheduled, rocket, API and sensor adapters. Stored events are dispatched like a change feed: one page after another
  on the storing thread, so a request returns when its events, the events registered by their handlers, the snapshots,
  projections and notifications are all done.
* `integration.app` is the fixture store application, started with `Booster.start(App.class)` for every test: carts and
  products, roles, a notification, read models with a calculated field, a `@NonExposed` property, before hooks, a join
  key shared by many entities, a sequenced read model, schema migrations of an event and a read model, two data
  migrations, event handlers (one registering events, one global), a scheduled command, a global error handler, health
  sensors, a `@Trace`d handler, and queries returning lists and unions. Tokens are real RS256 JWTs (`TestTokens`)
  checked by a `PublicKeyTokenVerifier`.
* The tests talk only through the `UserApp` entry points with the request records of `InMemoryRequests`: GraphQL over
  HTTP and websockets, `boosterHealth`, scheduled triggers, event streams and rockets. They cover commands to read
  models, queries by id/filtered/paginated with sort and cursor, authorization and token rejection, subscriptions,
  migrations, the events API, touch and delete, health, error hooks, tracing, GraphQL type mapping and parallel
  requests. Where handlers and projections of one event interleave (they run concurrently), the assertions accept both
  orders.
