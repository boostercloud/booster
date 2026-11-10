# Parity ledger: local provider (`framework-provider-local` + `framework-provider-local-infrastructure`)

This ledger walks spec 10 (`10-provider-local.md`, written from `packages/framework-provider-local` and
`packages/framework-provider-local-infrastructure` 4.1.0) section by section, plus the local-provider items of the
documentation checklist (spec 18), and records for every item how the Java port stands:

* **test**: implemented as in TypeScript and pinned by the named Java test(s) (`Class#method`, or `Class.Nested#method`).
* **fixed `<commit>`**: a gap found by this audit and fixed in that commit (and pinned by a test).
* **diff**: an intentional difference, with the reason. Each one is also listed in the module README under
  "Differences from TypeScript".
* **n/a**: nothing to port (the item describes a JavaScript/Node mechanism that has no Java counterpart).

Test classes without a module prefix are in `framework-provider-local`; `infra:` marks
`framework-provider-local-infrastructure`.

## How the audit was done

1. Every spec section re-read against the TypeScript sources (the code wins where the spec and the code disagree) and
   the Java implementation.
2. **Oracles instead of hand-written expectations** where possible:
   * the real `@seald-io/nedb` **4.0.2** (the version the TypeScript provider pins, taken from the local npm cache) ran
     750 random queries with sorts/skips/limits/projections, 240 random modifier updates and 6 random data files;
     `store.NedbDifferentialTest` replays them all (`src/test/resources/nedb/differential/generate.js`);
   * the TypeScript `queryRecordFor`, transcribed and run on NeDB 4.0.2, produced the expectations of every row of
     `library.FilterOperatorSemanticsTest` (all rows agreed with the hand-derived ones);
   * V8 (Node.js 24) produced the expectations of `store.JsRegExpTest` (207 rows) and
     `store.JsRegExpDifferentialTest` (1500 random patterns; `src/test/resources/regexp/generate.js`);
   * a data file written by the Java store was loaded by NeDB 4.0.2: same 209 documents, and both compacted it to
     byte-identical files.
3. Stress: `store.StoreStressTest`, `store.KillDuringWriteStressTest`, `ProviderStressTest` (results in the last
   section).

### Gaps found and fixed by this audit

| Commit | Gap |
| --- | --- |
| `2fe6bceac` | Reads chose between the read and the write lock (TTL expiry) before locking: a TTL index created concurrently could make a reader remove documents under the shared lock. `indexNames()` did not load the data file. |
| `cccaacd26` | Regular expressions were compiled as Java patterns. Filters pass user text unescaped to `new RegExp(...)`, and Java reads part of that syntax differently: `contains: '{'` threw in Java, `beginsWith: 'C++'` matched in Java but is a `SyntaxError` in JavaScript, `\s` missed Unicode spaces, `\v`, `$` before a final newline, identity escapes (`\e`), octal escapes, `[]`, `[^]`, `[` and `&&` in classes all differed. `JsRegExp` now translates the JavaScript (Annex B) syntax into an equivalent Java pattern and throws `JsRegExp.SyntaxError` with V8's message. |
| `a3270a0b3` | A JSON-array GraphQL body became an error envelope with a Java-only message; TypeScript hands it to core, which answers `Received an empty GraphQL query`. |
| `cfde5462a` | NeDB's modifier code is sloppy-mode JavaScript: `$set`/`$inc` on `n.x` when `n` is a number or a string is silently ignored, and `$push` then fails with NeDB's own message. The Java `Model` threw `Cannot create property ...`. |
| `b31432b83` and this ledger's commit | Missing pins: provider-level concurrency, persisted document shapes, health counts, TypeScript constants. |
| `c1ad2886e` | Flaky test (found by the Azure contract stage): `ReadModelAdapterTest#shouldReturnAnArrayOfEnvelopes` compared Java numbers, and the mock's random `bar` is sometimes an integral double that the JSON conversion of a raw envelope turns into an integral number (JavaScript semantics, not a provider change). It compares the JSON forms; `#convertsTheNumbersOfRawEnvelopesLikeJavaScript` pins the conversion. |

No foundation module was changed.

---

## 1. Public API (spec §2)

