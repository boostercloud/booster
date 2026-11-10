# booster-framework-provider-local (`com.boostercloud.framework.provider.local`)

Java port of `packages/framework-provider-local`: the runtime of the local provider. It stores events,
snapshots, read models, websocket connections and subscriptions in files under the project's `.booster`
folder, runs the websocket server, and turns the local HTTP/websocket traffic into the envelopes core works
with. The HTTP server, the scheduler and the rockets are in `framework-provider-local-infrastructure`.

Core loads it from `config.providerPackage = "com.boostercloud.framework.provider.local"`:
`Provider.provider(List<RocketDescriptor>)` returns the `ProviderLibrary`. The provider depends only on
`framework-types` and `framework-common-helpers`; it reaches the application through `UserAppRegistry`.

## Package map

| Java | TypeScript | Purpose |
| --- | --- | --- |
| `Provider` | `index.ts` (`Provider`, `loadInfrastructurePackage`) | entry point; `recoverUndispatchedEvents(config)` (Java-only) |
| `LocalProvider` | the object literal returned by `Provider(...)` | the `ProviderLibrary` wiring |
| `LocalProviderRuntime` | the module-level singletons of `index.ts` | registries, journal, services, websocket server; `shared()` / `create(Options)` |
| `Paths` | `paths.ts` | `.booster/*.json` file names |
| `services.EventRegistry` | `services/event-registry.ts` | events and snapshots |
| `services.ReadModelRegistry` | `services/read-model-registry.ts` | read models, optimistic concurrency, sort and `select` projection; `UNIQUE_VIOLATED_ERROR_TYPE` |
| `services.WebSocketRegistry`, `services.ConnectionData` | `services/web-socket-registry.ts` (`ConnectionData`, `SimpleRegistryTypes`) | connections and subscriptions |
| `services.GraphQLService`, `services.HealthService` | `services/graphql-service.ts`, `services/health-service.ts` | hand requests to the application |
| `services.DispatchJournal` | (Java-only) | crash recovery of undispatched events |
| `library.EventsAdapter` | `library/events-adapter.ts` | `rawEventsToEnvelopes`, `readEntityEventsSince`, `readEntityLatestSnapshot`, `storeEvents`, `storeSnapshot`, `storeDispatchedEvent`, `recoverUndispatchedEvents` |
| `library.EventDeleteAdapter` | `library/event-delete-adapter.ts` | `findDeletableEvent`, `findDeletableSnapshot`, `deleteEvent`, `deleteSnapshot` |
| `library.EventsSearchAdapter`, `library.EventsSearcherBuilder` | `library/events-search-adapter.ts`, `library/events-searcher-builder.ts` | event search, paginated entity ids |
| `library.ReadModelAdapter` | `library/read-model-adapter.ts` | fetch, store, search, delete |
| `library.SearcherAdapter` | `library/searcher-adapter.ts` | `queryRecordFor`: Booster filters to NeDB queries |
| `library.GraphQLAdapter` | `library/graphql-adapter.ts` | `rawGraphQLRequestToEnvelope` |
| `library.ApiAdapter` (`APIResult`, `Success`, `Failure`) | `library/api-adapter.ts` | `requestSucceeded`, `requestFailed`, `healthRequestResult` |
| `library.WebSocketServerAdapter`, `ConnectionContext`, `ExpressWebSocketMessage` | `library/web-socket-server-adapter.ts` | the Jetty websocket server |
| `library.ConnectionsAdapter`, `library.SubscriptionAdapter` | `library/connections-adapter.ts`, `library/subscription-adapter.ts` | connection data, messages, subscriptions |
| `library.ScheduledAdapter`, `library.RocketAdapter`, `library.HealthAdapter` | `library/scheduled-adapter.ts`, `library/rocket-adapter.ts`, `library/health-adapter.ts` | scheduled triggers, rockets, sensor |
| `library.LocalHttpRequest` | (Express `Request`) | the raw HTTP request the infrastructure hands to core |
| `store.*` | (`@seald-io/nedb` 4.0.2) | the embedded document store |

TypeScript's `QueryValue`/`QueryOperation` types have no Java counterpart: queries are raw maps
(`Map<String, Object>`) with `JsRegExp` values for regular expressions. `NedbError` is `store.NedbError`.

## Storage layout

Every file is relative to the project directory (the working directory of the process, as in TypeScript, or
`LocalProviderRuntime.Options.baseDirectory`):

