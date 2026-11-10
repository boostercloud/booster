# booster-framework-types (`com.boostercloud.framework.types`)

Java port of `packages/framework-types`: the concepts, the configuration and registry
(`BoosterConfig`), the envelopes exchanged between core and providers, the provider SPI, the searcher
and filter grammar, the GraphQL websocket messages, sensor and tracing types, the error hierarchy, and
the shared JSON mapper.

## Package map

| Java package | TypeScript files | Main types |
| --- | --- | --- |
| `…types` | `config.ts`, `logger.ts`, `typelevel.ts`, `user-app.ts`, `booster-app.ts`, `schedule.ts`, `data-migration-parameters.ts`, `super-kind.ts`, `internal-info.ts` | `BoosterConfig`, `RetryConfig`, `ResourceNames`, `SubscriptionsConfiguration`, `Level`, `Logger`, `ConsoleLogger`, `TypeLevel`, `UserApp`, `UserAppRegistry`, `BoosterApp`, `ScheduleInterface`, `DataMigrationParameters`, `SuperKind`, `InternalInfo`; Java-only: `BoosterJson`, `Dates`, `EnvironmentVariables` |
| `…types.concepts` | `concepts/*.ts` | `UUID`, `TimeKey`, `Register`, `FlusherFunction`, every `*Metadata`, `*Interface` markers, authorizers, `Authorize` + `*RoleAccess`, before hooks + `*FilterHooks`, `TokenVerifier`, `DecodedToken`, `ProjectionResult`, `ReadModelAction`, `JoinKey`, `ReadModelJoinKeyFunction`, `ProjectionInfo(Reason)`, `BoosterMetadata`, `SequenceKey`, `EventStreamConfiguration`, `DataMigrationStatus`, `SignUpMethod`, … |
| `…types.envelope` | `envelope.ts` | every envelope record, `SuperKindType`, `EventType`, `EventSearchParameters`, `ReadModelListResult`, `GraphQLOperation`, `GraphQLRequestEnvelopeOrError` |
| `…types.provider` | `provider.ts`, `provider/azure-configuration.ts` | `ProviderLibrary` and its sub-libraries, `ProviderInfrastructure`, `HasInfrastructure`, `AzureConfiguration`, `ProviderLoader` |
| `…types.searcher` | `searcher.ts` | `Searcher`, `SearcherFunction`, `FinderByKeyFunction`, `SequenceFinderByKeyFunction`, `FilterFor`, `Operation`, `SortFor`, `SortDirection`, `ProjectionFor` |
| `…types.graphql` | `graphql-websocket-messages.ts` | `MessageTypes`, client messages (`GraphQLInit`, `GraphQLStart`, `GraphQLStop`, `GraphQLTerminate`), server messages (`GraphQLInitError`, `GraphQLInitAck`, `GraphQLData`, `GraphQLErrorMessage`, `GraphQLComplete`), `GraphQLWebsocketMessages` |
| `…types.errors` | `errors.ts`, `errors/*.ts` | `BoosterError` and its 10 subclasses, `BoosterErrors` (`httpStatusCodeFor`), `GlobalErrorContainer` and its 8 subclasses |
| `…types.sensor` | `sensor/health-indicator-configuration.ts` | `HealthStatus`, `HealthIndicator(s)Result`, `BoosterHealthIndicatorsIds`, `HealthIndicatorConfiguration(Base)`, `SensorConfiguration`, `SensorBoosterHealthConfiguration`, `HealthIndicatorInterface`, `HealthIndicatorMetadata`, `SensorDefaults` |
| `…types.instrumentation` | `instrumentation/trace-types.ts` | `TraceTypes`, `TraceActionTypes`, `TraceInfo`, `TraceHandler`, `TraceConfiguration` |
| `…types.rockets` | `rockets.ts` | `RocketDescriptor`, `RocketEnvelope`, `RocketFunction`, `Rockets` |
| `…types.injectable` | (`config.injectable`, core `injectable/`) | `Injectable`, `InjectableCommand` |

TypeScript-only symbols without a Java type: `Class<T>`/`AnyClass`/`Instance` (→ `java.lang.Class`
and `Object`), `ReadOnlyNonEmptyArray<T>` (→ `List<T>`), `CommandInput`/`QueryInput` and
`ReadModelRequestProperties`/`ReadModelSortProperties` (→ `Map<String, Object>`), `EventStream`
(→ `List<Object>`), `EventEnvelopeFromDatabase`/`EntitySnapshotEnvelopeFromDatabase` (→ the
envelope with a non-null `id`), `SensorBoosterHealthConfigurationDetails` (→
`HealthIndicatorConfigurationBase`), the type-level helpers of `searcher.ts` (`Paths`,
`DataProperties`, …; filters are plain maps at run time), `BoosterConfigTag` and the Effect-TS module
`effect/` (see below).