| TypeScript | Java | Status |
| --- | --- | --- |
| `Provider(rocketDescriptors?)` | `Provider.provider(List<RocketDescriptor>)` → `LocalProvider` (`ProviderLibrary`) | test `ProviderTest#coreLoadsTheProviderFromItsPackage` |
| `loadInfrastructurePackage(name)` | `Provider.loadInfrastructurePackage(String)` | test `ProviderTest#loadsTheInfrastructurePackageByName` |
| `ProviderLibrary` wiring (§2.1 table, 40 members) | `LocalProvider.*` | test `LocalProviderTest` (every member is called at least once) |
| `events.rawStreamToEnvelopes` / `dedupEventStream` / `produce` = `notImplemented` | empty lists / no-op | diff: the Java SPI returns typed values; TypeScript returns `undefined` |
| `readModels.fetch(..., sequenceKey)` | `sequenceKey` ignored | test `ReadModelAdapterTest.FetchReadModel#*`; see A2 |
| `paths.ts` (6 constants) | `Paths` (+ `DISPATCH_JOURNAL_DATABASE`) | test `ProviderTest#pathsAreTheTypeScriptOnes` |
| `EventRegistry`, `ReadModelRegistry` (+ `NedbError`, `UNIQUE_VIOLATED_ERROR_TYPE`), `WebSocketRegistry` (+ `ConnectionData`, `SimpleRegistryTypes`), `GraphQLService`, `HealthService` | `services.*`; `NedbError` is `store.NedbError` | test `services.*Test`; `SimpleRegistryTypes` is a TypeScript union with no Java counterpart (the registry takes any envelope) |
| `library/*` functions (§2.4) | static methods of `library.*Adapter` with the same names and argument order (bound arguments first) | test `library.*Test` |
| `QueryValue`, `QueryOperation` types | none: queries are `Map<String, Object>`, regular expressions `JsRegExp` | n/a (TypeScript-only types) |
| `APIResult` | `ApiAdapter.APIResult` (`Success`, `Failure`) | test `SmallAdaptersTest.Api#*` |
| `ConnectionContext`, `ExpressWebSocketMessage`, `WebSocketServerAdapter` | same names in `library` | test `WebSocketServerAdapterTest`, infra: `WebSocketTest` |
| `Infrastructure(rocketDescriptors?)` → `{ start }` | `Infrastructure.infrastructure(...)` → `LocalInfrastructure` | test infra: `InfrastructureTest`; diff: also `deploy`/`nuke`/`synth` that throw the CLI's message, `supports()` |
| `HttpCodes`, `requestFailed(error, res)` | `HttpCodes`, `Http.requestFailed` | test infra: `InfrastructureTest#httpCodes`, `#requestFailedUsesTheErrorClassForStatusAndTitle` |
| `GraphQLController`, `HealthController` | `controllers.*` | test infra: `controllers.*Test` |
| `configureScheduler(config, userProject)` | `Scheduler` | test infra: `SchedulerTest` |
| `InfrastructureRocket`, `InfrastructureRocketMetadata` | `LocalInfrastructureRocket`, `InfrastructureRocketMetadata` | test infra: `RocketMountingTest` |
| `LocalTestHelper`, `LocalCounters`, `LocalQueries` | `testhelper.*` | diff (Q23): real counters/queries; test infra: `testhelper.*Test` |
| `declare module 'http' { IncomingMessage.rawBody }` | `HttpRequest.rawBody()` | test infra: `web.BodyParsersTest#theVerifierReceivesTheRawBytes` |
| Constants (§2.6): `LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT`/`65529`, `/websocket`, `uniqueViolated`, `-1` sort, `originOfTime`, TTL `172800`, `6mb` | same values | test `ProviderTest#constantsAreTheTypeScriptOnes`, infra: `LocalServerTest#jsonBodiesAreLimitedToSixMegabytes` |
| `BOOSTER_INTERNAL_LOCAL_PORT` default `3000` | `InternalInfo.boosterLocalPort()` (foundation) | test `LocalProviderTest#graphQLApiScheduledRocketsAndSensor` |

## 2. Behaviour (spec §3)

### 3.1 Bootstrap and singletons

| Item | Status |
| --- | --- |
| Process-wide registries, user app, GraphQL service | diff: explicit `LocalProviderRuntime` (`shared()` / `create(Options)`) instead of module-load side effects; the application comes from `UserAppRegistry` instead of `require(dist/index.js)` (ARCHITECTURE §3) |
| Websocket server starts when the provider is created, not at `start` | test `ProviderTest#coreLoadsTheProviderFromItsPackage` (same timing) |
| Each `Provider()` call binds the port again (Q20) | diff: once per process; a taken port is logged, not a crash. test `WebSocketServerAdapterTest#aPortInUseIsReportedWithoutCrashing` |
| `infrastructure()` loads the infrastructure package; error message | diff: names the Maven artifact instead of the npm commands, keeps the first sentence. test `LocalProviderTest#infrastructureNeedsTheInfrastructureModule` |

### 3.2 Paths

Relative `.booster/<file>` resolved against the working directory (or `Options.baseDirectory`). test
`ProviderTest#pathsAreTheTypeScriptOnes`, `LocalProviderTest#storesEventsAndDispatchesThemInProcess`.

### 3.3 NeDB semantics (the store)

The whole section is pinned by `store.NedbDifferentialTest` against the real NeDB 4.0.2, plus:

| Item | Status |
| --- | --- |
| Append-only JSON lines, `$$deleted`, `$$indexCreated`, `$$date`, replay on load, compaction on load | test `DatastoreTest.Persistence#*`, `NedbFixtureCompatibilityTest`, `NedbDifferentialTest#dataFilesWrittenByNedbLoadAndCompactTheSame` (byte-identical compaction) |
| Operations before load are buffered; a datastore never loaded buffers forever | diff: the Java datastore loads itself on first use and a failed load can be retried |
| One serial executor per datastore | diff: a fair read/write lock (reads in parallel, single writer); compare-and-swap and unique inserts stay atomic. test `DatastoreTest.Concurrency#*`, `StoreStressTest`, `ProviderStressTest` |
| 16-character random `_id`; caller's object not mutated | diff: monotonic ids (insertion order instead of random order, Q1). test `DatastoreTest.Crud#insertGeneratesNedbIdsWithoutTouchingTheCallerDocument`, `#generatedIdsGrowSoUnsortedResultsComeInInsertionOrder` |
| Deep copies on write and read; class instances flattened, functions dropped | test `ModelTest.DeepCopy#*`, `DatastoreTest.Crud#resultsAreDeepCopies` |
| Field names: no leading `$`, no `.` | test `ModelTest.CheckObject#*`, `DatastoreTest.Crud#invalidFieldNamesAreRejectedAndNothingIsStored` |
| Matching: implicit AND, dot paths, array any-element rule, deep equality, missing ≠ null, operators `$lt..$elemMatch`, `$and/$or/$not`, regex as value, error messages | test `ModelTest.Matching#*`, `NedbDifferentialTest#queriesAndCursorsMatchNedb` |
| Cursor: `_id` candidate order, multi-key sort with NeDB's cross-type order, skip/limit, projections | test `DatastoreTest.Cursors#*`, `NedbDifferentialTest#queriesAndCursorsMatchNedb` |
| `insert` unique violation (`errorType`, `key`, message) | test `DatastoreTest.Indexes#uniqueSparseIndexesRejectDuplicatesButNotMissingFields`; diff: the key in the message is quoted with `JSON.stringify` (as `binary-search-tree` does) |
| `update` replacement keeps `_id`, first match unless `multi`; `remove` one/many; `count` | test `DatastoreTest.Crud#*`, `NedbDifferentialTest` |
| Modifiers (`$set`, `$unset`, `$inc`, `$push`, ...) | fixed `cfde5462a` (sloppy-mode assignments); test `ModelTest.Modify#*`, `NedbDifferentialTest#modifiersMatchNedb` |
| Unique sparse index persisted and restored | test `DatastoreTest.Indexes#ensureIndexIsIdempotentAndPersisted` |
| TTL index only expires `Date` values (Q8) | test `DatastoreTest.Indexes#ttlIndexesExpireDatesButNotNumbers`, `WebSocketRegistryAndJournalTest#theTtlIndexOnlyExpiresDatesAndOnlyInTheProcessThatCreatedIt`; fixed `2fe6bceac` (race with readers), test `StoreStressTest#creatingATtlIndexWhileReadersRunIsSafe` |
| Line splitting on `\n` | diff: NeDB also splits on U+0085, U+2028, U+2029, `\v`, `\f`, `\r` (so NeDB corrupts its own documents containing them); Java splits on `\n` only and writes those characters escaped, so both read Java's files. test `SerializationTest#escapesTheCharactersNedbSplitsLinesOn` |
| Durability | diff: appends are flushed (`force`), a failed write is rolled back, a torn last line is discarded instead of counting as corruption. test `DatastoreTest.Persistence#*`, `StoreStressTest#truncating...`, `KillDuringWriteStressTest` |