| File | Content |
| --- | --- |
| `.booster/events.json` | events (`kind: "event"`) and entity snapshots (`kind: "snapshot"`) |
| `.booster/read_models.json` | every read model as `{ typeName, value, uniqueKey }`, plus the unique sparse index on `uniqueKey` |
| `.booster/connections.json` | websocket connections `{ expirationTime, user, connectionID }`, plus a TTL index on `expirationTime` |
| `.booster/subscriptions.json` | `SubscriptionEnvelope`s, plus a TTL index on `expirationTime` |
| `.booster/dispatch_journal.json` | Java-only: events whose dispatch has not finished (see below) |

The format is NeDB's, so files written by the TypeScript provider are read by the Java one and vice versa
(pinned by `NedbFixtureCompatibilityTest` with files in the TypeScript format):

* one JSON document per line, each with its `_id` (16 characters `[0-9A-Za-z]`);
* inserts and updates append the full new version of the document, removals append
  `{"$$deleted":true,"_id":"…"}`, index definitions are `{"$$indexCreated":{…}}` lines
  (`{"$$indexRemoved":"field"}` removes one); dates are `{"$$date": <epoch millis>}`;
* when a file is loaded it is replayed (the last line of each `_id` wins) and rewritten compacted:
  `<file>~` is written and flushed, then renamed over the file atomically, then the directory is flushed
  (NeDB's `crashSafeWriteFileLines`); a missing file is restored from `<file>~` if a crash interrupted that
  rename.

## Durability and recovery

* Every write (insert, update, remove, index change) returns only after its lines were written and flushed
  with `FileChannel.force`. A failed write is rolled back in memory and the file is truncated back to its
  previous length, so no acknowledged write is lost and no partial line is left for the next append.
* On load, an unparsable last line without a terminating newline is a write interrupted by a crash: it is
  discarded (with a warning) and does not count as corruption. Other unparsable lines are skipped; if they are
  more than 10 % of the non-empty lines (NeDB's `corruptAlertThreshold`), loading fails with
  `CorruptDataFileException` and NeDB's message
  (`40% of the data file is corrupt, more than given corruptAlertThreshold (10%). Cautiously refusing to start NeDB to prevent dataloss.`),
  leaving the file untouched.
* `LocalProviderRuntime.loadExistingDataFiles()` (Java-only, called by the local infrastructure at start-up) loads
  every data file that exists, so a damaged file is found when the application starts (and refuses to start when it
  is beyond the threshold) instead of by the first request that touches it; missing files are still created on first
  use (Q19).
* Compaction happens on every load (as in NeDB), on demand (`Datastore.compactDatafile()`), periodically
  (`setAutocompactionInterval`) and, for the dispatch journal, automatically once it holds 1000 obsolete lines.
* Each collection has one fair read/write lock: reads run concurrently, writes are serialised (single writer),
  so the read-model compare-and-swap and the unique-key insert are atomic. Everything works on virtual threads.
* **Recovery of undispatched events** (not in TypeScript). TypeScript dispatches events in-process right after
  storing them; a crash in between loses the dispatch and therefore the projection. The Java provider records
  each batch in `.booster/dispatch_journal.json` (flushed) *before* storing it, marks an event `dispatched`
  when core calls `events.storeDispatched` for it, and removes the entries when the dispatch returns.
  `Provider.recoverUndispatchedEvents(config)` (or `LocalProvider#recoverUndispatchedEvents`), called by the
  local infrastructure at start-up, re-dispatches through `UserAppRegistry` every stored event whose entry is
  still pending, oldest first. Entries of events that were never stored are dropped; entries whose processing
  had started are dropped too (core's at-most-once rule once `storeDispatched` returned `true`), so
  `storeDispatched` answers `false` for a re-dispatched event core already saw, which makes re-dispatching safe.
  A failed recovery keeps the pending entries for the next one. Dispatches running in the current process are
  never touched, so recovery can run while the application serves requests.
* **Atomic event batches** (not in TypeScript). `events.store` writes the events of one batch (what one command,
  event handler or scheduled command registered) with a single flushed append (`EventRegistry.storeAll`), so a
  crash or a failed write leaves all of them stored or none: a command is never half applied. TypeScript (and
  `EventsAdapter.storeEvents` without journal) inserts them one by one.

## Differences from TypeScript

Store (NeDB replacement):

* Writes are flushed (NeDB does not flush appends) and rolled back on failure; a torn last line is recovered
  instead of counting as corruption; a failed load can be retried (NeDB never retries); the datastore loads
  itself on first use.
* Generated `_id`s start with a clock-based monotonic counter, so unsorted results and sort ties (for example
  events created in the same millisecond, TypeScript quirk Q1) come in insertion order instead of random order.
  The order rule itself is NeDB's (ascending `_id` unless a persistent index supplies the candidates).
* Lines are split on `\n` only (NeDB also splits on U+0085, U+2028, U+2029, `\v`, `\f`, `\r`), and those three
  characters and UTF-16 surrogates are written as `\uXXXX` escapes: a document containing them is valid in both
  implementations instead of becoming a corrupt line for NeDB.
* Memory-only lookup indexes (on `entityID`, `entityTypeName`, `typeName`, `value.id`, `connectionID`,
  `className`) narrow equality queries; they are never persisted and never change results or their order.
* Regular expressions keep JavaScript's syntax: `JsRegExp` translates the source (Annex B grammar, flags `i`, `m`,
  `s`, `u`) into an equivalent Java pattern, so a lone `{`, `\s` with Unicode spaces, `$` at the very end only,
  identity and octal escapes, `[]`/`[^]` and the like behave as in JavaScript, and invalid sources throw
  `JsRegExp.SyntaxError` with V8's message (`Invalid regular expression: /^C++/: Nothing to repeat`). Not emulated:
  JavaScript's non-Unicode case folding for the few characters where it differs from Java's (`ſ`, the Kelvin sign),
  code-unit matching outside the BMP, and back references to groups that did not participate (empty in JavaScript,
  a failure in Java).
* Every acknowledged write is flushed (`FileChannel.force`, about 4 ms on an Apple-silicon laptop) while the
  collection's write lock is held, so one collection takes roughly 250 single-document writes per second (NeDB does
  not flush and is faster). Batch inserts share one flush.
* NeDB's modifiers run in sloppy-mode JavaScript; `Model.modify` reproduces that (setting a property of a primitive is
  ignored) except for non-index properties set on an array (`'arr.x'`), which JavaScript keeps on the array object
  until serialization and Java ignores.
