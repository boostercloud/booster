# booster-framework-core (`com.boostercloud.framework.core`)

Java port of `packages/framework-core`: the annotations a Booster application is written with, the `Booster`
facade that boots it, the dispatchers of commands, queries, scheduled commands and rockets, authorization and
token verification, the global error handler, tracing and schema migrations, the read models (projections, reader,
subscriptions and their notification), data migrations and the health sensor — plus the entry points the providers
call (`UserApp`). The event pipeline and the GraphQL layer are ported by other stages; their public signatures exist
already as **skeletons** (see [Module layout](#module-layout)).

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
way against this module, boots it and dispatches a command and a query.

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

| `UserApp` method | Delegate | Stage |
| --- | --- | --- |
| `boosterServeGraphQL` | `new BoosterGraphQLDispatcher(config).dispatch(raw)` | core-3 |
| `boosterEventDispatcher` | `BoosterEventDispatcher.dispatch(raw, config)` | core-2a |
| `boosterConsumeEventStream` / `boosterProduceEventStream` | `BoosterEventStreamConsumer.consume` / `BoosterEventStreamProducer.produce` | core-2a |
| `boosterNotifySubscribers` | `new BoosterSubscribersNotifier(config).dispatch(raw)` | done (needs the core-3 schema) |
| `boosterHealth` | `new BoosterHealthService(config).boosterHealth(raw)` | done |
| `boosterTriggerScheduledCommands` (+ deprecated alias) | `new BoosterScheduledCommandDispatcher(config).dispatch(raw)` | done |
| `boosterRocketDispatcher` | `new BoosterRocketDispatcher(config).dispatch(raw)` | done |

**Commands** (GraphQL mutation → `BoosterCommandDispatcher`): version check, handler lookup, authorizer, schema
migration (`SchemaMigrator`), `before` hooks (`FilterHelpers`), instance creation (`Instances.createInstance`),
static `handle(command, register)`, global error handler, `RegisterHandler.handle` (wraps the registered events and
stores them with retry/backoff). Queries follow the same path without migration and register.

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
graphql-java against a `FilteredReadModelPubSub` of the changed read models; each event is sent as a `GraphQLData`
message to the subscription's connection. Failures are logged, never thrown.

**Data migrations** (`BoosterDataMigrations.run()`, called by user code): the `@DataMigration` classes by `order`; a
migration without `BoosterDataMigrationEntity` gets a `BoosterDataMigrationStarted` event and its static
`start(register)` runs; a `RUNNING` one is reported but not started again. `migrateEntity` emits
`BoosterEntityMigrated`.

**Health** (`sensor.health.BoosterHealthService`, the provider's `boosterHealth`): token verification and the global
health authorizer, then the indicator tree derived from the `/`-separated ids (`HealthUtils`), resolved depth-first
with the `enabled`/`details`/`showChildren` flags, and `healthRequestResult(nodes, isHealthy)`.

**Running** (`Booster.run(main, args)`, the contract of `java/cli/README.md`): `start [--port N]` (blocks while the
local server runs), `synth`, `deploy`, `nuke` through `config.provider().infrastructure()`; with
`BOOSTER_CLI_HOOK=true`, the `config.injectable` commands. Exit codes: 0 success, 1 failure (also when the provider
does not support the operation), 2 usage error.

## Module layout

TypeScript file → Java type. "done" = implemented in this stage; the other rows are skeletons whose methods throw
`UnsupportedOperationException("not implemented yet: <Class#method>")`, owned by the named stage.

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
| `core-concepts/**` | `coreconcepts.datamigration.events.*`, `coreconcepts.touchentity.events.*` (records, done); `coreconcepts.datamigration.entities.BoosterDataMigrationEntity` (record done, reducers core-2a) | done / core-2a |
| `services/event-store.ts`, `services/raw-events-parser.ts`, `booster-event-dispatcher.ts`, `booster-event-processor.ts`, `booster-event-stream-consumer.ts`, `booster-event-stream-producer.ts`, `booster-delete-event-dispatcher.ts`, `booster-touch-entity-handler.ts`, `booster-events-reader.ts`, `booster-event-search.ts` | `services.EventStore`, `services.RawEventsParser`, `BoosterEventDispatcher`, `BoosterEventProcessor`, `BoosterEventStreamConsumer`, `BoosterEventStreamProducer`, `BoosterDeleteEventDispatcher`, `BoosterTouchEntityHandler`, `BoosterEventsReader`, `BoosterEventSearch` | core-2a |
| `services/read-model-store.ts`, `booster-read-models-reader.ts`, `booster-subscribers-notifier.ts`, `read-model-schema-migrator.ts`, `booster-data-migrations.ts` | `services.ReadModelStore` (+ `ProjectionFunction`), `BoosterReadModelsReader`, `BoosterSubscribersNotifier` (+ `GraphQLErrors`), `ReadModelSchemaMigrator`, `BoosterDataMigrations` | done (core-2b) |
| `services/pub-sub/read-model-pub-sub.ts`, `noop-read-model-pub-sub.ts` (`iterall.createAsyncIterator`) | `services.pubsub.ReadModelPubSub` (+ `toPublisher`), `FilteredReadModelPubSub`, `NoopReadModelPubSub`, package-private `IteratorPublisher` | done (core-2b) |
| `sensor/health/booster-health-service.ts`, `health-utils.ts`, the indicators, `booster-version.ts`, `os-info.ts` | `sensor.health.BoosterHealthService`, `sensor.health.HealthUtils`, `sensor.health.healthindicators.*` (+ `RawHealthIndicatorResult`; `OsInfo.OsInfoResult`/`OsInfoCpuResult`/`OsInfoMemoryResult` records) | done (core-2b) |
| `booster-graphql-dispatcher.ts`, `services/graphql/**` | `BoosterGraphQLDispatcher`, `services.graphql.*` (generators, type informer, `Common`, `TargetTypeMetadata`, `ResolverBuilder`), `services.graphql.querygenerators.*`, `services.graphql.queryhelpers.*`, `services.graphql.websocketprotocol.*` | core-3 |
| `injectable/index.ts` | `com.boostercloud.framework.types.injectable.Injectable` / `InjectableCommand` (foundation) | foundation |
| `decorators/metadata.ts` (`getClassMetadata`, `getFunctionArguments`) | `com.boostercloud.metadata.ClassMetadata.of` (foundation); argument names come from reflection | not needed |

### Facilities for the other areas (`internal`, `instrumentation`)

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
* Subscription resolvers: read the `GraphQLResolverContext` from `environment.getGraphQlContext().get(
  GraphQLResolverContext.class)` (the notifier also sets it as the legacy `environment.getContext()`) and return
  `ReadModelPubSub.toPublisher(context.pubSub().asyncIterator(envelope, config))`. `NoopReadModelPubSub` instances are
  equal to each other.

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
  failure); user code must not share unsynchronised mutable state (JavaScript interleaves on one thread).
* The pub-sub keeps the TypeScript in-memory filter (first top-level property only, `includes` and nested filters decide
  alone, `isDefined` ignored, combinators fail); values are compared in their JSON form and regular expressions use the
  Java dialect. Its iterator is a plain `Iterator`; `ReadModelPubSub.toPublisher` adapts it for graphql-java.
* `BoosterSubscribersNotifier` executes the stored document with graphql-java (TypeScript: `graphql.subscribe`), with
  `KEEP_SUBSCRIPTION_EVENTS_ORDERED`; a second constructor takes the schema (tests and embedders).

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

## Not implemented here

Everything in the core-2a and core-3 rows of the layout table (skeletons). The data-migration reducers
`BoosterDataMigrationEntity.started/finished` belong to core-2a. `BoosterSubscribersNotifier(config)` needs the schema
of `GraphQLGenerator.generateSchema` (core-3); until then only the constructor taking a schema works.

## Tests

`mvn -B -pl framework-core -am verify` from `java/`. Surefire runs with `booster.env=test` (TypeScript:
`BOOSTER_ENV=test`); tests that touch the global state extend `BoosterTestSupport`. Test classes are named after the
TypeScript test files (`booster-command-dispatcher.test.ts` → `BoosterCommandDispatcherTest`); `ImporterTest`
compiles and scans a jar, `BoosterTokenVerifierTest` uses an in-process JWKS server (`JwtTestKeys`, replacing
`mock-jwks`), and `UserFacingApiTest` compiles and boots an application written with the documented API.
`BoosterSubscribersNotifierTest` builds a small schema whose subscription resolvers follow the core-3 contract; the
read model tests mock the provider and use Mockito construction mocks for the collaborators of other stages
(`EventStore`).