### 3.4 `EventRegistry`

| Item | Status |
| --- | --- |
| One file for events and snapshots, `kind` | test `EventRegistryTest`, `PersistedShapesTest#eventsSnapshotsAndSoftDeletedEvents` |
| `loadDatabaseIfNeeded` early-flag pattern; failed load never retried | diff: once-only, retried after a failure |
| `query(query, createdAt = 1, limit?, projections?)`: sort by `createdAt`, falsy limit = none, `Number(limit)` | test `EventRegistryTest.Query#*` |
| `replaceOrDeleteItem(id, newValue?)` | test `EventRegistryTest.ReplaceOrDeleteItem#replacesOrRemovesByDatabaseId` |
| `queryLatestSnapshot`: adds `kind: 'snapshot'`, greatest `snapshottedEventCreatedAt`, no limit | test `EventRegistryTest.QueryLatestEntitySnapshot#*` |
| `store`, `deleteAll`, `count` | test `EventRegistryTest.ThePublishMethod#*`, `.DeleteAll#shouldClearAllEvents` |

### 3.5 Events adapter

| Item | Status |
| --- | --- |
| `rawEventsToEnvelopes` identity | test `EventsAdapterTest.RawEventsToEnvelopes#*`; diff: typed envelopes (raw documents converted, `_id` exposed as `id`) |
| `readEntityEventsSince` query (exactly one argument), strict `$gt`, `deletedAt` filter, debug message | test `EventsAdapterTest.ReadEntityEventsSince#*` |
| `readEntityLatestSnapshot` query and both debug messages; `null` → `undefined` | test `EventsAdapterTest.ReadEntityLatestSnapshot#*` |
| `storeEvents`: sequential, own `createdAt` each (24-char ISO), dispatch once with the whole batch (also empty), awaited, re-entrant; failure stops the batch | test `EventsAdapterTest.StoreEvents#*`, `LocalProviderTest#storesEventsAndDispatchesThemInProcess`, `#eventHandlersCanStoreEventsReentrantly`, `#emptyBatchesAreDispatchedToo` |
| `retryIfError(..., OptimisticConcurrencyUnexpectedVersionError)` (never triggers, Q18) | test (same `Retrier` call) `EventsAdapterTest.StoreEvents#*` |
| Dispatched envelopes have no `_id`/`id` | diff: with the dispatch journal they carry the database `id` (needed for `storeDispatched`). test `EventsAdapterTest.StoreEvents#withTheJournalTheEventsGetTheirIdsAndTheBatchIsTracked`; the plain `EventsAdapter.storeEvents(userApp, registry, envelopes, config)` is TypeScript's |
| `storeEvents` persists the events one by one (a failure or a crash after the first ones leaves them stored) | diff: with the dispatch journal (what `events.store` uses) the batch is one atomic, flushed insert (`EventRegistry.storeAll`): all events of a command or none. test `EventsAdapterTest.StoreEvents#withTheJournalTheBatchIsStoredAtomically`, `RecoveryTest#failuresWhileStoringOrDispatchingClearTheJournalAsTheCallerRetries`, end to end `framework-integration-tests` `CrashUnderLoadIT` (a `ChangeMultipleCartItems` of 8 events killed mid-batch was stored 7 of 8 before the fix); the plain `EventsAdapter.storeEvents(userApp, registry, envelopes, config)` keeps TypeScript's loop |
| `storeSnapshot`: `createdAt = snapshottedEventCreatedAt`, `persistedAt = now`, always inserted | test `EventsAdapterTest.StoreSnapshot#storesTheSnapshotWithItsLogicalAndPhysicalDates`, `PersistedShapesTest#eventsSnapshotsAndSoftDeletedEvents` |
| `storeDispatchedEvent` always `true` (Q18) | diff: answers `false` for an event re-dispatched by the crash recovery that core already saw. test `EventsAdapterTest#storeDispatchedEventIsAlwaysTrueWithoutJournal`, `RecoveryTest#redispatchingIsDedupedByStoreDispatched` |
| (Java-only) crash recovery of undispatched events | test `RecoveryTest#*` |

### 3.6 Event delete adapter

| Item | Status |
| --- | --- |
| Filters (exact `createdAt`, `kind`, `deletedAt: { $exists: false }`) | test `EventDeleteAdapterTest#findDeletableEventQueriesTheExactEventAndExposesTheDatabaseId`, `#findDeletableSnapshotQueriesTheSnapshotsOfThatEvent` |
| Soft delete (`value: {}`, `deletedAt`, extra `id`, Q17), sequential | test `EventDeleteAdapterTest#deleteEventSoftDeletesEachEventSequentially`, `PersistedShapesTest#eventsSnapshotsAndSoftDeletedEvents` |
| Snapshot hard delete; warnings for empty lists; log locations | test `EventDeleteAdapterTest#deleteSnapshotRemovesEachSnapshot`, `#deleteEventWithoutEventsOnlyWarns`, `LocalProviderTest#deletingEventsAndSnapshots` |

### 3.7 Event search