* Errors are `NedbError` (unchecked) with NeDB's messages; the unique-violation message quotes the key with
  `JSON.stringify` (`Can't insert key "T_1_1", it violates the unique constraint`), as `@seald-io/binary-search-tree` does.
* An undefined element inside an array is stored as `null` (NeDB keeps `undefined` in memory until the next load).

Provider:

* Fixed TypeScript bugs: `connections.fetchData` queries `connectionID` (TypeScript queries `connectionId` and
  never finds anything, so websocket operations ran without the user, Q2); `readModels.deleteSubscription`
  matches `operation.id` (TypeScript filters on a `subscriptionID` field that does not exist, so `stop` never
  deleted anything, Q9).
* `events.store` returns (and dispatches) the events with their database `id`, records them in the dispatch
  journal, stores the batch atomically (one append; TypeScript stores the events one by one, so a failure or a
  crash can leave the first ones stored), and `events.storeDispatched` deduplicates through that journal
  (TypeScript always returns `true`). Without the journal (`EventsAdapter.storeEvents(userApp, registry, envelopes, config)`) the behaviour is
  exactly TypeScript's.
* A version-1 read model is not inserted when one of the same type and id is already stored with a version: the
  store fails with NeDB's unique violation, which core retries like any optimistic-concurrency failure. In
  TypeScript the unique key `<type>_<id>_<version>` of the stored document changes with every update, so a third
  concurrent projection of a new read model that had found nothing inserted a second document with the same id
  (both kept being projected, and versions were counted twice).
* Envelopes read from the store expose NeDB's `_id` as `id` (TypeScript returns the raw documents, with `_id`).
* `readModels.fetch` returns an empty list when the read model does not exist (TypeScript `[undefined]`), as
  the Java SPI specifies; `readModels.delete` with a `null` read model is a no-op (TypeScript throws a
  `TypeError`); `readModels.search` always returns a `ReadModelListResult` (only `items` unless paginated).
* `rawStreamToEnvelopes` and `dedupEventStream` return empty lists and `produce` does nothing (TypeScript's
  `notImplemented` returns `undefined`).
* The provider state is a process-wide `LocalProviderRuntime` created on first use; the websocket server starts
  with the first `Provider.provider(...)` call, once per process (TypeScript binds the port again on every call,
  Q20). If the port is taken, the error is logged instead of crashing the process.