## Conventions

* JSON: one mapper, `BoosterJson.mapper()`; see its Javadoc and ARCHITECTURE §4.
* Timestamps: `Dates.nowIso()` / `Dates.toIso(instant)` (`2024-01-31T10:20:30.123Z`); the clock is
  replaceable for tests.
* Ids: `UUID` is a string wrapper (any string is valid); `UUID.generate()` is a random v4.
* The TypeScript `class` field of metadata/envelopes is called `type` (Java keyword).
* Envelopes are records with `toBuilder()`/`builder()`; absent fields are `null` and omitted from
  JSON.
* Provider SPI methods are blocking and keep the TypeScript parameter order.

## Differences from TypeScript

* `BoosterConfig.provider()` loads `<providerPackage>.Provider.provider(rockets)` by reflection
  (`ProviderLoader`) instead of `require(providerPackage).Provider(rockets)`; infrastructure is
  `<providerPackage>.infrastructure.Infrastructure.infrastructure(rockets)`. `setProvider` keeps the
  deprecation warning (printed to stderr).
* `currentVersionFor` returns 1 for an existing but empty migration map (TypeScript: `-Infinity`).
* `readModelNameFromResourceName` strips the literal prefix (TypeScript builds an unescaped regex).
* Each `BoosterConfig` has its own copy of the health defaults (TypeScript shares the exported
  constant).
* `traceConfiguration.enableTraceNotification` keeps the `boolean | string[]` union as an `Object`
  field plus typed helpers (`enableAll`, `enableFor`, `isTraceEnabledFor`).
* `ProjectionResult<T>` is a sealed type (`Upsert` | `ReadModelAction`) instead of an untagged union;
  `ReadModelAction.DELETE` can still be returned directly (unchecked-conversion warning) or through
  `ProjectionResult.delete()`.
* `joinKey` is a sealed `JoinKey` (`EntityProperty` | `Query`); query join keys compare by function
  class (TypeScript compares function source text).
* `authorize: 'all' | Role[] | fn` is the sealed `Authorize<A>`; authorizers return normally to
  allow and throw to deny (TypeScript resolves/rejects a promise).
* `QueryEnvelope` is a record of its own (TypeScript: an alias of `CommandEnvelope`), with
  conversions both ways.
* `ProviderReadModelsLibrary.search` always returns `ReadModelListResult`; read model documents are
  `Map<String, Object>`; `latestEntitySnapshot` and `connections.fetchData` return `Optional`;
  `store`/`delete` return nothing (TypeScript `Promise<unknown>`, unused).
* `ProviderInfrastructure`'s optional members are default methods throwing
  `UnsupportedOperationException`, with `supports(Operation)` telling which ones an implementation
  overrides.
* The websocket `GraphQLError` message is called `GraphQLErrorMessage` (graphql-java owns the name)
  and carries `{ "message": … }` entries (TypeScript `Error` objects serialise as `{}`).
* `BoosterError.code`/`data` are methods; `httpStatusCodeFor` lives in `BoosterErrors`; error names
  for retry matching are simple class names (TypeScript `error.name`, always `"Error"` for Booster
  errors). Plain TypeScript `Error`s are `IllegalStateException`s with the same messages.
* `Searcher.search()` returns `List<T>`, `searchPaginated()` the page, `searchRaw()` exactly what
  the backend returned, `searchOne()` an `Optional`; the builder state is visible through `state()`.
* `UserApp` flattens TypeScript's nested `Booster` object (`config()`, `configureCurrentEnv`,
  `configuredEnvironments`); the running app is registered in `UserAppRegistry` (TypeScript
  `require(dist/index.js)`).
* `process.env` reads go through `EnvironmentVariables` (with in-process overrides, since a JVM
  cannot change its environment); `InternalInfo.setBoosterLocalPort` replaces the CLI's
  `process.env[BOOSTER_INTERNAL_LOCAL_PORT] = port`.
* The Effect-TS module (`src/effect`) is not ported: it is a re-export of `@effect-ts/core` used by the
  TypeScript CLI only. The `config.injectable` CLI extension point is typed as `Injectable` (a
  non-empty list of `InjectableCommand { name, description, run(config, args) }`), which core runs
  when `BOOSTER_CLI_HOOK=true`; Effect's `runMain`/`contextProvider` have no counterpart and the
  configuration is passed to the command directly instead of through `BoosterConfigTag`.
* The handler identifiers (`dist/index.boosterServeGraphQL`, `../dist/index.js`) are kept verbatim
  for parity; Java infrastructure modules use their own entry-point naming.