| Item | Status |
| --- | --- |
| `buildFiltersForByTime` (inclusive, string order) | test `EventsSearcherBuilderTest#timeFiltersAreInclusiveAndOptional` |
| `buildFiltersForByFilters` (entity wins, error text with "an search") | test `EventsSearcherBuilderTest#filtersByEntityOrType` |
| `resultToEventSearchResponse` | test `EventsSearcherBuilderTest#mapsResultsToSearchResponses` |
| `searchEvents`: newest first, `kind: 'event'`, truthy limit, soft-deleted events returned (Q16) | test `EventsSearchAdapterTest#searchEventsBuildsTheQueryAndSortsNewestFirst`, `LocalProviderTest#deletingEventsAndSnapshots` |
| `searchEntitiesIds`: exact query, distinct ids by recency, offset cursor always present (Q15), `parseInt` rules and `NaN` cursor | test `EventsSearchAdapterTest#*`, `LocalProviderTest#searchAndPaginatedEntityIds`, `ModelTest#jsHelpersFollowJavaScript` |

### 3.8 `ReadModelRegistry`

| Item | Status |
| --- | --- |
| Unique sparse index on `uniqueKey` ensured after load | test `ReadModelRegistryTest`, `PersistedShapesTest#readModelsCarryTheirUniqueKey` |
| `query` (sort translation, falsy skip/limit ignored, projection of `value` only) | test `ReadModelRegistryTest.Query#*` |
| `toLocalSortFor` (dot paths, insertion order, only `'ASC'` ascends, Q5) | test `ReadModelRegistryTest.SortAndProjectionHelpers#toLocalSortFor`, `FilterOperatorSemanticsTest#sortingFollowsNedbOrderAcrossTypesAndOnlyAscSortsAscending` |
| `store`: `uniqueKey = type_id_version` (`undefined` text), version 1 inserts, otherwise compare-and-swap with the exact error message | test `ReadModelRegistryTest.TheStoreMethod#*`, `PersistedShapesTest#readModelsCarryTheirUniqueKey`; diff: the caller's envelope is not mutated (Java builds a new map) |
| CAS atomicity under concurrency | test `ReadModelRegistryTest.TheStoreMethod#concurrentWritersOfTheSameVersionOnlyOneWins`, `ProviderStressTest#hotReadModelsUpdatedConcurrentlyLoseNoIncrement` |
| `deleteById` removes at most one | test `ReadModelRegistryTest.DeleteById#*` |
| `filterFields`/`setNestedValue` incl. Q6, Q7 | test `ReadModelRegistryTest.SortAndProjectionHelpers#filterFieldsKeepsTheTypeScriptQuirks`, `.Query#shouldReturnOnlyProjectedFields*`, `FilterOperatorSemanticsTest#selectProjectsTheValues` |
| Sequence keys ignored (A2) | test (same behaviour) `ReadModelAdapterTest.FetchReadModel#*` |

### 3.9 Read model adapter

| Item | Status |
| --- | --- |
| `rawReadModelEventsToEnvelopes` identity | test `ReadModelAdapterTest.RawReadModelEventsToEnvelopes#*`; diff: typed envelopes |
| `fetchReadModel` query and debug messages | test `ReadModelAdapterTest.FetchReadModel#*`; diff: not found → empty list (Java SPI) instead of `[undefined]` |
| `storeReadModel`: unique violations → `OptimisticConcurrencyUnexpectedVersionError(message)` + warning; subscribers notified synchronously, failures only logged; disabled subscriptions | test `ReadModelAdapterTest.StoreReadModel#*`, `LocalProviderTest#readModelsWithOptimisticConcurrency`, `#notificationFailuresAreSwallowedAndDisabledSubscriptionsAreNotNotified` |
| `searchReadModel`: `typeName` overrides, skip = parsed cursor, paginated shape, cursor = `(limit or 1) + skip` (Q15) | test `ReadModelAdapterTest.SearchReadModel#*`, `FilterOperatorSemanticsTest#paginationIsAnOffsetCursor`; diff: always a `ReadModelListResult` (only `items` unless paginated), as the Java SPI defines |
| `deleteReadModel` swallows registry errors (Q22) | test `ReadModelAdapterTest.DeleteReadModel#*`; diff: a `null` read model is a no-op instead of a `TypeError` |

### 3.10 Filter translation (`searcher-adapter.ts`)

| Item | Status |
| --- | --- |
| Operator table (13 operators), nested properties, `and`/`or`/`not` | test `SearcherAdapterTest#*` (ports of TS tests 21–36), `ReadModelAdapterTest.SearchReadModel#*` (TS 43–58) |
| Every operator's matching semantics on real documents (arrays, null vs missing, mixed types, nested, regexes, `includes` with objects) | test `FilterOperatorSemanticsTest#operatorsMatchTheTypeScriptLocalProvider` (63 rows, each agreeing with TypeScript + NeDB 4.0.2) |
| Q3 only the first operator, Q4 array operands unwrapped | test `SearcherAdapterTest.OperatorsAndQuirksNotCoveredByTypeScript#*`, `FilterOperatorSemanticsTest` rows "Q3", "Q4" |
| Unescaped regex sources; JavaScript regex syntax and errors | fixed `cccaacd26`; test `JsRegExpTest`, `JsRegExpDifferentialTest`, `FilterOperatorSemanticsTest#operatorsThatNeedAStringOrAValidPatternFailLikeTypeScript` |
| Non-string operand errors (`begins-with`, `iRegex` wording) | test `SearcherAdapterTest.OperatorsAndQuirksNotCoveredByTypeScript#regexOperatorsNeedStrings` |
| `null` property filter → `TypeError`; non-empty string → infinite recursion | diff: `IllegalArgumentException` with an explicit message instead of a stack overflow |
| `not`/`or`/`and` nested in a property → `$foo.not` key rejected at query time | test (same) `SearcherAdapterTest` / `ModelTest.Matching#malformedQueries` (`Unknown logical operator`) |
| Operands that are not raw JSON (UUID, enum, date) | diff: converted to their JSON form (Java values); test `SearcherAdapterTest.OperatorsAndQuirksNotCoveredByTypeScript#nonRawOperandsAreConvertedToJson`, `FilterOperatorSemanticsTest` row "eq uuid operand" |