* The websocket server (Jetty) processes the events of each connection in order, on a virtual thread, including
  frames received before the `CONNECT` dispatch finished (TypeScript loses them and processes messages
  concurrently, Q11); handler failures are logged (TypeScript: unhandled rejections); closed connections are
  removed from the connection table (Q10); stopping the server lets the open connections process their
  `DISCONNECT` (core deletes their connection data and subscriptions) before the data files close; sending to an unknown connection throws `IllegalStateException`
  (TypeScript: `TypeError`), sending to a closing one is dropped; there is no idle timeout and messages up to
  100 MiB are accepted (the `ws` defaults); its threads are daemon threads; an upgrade on another path gets 404
  (`ws`: 400).
* `GraphQLRequestEnvelope.value` is typed: the HTTP body becomes a `GraphQLOperation`, websocket messages a
  `GraphQLClientMessage`. A websocket message outside the `graphql-ws` protocol becomes an error envelope with
  the error TypeScript's core reports for it (`Unknown message type. Message=[object Object]`), which core
  answers with `connection_error` as in TypeScript. An HTTP body that is not an object reaches core as TypeScript
  hands it over (a JSON array as an operation without query, a falsy body as no value), so core reports the same
  `Received an empty GraphQL query` / `body` errors; an object that cannot be read as an operation gives an error
  envelope.
* The raw HTTP request is `LocalHttpRequest` (lower-cased header names, mount-relative `url`, parsed body, raw
  bytes) instead of Express's `Request`; raw maps with `headers`/`body`/`url` are accepted too.
* `HealthAdapter.countAll` counts the loaded documents (TypeScript reloads the file from disk on every health
  request); `isDatabaseEventUp`/`areDatabaseReadModelsUp` resolve the files against the runtime's base
  directory.
* `infrastructure()` loads `com.boostercloud.framework.provider.local.infrastructure.Infrastructure`; the
  error keeps TypeScript's first sentence and names the Maven dependency instead of the npm commands.
* `searchEntitiesIDs` receives `limit` as an `int` (the Java SPI); `0` behaves like TypeScript's falsy limit.
* TypeScript's `NonPersistedEventEnvelope`s handed to `rawEventsToEnvelopes` become `EventEnvelope`s without
  `createdAt`; raw documents are converted.

Kept on purpose (TypeScript behaviour, documented in the Javadoc): offset cursors that are always returned and
equal offset + requested limit (`parseInt` rules included, `NaN` cursors), only the first operator of an
operator object being translated and array operands using their first element (Q3, Q4), only `"ASC"` sorting
ascending (Q5), the projection quirks of `select` (Q6, Q7), the TTL index never expiring numeric
`expirationTime`s and losing its TTL after a reload (Q8), event search returning soft-deleted events (Q16),
soft-deleted events gaining an `id` field (Q17), the `.booster` files being created lazily so that the
database health checks report `DOWN` until first use (Q19), unescaped regular-expression sources for
`contains`/`beginsWith`/`includes`, and `deleteReadModel` swallowing registry errors.

## Not implemented

* NeDB features the provider does not use: compound indexes through the API (they are restored from a file but
  `ensureIndex` rejects comma-separated names, like NeDB rejects commas in a field name), `timestampData`,
  `afterSerialization`/`beforeDeserialization` hooks, `compareStrings`, the event emitter, and `$where`
  functions other than `java.util.function.Predicate`.

## Tests and parity ledger

`mvn -B -pl framework-provider-local -am verify` from `java/`. `PARITY.md` maps every item of the specification
(behaviour, data shapes, the TypeScript tests, quirks, documented features) to the Java test that pins it, the commit
that fixed it, or the reason for an intentional difference. Besides the ports of the TypeScript tests:

* `store.NedbDifferentialTest` replays queries, cursors, modifier updates and data files generated with the real
  `@seald-io/nedb` 4.0.2 (`src/test/resources/nedb/differential/generate.js`); `store.JsRegExpTest` and
  `store.JsRegExpDifferentialTest` compare regular expressions with V8 (`src/test/resources/regexp/generate.js`);
  `library.FilterOperatorSemanticsTest` runs every Booster filter operator end to end on a real store.
* `store.StoreStressTest`, `store.KillDuringWriteStressTest` (a writer JVM killed with `SIGKILL`) and
  `ProviderStressTest` cover concurrency on virtual threads, compaction under load, truncated and zero-filled data
  files and 100k-document collections.