### 3.11 `WebSocketRegistry`

TTL index (`expirationTime`, 172800 s, not awaited, numbers never expire, Q8), `query`/`store`/`delete`/`deleteAll`/
`count`, `createdAt` sort is a no-op: test `WebSocketRegistryAndJournalTest#storesQueriesAndDeletesDocuments`,
`#theTtlIndexOnlyExpiresDatesAndOnlyInTheProcessThatCreatedIt`, `PersistedShapesTest#connectionsAndSubscriptions`.

### 3.12 Connections adapter

| Item | Status |
| --- | --- |
| `storeConnectionData` with `connectionID` | test `ConnectionsAndSubscriptionsAdapterTest#storesConnectionDataWithItsConnectionId`, `PersistedShapesTest#connectionsAndSubscriptions` |
| `fetchConnectionData` queries `connectionId` and never finds anything (Q2) | diff (bug fix): queries `connectionID`, so websocket operations get their user. test `ConnectionsAndSubscriptionsAdapterTest#fetchesConnectionDataByConnectionId` |
| `deleteConnectionData` info/debug messages | test `ConnectionsAndSubscriptionsAdapterTest#deletesConnectionData`, `LocalProviderTest#subscriptionsAndConnections` |
| `sendMessageToConnection` (location `connection-adapter#...`) | test `ConnectionsAndSubscriptionsAdapterTest#sendsMessagesThroughTheWebSocketServer` |

### 3.13 Subscription adapter

| Item | Status |
| --- | --- |
| `subscribeToReadModel`, `fetchSubscriptions` by `className`, `deleteAllSubscriptions` by `connectionID` | test `ConnectionsAndSubscriptionsAdapterTest#subscriptions`, `ProviderStressTest#concurrentConnectionsAndSubscriptionsKeepTheirBookkeeping` |
| `deleteSubscription` filters on a `subscriptionID` field that does not exist (Q9, A1) | diff (bug fix): matches `operation.id`, the id a `stop` message carries (the Azure provider stores it as `subscriptionID`); otherwise a stopped subscription keeps receiving data until disconnect. test `LocalProviderTest#subscriptionsAndConnections` |

### 3.14 Websocket server

| Item | Status |
| --- | --- |
| Separate port (`LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT`/65529), path `/websocket`, info log | test `WebSocketServerAdapterTest#*`, `ProviderTest#constantsAreTheTypeScriptOnes` |
| UUID connection ids; CONNECT/MESSAGE/DISCONNECT envelopes | test `WebSocketServerAdapterTest#dispatchesConnectMessagesAndDisconnectInOrder`, infra: `WebSocketTest` |
| First offered sub-protocol accepted; connections without one accepted | test `WebSocketServerAdapterTest#dispatchesConnectMessagesAndDisconnectInOrder` (`graphql-ws` echoed), `#connectionsAreIndependentAndHandlerFailuresAreLogged` (no sub-protocol), infra: `WebSocketTest`; same as `ws` (so docs acceptance item 17 does not hold for the local provider in TypeScript either) |
| Listeners attached after CONNECT, frames lost (Q11); messages of a connection processed concurrently | diff: frames are queued and processed in order per connection |
| Closed connections never removed (Q10) | diff: removed on close |
| Unparsable message logged, nothing sent | test `WebSocketServerAdapterTest#connectionsAreIndependentAndHandlerFailuresAreLogged` |
| `sendToConnection` to an unknown id → `TypeError` | diff: `IllegalStateException`; test `LocalProviderTest#subscriptionsAndConnections` |
| Upgrade on another path → 400 | diff: 404 (Jetty); test `WebSocketServerAdapterTest#onlyTheWebSocketPathUpgrades` |

### 3.15 GraphQL request adapter

| Item | Status |
| --- | --- |
| HTTP envelope (`connectionID` undefined, raw `authorization` as token, body as value, context) and debug message | test `GraphQLAdapterTest.HttpRequests#*` (TS tests 19, 20) |
| WebSocket envelope (event type from the context, upgrade headers, two-space debug message), error envelope without `connectionId` | test `GraphQLAdapterTest.WebSocketMessages#*` |
| Non-object HTTP bodies passed to core as they are | fixed `a3270a0b3`; test `GraphQLAdapterTest.HttpRequests#bodiesThatAreNotObjectsReachCoreAsTypeScriptHandsThem` |
| Typed `value` (`GraphQLOperation` / `GraphQLClientMessage`) | diff: websocket messages outside the protocol become the error core reports for them in TypeScript; test `GraphQLAdapterTest.WebSocketMessages#messagesOutsideTheProtocolBecomeTheErrorCoreReports` |

### 3.16–3.20 Small adapters and services

| Item | Status |
| --- | --- |
| `requestSucceeded` (headers always an object), `requestFailed` (status by exact class name, `toClassTitle`), `healthRequestResult` (`status-code` 200/503) | test `SmallAdaptersTest.Api#*` |
| `rawScheduledInputToEnvelope` (error text, falsy `typeName`) | test `SmallAdaptersTest.Scheduled#*`; diff: `IllegalArgumentException` instead of `Error` (ARCHITECTURE §7) |
| `rawRocketInputToEnvelope` (request property wins over the environment, `??`) | test `SmallAdaptersTest.Rocket#theRequestPropertyWinsOverTheEnvironment` |
| `databaseUrl`, `graphqlFunctionUrl` (`''` on error), `isGraphQLFunctionUp` (real POST of `query { __typename }`), `rawRequestToSensorHealth` (mount-relative path, query string kept, body `{}`), `areRocketFunctionsUp` (scope and last segment stripped) | test `SmallAdaptersTest.Health#*` |
| `countAll` / health details count every document (snapshots, soft-deleted events) | test `PersistedShapesTest#healthCountsEveryDocumentIncludingSnapshotsAndSoftDeletedEvents`; diff: counts the loaded documents instead of reloading the file on every request (Q19) |
| Database "up" = data file exists (false until first use, Q19) | test `LocalProviderTest#graphQLApiScheduledRocketsAndSensor` |
| `GraphQLService`/`HealthService` delegate to the application | test `LocalProviderTest#graphQLApiScheduledRocketsAndSensor` |

### 3.21–3.25 Infrastructure

| Item | Status |
| --- | --- |
| Rockets loaded eagerly with the three error messages | test infra: `RocketMountingTest#rocketsAreLoadedWhenTheInfrastructureIsCreated`, `#loadingErrorsKeepTheTypeScriptMessages` |
| Route order: `/sensor/health`, `/graphql`, then rockets on the same router | test infra: `RocketMountingTest#theFrameworkRoutesComeFirst`, `#rocketsMountTheirRoutesWithTheBoundPortInOrder` |
| Middleware order: JSON (6 MB, `rawBody`), urlencoded (extended, 6 MB), `cors()`, `Access-Control-Allow-*` headers, router, error handler | test infra: `LocalServerTest#*`, `web.BodyParsersTest#*`, `web.WebHelpersTest#cors*` |
| Malformed JSON / too large → 500 `{title, reason}` (class names not in the status table) | test infra: `LocalServerTest#malformedJsonIsAnInternalErrorWithoutCorsHeaders`, `#jsonBodiesAreLimitedToSixMegabytes` |
| Unknown routes → Express 404 page; CORS pre-flight 204 | test infra: `LocalServerTest#unknownRoutesGetExpressNotFoundPage`, `#corsPreflightRequestsAreAnsweredDirectly` |
| Scheduler started once listening | test infra: `LocalServerTest#theSchedulerTriggersScheduledCommandsOnceListening` |
| `start` resolves right after `listen`, no error handler | diff: blocks until SIGINT/SIGTERM and stops gracefully; a taken port fails `start`. test infra: `SignalShutdownTest`, `GracefulShutdownTest`, `LocalServerTest#aTakenPortFailsTheStart` |
| `defaultErrorHandler` → `requestFailed` (`Error('x')` → 500 `{title:'Error', reason:'x'}`) | test infra: `InfrastructureTest#requestFailedUsesTheErrorClassForStatusAndTitle`; diff: Java exception classes give Java titles (ARCHITECTURE §7) |
| GraphQL controller: always 200 with `result`, headers ignored (Q12), error answered and passed to `next` | test infra: `controllers.GraphQLControllerTest` (TS tests 88–94) |
| Health controller: 200 even when unhealthy (Q13), failure body `{title}` only (Q14) | test infra: `controllers.HealthControllerTest#*`, `LocalServerTest#unhealthyApplicationsStillAnswer200`, `#healthFailuresAnswerTheirCodeWithOnlyTheTitle` |
| Scheduler: 5-field cron `minute hour day month weekDay`, `*` defaults, `year` ignored, local time zone, second 0 | test infra: `SchedulerTest#createsTheFiveFieldExpressionWithStarsForMissingFieldsAndWithoutTheYear`, `CronExpressionTest#*` |
| `scheduledOn: {}` runs every minute in TypeScript | diff: never scheduled, as the documentation (F162, acceptance item 23) and the Azure provider say. test infra: `SchedulerTest#schedulesOneJobPerCommandWithASchedule` |
| Deprecated `boosterTriggerScheduledCommand`, promise not awaited (Q21) | diff: calls `boosterTriggerScheduledCommands` (same function), failures logged, runs do not wait for each other (as TypeScript). test infra: `SchedulerTest#runsDoNotWaitForPreviousOnes`, `#failingRunsAreLoggedAndTheJobGoesOn` |
| `InfrastructureRocket.mountStack(config, router, { port })`, `unmountStack` never called | test infra: `RocketMountingTest#*`; diff: `unmountStack` is called on shutdown, the port is the bound port |

### 3.26 Test helpers (Q23)

diff: real counters and queries over the `.booster` files, ports from the environment, a readiness check. test infra:
`testhelper.LocalTestHelperTest#*`, `testhelper.ReadinessTest`.

### 3.27 Logging

Locations and messages (including TypeScript's inconsistencies such as `connection-adapter#...`,
`events-searcher-adapter#...`, the two spaces in `Received  WebSocket GraphQL request:`) are kept; the adapter tests
verify the logger calls (`verify(logger).debug("[Booster]|<location>: ", ...)`). The infrastructure logs through
`config.logger` instead of `console.error` (diff, see infra README).

## 3. Data shapes (spec §4)

| Shape | Status |
| --- | --- |
| 4.1 event document, soft-deleted event | test `PersistedShapesTest#eventsSnapshotsAndSoftDeletedEvents`, `NedbFixtureCompatibilityTest#writesEventsInTheTypeScriptShape` |
| 4.2 snapshot document | test `PersistedShapesTest#eventsSnapshotsAndSoftDeletedEvents` |
| 4.3 read model document + index line | test `PersistedShapesTest#readModelsCarryTheirUniqueKey` |
| 4.4 connection document + TTL index line | test `PersistedShapesTest#connectionsAndSubscriptions` |
| 4.5 subscription document | test `PersistedShapesTest#connectionsAndSubscriptions` |
| Files written by TypeScript read by Java | test `NedbFixtureCompatibilityTest#*`, `NedbDifferentialTest#dataFilesWrittenByNedbLoadAndCompactTheSame` |
| 4.6 HTTP GraphQL request/response | test infra: `LocalServerTest#servesGraphQLPostsWithTheResultOfTheApplication`, `#errorsOfTheApplicationAreAnsweredWithTheirStatusTitleAndReason` |
| 4.7 HTTP health | test infra: `LocalServerTest#healthRequestsGetTheComponentPathRelativeToTheEndpoint` |
| 4.8 WebSocket frames and raw requests | test `WebSocketServerAdapterTest`, infra: `WebSocketTest` |
| 4.9 envelopes and queries handed to the stores | test `EventsAdapterTest`, `EventsSearchAdapterTest`, `ReadModelAdapterTest`, `SmallAdaptersTest` |

## 4. Behaviours pinned by the TypeScript tests (spec §5)

| TS tests | Java |
| --- | --- |
| 1–2 `rawEventsToEnvelopes` | `EventsAdapterTest.RawEventsToEnvelopes#shouldReturnAnEmptyArrayOfEnvelopes`, `#shouldReturnAnArrayOfEnvelopes` |
| 3–7 `readEntityEventsSince` | `EventsAdapterTest.ReadEntityEventsSince#shouldReturnExpectedResult`, `#dateProvidedShouldCallEventRegistryQueryWithExpectedInput`, `#dateNotProvidedShouldQueryFromTheOriginOfTime` |
| 8–12 `readEntityLatestSnapshot` | `EventsAdapterTest.ReadEntityLatestSnapshot#*` |
| 13–16 `storeEvents` | `EventsAdapterTest.StoreEvents#noEventEnvelopesShouldNotStoreButShouldDispatch`, `#withEventEnvelopesShouldStoreThemWithTheirCreationTime`, `#withEventEnvelopesShouldCallTheDispatcherWithTheStoredEvents` |
| 17–18 `searchEntitiesIds` query | `EventsSearchAdapterTest#generatesTheQueryForEntityTypeNameLimitAndAfterCursor`, `#generatesTheQueryForEntityTypeNameAndLimit` |
| 19–20 GraphQL adapter | `GraphQLAdapterTest.HttpRequests#shouldCallLoggerDebug`, `#shouldGenerateTheExpectedEnvelope` |
| 21–36 `queryRecordFor` | `SearcherAdapterTest.ConvertsSimpleOperators#*`, `.ConvertsOperatorsThatRelyOnRegexes#*` |
| 37–38 `rawReadModelEventsToEnvelopes` | `ReadModelAdapterTest.RawReadModelEventsToEnvelopes#*` |
| 39–40 `fetchReadModel` | `ReadModelAdapterTest.FetchReadModel#*` (40: empty list instead of `[undefined]`, diff above) |
| 41–42 `storeReadModel` | `ReadModelAdapterTest.StoreReadModel#shouldCallReadModelRegistryStore`, `#shouldLogTheRightDebugMessage` |
| 43–59 `searchReadModel` | `ReadModelAdapterTest.SearchReadModel#*` |
| 60 `deleteReadModel` | `ReadModelAdapterTest.DeleteReadModel#deleteOneReadModel` |
| 61–67 `EventRegistry` | `EventRegistryTest.*` |
| 68–87 `ReadModelRegistry` | `ReadModelRegistryTest.*` (87, ambiguous in TS (A3): `TheStoreMethod#shouldThrowWhenNothingCanBeUpdated` pins the real outcome) |
| 88–94 `GraphQLController` | infra: `controllers.GraphQLControllerTest.HandleGraphQL.*` |

## 5. Quirks and ambiguities (spec §7)

| # | Decision |
| --- | --- |
| Q1 same-millisecond order | diff: insertion order (monotonic ids), deliberate and safe |
| Q2 `fetchConnectionData` key | diff: fixed (bug) |
| Q3 first operator only | kept, test |
| Q4 array operands unwrapped | kept, test |
| Q5 only `'ASC'` ascends | kept, test |
| Q6, Q7 projection quirks | kept, test |
| Q8 TTL never expires numbers; index not awaited; TTL lost after reload | kept, test |
| Q9 `deleteSubscription` | diff: fixed (bug), matches `operation.id` |
| Q10 closed connections kept | diff: removed |
| Q11 early frames lost, concurrent messages | diff: queued, in order |
| Q12 result headers not sent | kept, test |
| Q13 health 200 when unhealthy | kept, test |
| Q14 health failure body `{title}` | kept, test |
| Q15 offset cursor always present | kept, test |
| Q16 search returns soft-deleted events | kept, test |
| Q17 soft-deleted events gain `id` | kept, test |
| Q18 no retry trigger; `storeDispatched` always `true` | retry kept; `storeDispatched` diff (journal dedup) |
| Q19 DB up = file exists; `countAll` reloads | first kept, second diff (counts loaded documents) |
| Q20 port bound per `Provider()` call | diff: once per process |
| Q21 deprecated alias, `year` ignored, unawaited | `year` ignored kept; others diff (logged, same function) |
| Q22 `deleteReadModel` swallows errors, `TypeError` on undefined | swallowing kept; `null` diff (no-op) |
| Q23 stub test helpers | diff: implemented |
| A1 `subscriptionID` | resolved with the core and Azure code: `stop` carries `operation.id`; Q9 fixed accordingly |
| A2 sequence keys | kept (unsupported in TypeScript's local provider too) |
| A3 test 87 | real outcome pinned |
| A4 library behaviour unverified in the spec | verified now: NeDB 4.0.2 by `NedbDifferentialTest`; V8 regular expressions by `JsRegExp*Test`; `ws`/Express/`cors`/`node-schedule` behaviour by the infrastructure tests |
| A5 test passes a `WebSocketRegistry` | n/a (only the query shape matters; ported as such) |

## 6. Documentation checklist (spec 18), local-provider items

| Item | Status |
| --- | --- |
| F162 / acceptance 23: empty schedule never triggered; `0/5` every five minutes | diff vs TypeScript local (see 3.24), matches the docs; test infra: `SchedulerTest`, `CronExpressionTest#aSingleValueStepRepeatsFromThatValue` |
| F232: local GraphQL URL `http://localhost:<port>/graphql` | test infra: `LocalServerTest#logsAListeningLineAndPublishesThePort` |
| F246: `eq`/`ne` null, `regex`/`iRegex` on local, `includes` on complex elements | test `FilterOperatorSemanticsTest` (in the local provider `includes: {sku}` matches elements containing that field, in TypeScript too) |
| F250 / acceptance 32: sorting, "only one sort field" | the local provider (TypeScript and Java) sorts by several fields: test `ReadModelRegistryTest.Query#shouldReturnAllResultsSortedByAgeAndId` |
| F264: port 3000, `-p` | `start(config, port)`; the default belongs to the CLI |
| F298: data under `.booster`, delete it to reset | test `ProviderTest#pathsAreTheTypeScriptOnes`, `RestartTest#dataSurvivesARestart` |
| F310 / F318: health endpoints, local details `{file, count}`, rockets always up | test infra: `LocalServerTest#healthRequests*`, `SmallAdaptersTest.Health#*`, `PersistedShapesTest#healthCounts...` |
| F348 / acceptance 35: delete events (tombstone kept, snapshots removed, still listed by event search) | test `LocalProviderTest#deletingEventsAndSnapshots`, `PersistedShapesTest#eventsSnapshotsAndSoftDeletedEvents` |
| F361: rockets loaded by package name | test infra: `RocketMountingTest` (Java: a `Rocket` class implementing `RocketBuilder` in that package) |
| Acceptance 16–17: subscriptions over `graphql-ws` | test infra: `WebSocketTest` (end-to-end delivery belongs to the integration tests) |
| Acceptance 21: `to:"2021-02-13"` excludes that day (string comparison) | test `EventsSearcherBuilderTest#timeFiltersAreInclusiveAndOptional`, `FilterOperatorSemanticsTest` row "lt ISO date prefix" |
| Acceptance 30–31: `select` | test `FilterOperatorSemanticsTest#selectProjectsTheValues`, `ReadModelRegistryTest.Query#shouldReturnOnlyProjectedFields*` |
| Acceptance 27: `register.responseHeaders` reach the HTTP response | does not hold for the local provider in TypeScript (Q12: result headers are never written); kept, test infra: `controllers.GraphQLControllerTest.HandleGraphQL.OnSuccess#doesNotSendTheHeadersOfTheResult` |
| Acceptance 38: a failing health component answers `503` | does not hold for the local provider in TypeScript (Q13: always `200`); kept, test infra: `LocalServerTest#unhealthyApplicationsStillAnswer200` |
| Acceptance 39: a rejecting `globalAuthorizer` gives `401` on health | test infra: `LocalServerTest#healthFailuresAnswerTheirCodeWithOnlyTheTitle` (the code of the failure result, body `{title}`) |
| F370–F383, acceptance 41–42 (webhook, file uploads) | out of scope: rocket modules (`rockets/*`) |

## 7. Stress and differential results

Measured on the development machine (Apple silicon, APFS, JDK 27) while writing this ledger:

| Test | Result |
| --- | --- |
| `StoreStressTest#concurrentWritersReadersAndCompactionsLoseNothing` | 16 virtual-thread writers × 120 insert/replace/remove operations, 8 readers checking that every document they see is a complete version, a thread compacting continuously, growth compaction every 300 obsolete lines: no lost or torn write, same state after reopening, no temporary file left |
| `StoreStressTest#uniqueKeysStayUniqueUnderConcurrentInserts` | 200 virtual threads × 20 inserts of the same 20 unique keys: exactly 20 accepted |
| `StoreStressTest#truncatingTheFileAtAnyOffsetKeepsEveryCompleteWriteBeforeIt` | 101 single-line operations (multi-byte UTF-8 text), truncated at every line boundary ±1 and 100 random offsets: the loaded state is always exactly the operations whose JSON is complete; no corruption warning; later writes never glue onto the torn tail |
| `StoreStressTest#aZeroFilledTailIsAnInterruptedWriteToo` | the same with 512 zero bytes after the cut (pre-allocated blocks after a crash) |
| `StoreStressTest#aCrashDuringCompactionLosesNothing` | a partial `<file>~` next to an intact file, and a complete `<file>~` without the file: the state is always the full one |
| `KillDuringWriteStressTest` | a writer JVM killed with `SIGKILL` 8 times on the same file (after 50–650 acknowledged operations, or after a random delay that can land in the start-up load and compaction): about 1650 acknowledged operations, every one present after each kill; the only uncertain operation is the one in flight |
| `StoreStressTest#loadsAndQueriesOneHundredThousandEvents` | 100k events (38.9 MiB): written in batches in 0.6–0.7 s, loaded and compacted in about 0.65 s, an entity's events (lookup index) in under 1 ms, a full scan with projection and sort in 0.15–0.2 s |
| `StoreStressTest#loadsOneHundredThousandReadModelsWithTheirUniqueIndex` | 100k read models: loaded with the unique index in about 0.5 s; one flushed single-document write costs about 4 ms (the `fsync`), so a compare-and-swap update costs about 4 ms |
| `StoreStressTest#creatingATtlIndexWhileReadersRunIsSafe` | 16 readers while a TTL index expires half of 3000 documents: no reader sees a broken collection (regression test for `2fe6bceac`) |
| `ProviderStressTest` | 24 virtual threads incrementing 4 hot read models through optimistic concurrency (with conflicts): no increment lost; 24 threads storing 96 event batches (half on one hot entity): every event stored, dispatched once, in order within its batch, journal empty; 40 connections subscribing, stopping and disconnecting concurrently: exact bookkeeping, also after reopening |
| `NedbDifferentialTest` | 996 cases against NeDB 4.0.2: all equal |
| `JsRegExpDifferentialTest` | 1500 random patterns against V8: all equal |

Performance note (diff): every acknowledged write is flushed to the device (`FileChannel.force`), which TypeScript's
NeDB never does. On this machine that costs about 4 ms per write and the write lock is held during the flush, so one
collection accepts roughly 250 single-document writes per second. That is ample for the local development server;
batch writes (`insert(List)`) share one flush.

## 8. Remaining differences that are not implemented

* JavaScript's non-Unicode case folding for the few characters where it differs from Java's (`ſ` and the Kelvin sign
  match `s`/`k` in Java with `iRegex`, not in JavaScript); code-unit versus code-point matching outside the BMP; a
  back reference to a group that did not participate matches the empty string in JavaScript and fails in Java.
* A non-index property set on an array inside a NeDB modifier (`$set: { 'arr.x': 1 }`) is kept on the array object by
  JavaScript (and lost when serialized); Java ignores it, so a `$push` on such a path fails instead of being lost.
* The infrastructure items listed under "Not implemented" in its README (Express features no framework or rocket code
  uses, `node-schedule` features the framework does not use, V8-exact JSON error positions).
