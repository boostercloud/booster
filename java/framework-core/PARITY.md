# framework-core parity ledger

Audit of the Java port of `packages/framework-core` against (1) every behaviour the TypeScript unit tests pin
(section "Behaviours pinned by tests" of specs 04–09) and (2) the core-relevant items of the documentation checklist
(spec 18). Audited on 2026-11-10 against `port/core` at `cb5b6b2c4` (the TypeScript sources under `packages/` are the
reference; where a spec and the code disagree, the code wins).

Every row names the Java test that pins the behaviour. Tests are `Class#method` under
`src/test/java/com/boostercloud/framework/core` (nested test classes are omitted from the name).

**Status legend**

| Status | Meaning |
| --- | --- |
| covered | The Java code implements it and the named test pins it (checked by reading the test). |
| fixed `sha` | The behaviour was implemented but no test pinned it (or pinned it only partially); the test was added in that commit. No main-code change was needed. |
| differs | Intentionally different; the reason is given (and is listed in README "Differences from TypeScript"). The test pins the Java behaviour. |
| elsewhere | Owned by another module (foundation, provider, CLI, rocket); core only passes values through. The owning test is named when there is one. |
| n/a | Informational guidance, naming recommendations or documentation defects with no behaviour to port. |

## Summary

Counts are ledger rows (a row may group several numbered items that share one test).

| Part | Rows | covered | fixed | differs | elsewhere | n/a | still missing |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1. TypeScript-pinned behaviours (specs 04–09) | 284 | 268 | 2 | 14 | 0 | 0 | 0 |
| 2. Documentation checklist (spec 18, core-relevant) | 205 | 165 | 6 | 9 | 17 | 8 | 0 |

Missing → fixed: 8 rows. Part 1: spec 04 #3 and #4 (`Booster.readModel(...).search()` was pinned one layer down, at
the reader, not at the provider call). Part 2: F078 (asynchronous calculated fields), F282 and acceptance 26
(`register.flush()` partial success), F284 (`register.context`), F341 and acceptance 33 (a stored snapshot migrated on
load and not written back). No main code had to change: every gap was a missing test, and every new test passed
against the existing implementation.

Commits of this audit:

* `9b1c27722` — `BoosterTest#theSearcherCallsTheProviderSearchOnceWithTheTypeScriptArgumentsAndReturnsInstances`
* `67d71d31b` — `BoosterCommandDispatcherTest#eventsFlushedBeforeAHandlerFailureStayStoredAndTheLaterOnesAreNot`,
  `BoosterCommandDispatcherTest#theRegisterCarriesTheRequestContextOfTheCommand`
* `1a562c579` — `GraphQLExecutionTest#waitsForAsynchronousCalculatedFieldsAndTypesThemWithTheirResult`
* `50d7233d0` — `EventStoreTest#returnsTheMigratedSnapshotWithoutStoringItBackWhenThereAreNoPendingEvents`,
  `EventStoreTest#failsWhenTheLatestSnapshotHasAnUnknownSchemaVersion`
* `b403e1c3e` — not a parity gap: `integration.MigrationsIntegrationTest#theDataMigrationEventsAreNotReadableThroughTheApi`
  failed intermittently (it compared the in-memory store's strictly increasing `createdAt` with the clock); the
  assertion now allows that bump

---

## Part 1 — Behaviours pinned by the TypeScript tests

### Spec 04 — registration, `Booster`, `Importer`, entry points

| # | Pinned behaviour | Java test | Status |
| --- | --- | --- | --- |
| 1 | `configure` records every environment, applies only the current one | `BoosterTest#configuresTheCurrentEnvironmentAndRecordsEveryEnvironment` | covered |
| 2 | `start` imports the user project once | `BoosterTest#startImportsTheApplicationRegistersTheConceptsAndTheUserApp`, `ImporterTest#importsEachClassOnce` | differs: a package scan of the root class replaces the replaceable `Importer.importUserProjectFiles` |
| 3 | `Booster.readModel(X).search()` calls provider `search` once with `(any, 'X', {}, {}, undefined, undefined, false, undefined)` | `BoosterTest#theSearcherCallsTheProviderSearchOnceWithTheTypeScriptArgumentsAndReturnsInstances` (+ `#readModelReturnsASearcherBackedByTheReadModelsReader`) | fixed `9b1c27722` (was pinned only at the reader level) |
| 4 | its results are class instances | same test | fixed `9b1c27722` |
| 5 | `entitiesIDs` calls the provider once | `BoosterTest#entitiesIDsCallsTheProviderWithItsParameterOrder` | covered |
| 6 | `events` binds values to their event classes; provider called once | `BoosterEventSearchTest#bindsEveryValueToTheClassOfItsEvent`, `BoosterTest#eventsDelegatesToTheEventSearch` | covered |
| 7 | unknown type stays a plain value | `BoosterEventSearchTest#keepsTheRawValueOfAnUnregisteredType` | covered |
| 8 | notification class is bound | `BoosterEventSearchTest#bindsTheValueOfANotification` | covered |
| 9 | `entity` returns the snapshot value; `fetchEntitySnapshot('SomeEntity','42')` once | `BoosterTest#entityBuildsAnInstanceFromTheSnapshot` | differs: returns `Optional` |
| 10 | `entity` without `value` is still an instance | `BoosterTest#entityWithoutValueIsAnEmptyInstanceAndWithoutSnapshotIsEmpty` | covered |
| 11 | `loadTokenVerifierFromEnv` adds a `JwksUriTokenVerifier` from the three variables | `BoosterTest#loadsTheTokenVerifierFromTheEnvironment` | covered |
| 12 | nothing added when they are unset | `BoosterTest#doesNotLoadTheTokenVerifierWhenAVariableIsMissing` | covered |
| 13 | importer walks sub-directories first, skips `index.js`/`.ts`/`.d.ts` | `ImporterTest#findsTheClassesOfThePackageTreeInPathOrderSkippingAnonymousClasses` | differs: class-file scan (no JS files); deterministic path order, nested classes after their enclosing class |
| 14 | `@Command({authorize: fn})` metadata (class, properties, methods, authorizer identity, `before: []`) | `decorators.CommandTest#addsTheCommandClassAsACommand` | differs: properties/methods are `ClassMetadata` with Java types; custom authorizers are classes |
| 15 | `@Command({})` → `denyAccess` | `decorators.CommandTest#setsTheDenyAuthorizerByDefault` | covered |
| 16 | `before` hooks stored | `decorators.CommandTest#storesTheBeforeHooks` | covered |
| 17 | `@Entity` + `@Reduces` registration, deny by default | `decorators.EntityTest#addsTheEntityAndItsReducers` | covered |
| 18 | `authorizeReadEvents: 'all'` | `decorators.EntityTest#setsTheAllowAuthorizerWithAll` | covered |
| 19 | role list → `authorizeRoles([Manager], user)` | `decorators.EntityTest#setsARolesAuthorizer` | differs: a `RolesAuthorizer` record instead of a replaceable static |
| 20 | custom read-events authorizer | `decorators.EntityTest#setsACustomAuthorizer` | covered |
| 21 | `@Event` → `{ class }` | `decorators.EventTest#addsTheEventClassAsAnEvent` | covered |
| 22 | `@EventHandler` appends the handler | `decorators.EventHandlerTest#addsTheHandlerToTheEventHandlers` | covered |
| 23 | `@GlobalErrorHandler()` registers the class | `decorators.GlobalErrorHandlerTest#registersTheGlobalErrorHandler` | covered |
| 24 | … also with hook methods | `decorators.GlobalErrorHandlerTest#registersAHandlerWithHooks` | covered |
| 25 | `@ToVersion(1)` → `Migration versions must always be greater than 1` | `decorators.MigrationTest#throwsWhenTheVersionIsLowerThan2` | covered |
| 26 | migration methods in declaration order | `decorators.MigrationTest#collectsTheMigrationMethodsInDeclarationOrder` | covered |
| 27 | several migration classes of one concept | `decorators.MigrationTest#addsSeveralMigrationClassesToTheSameConcept` | covered |
| 28 | `There is an already defined migration for version 3` | `decorators.MigrationTest#throwsWhenAVersionIsDuplicated` | covered |
| 29 | `No migration methods found in this class` | `decorators.MigrationTest#throwsWhenThereAreNoMigrationMethodsLeavingAnEmptyMap` | covered |
| 30 | `@Notification()` → `{ class }` | `decorators.NotificationTest#addsTheNotificationWithTheDefaultTopic` | covered |
| 31 | `topicToEvent['my-topic']` | `decorators.NotificationTest#setsTheTopic` | covered |
| 32 | `@partitionKey` → `partitionKeys` | `decorators.NotificationTest#setsThePartitionKey` | covered |
| 33 | `@ReadModel({})` entry with properties | `decorators.ReadModelTest#registersTheReadModelWithItsProperties` | differs: Java property metadata (no `importPath`/TS `typeGroup` names) |
| 34 | `before` hooks | `decorators.ReadModelTest#storesTheBeforeHooks` | covered |
| 35 | numbers and readonly arrays | `decorators.ReadModelTest#describesNumbersAndArrays` | covered |
| 36 | role authorizer accepts/rejects | `decorators.ReadModelTest#authorizesByRole` | covered |
| 37 | custom authorizer | `decorators.ReadModelTest#usesACustomAuthorizer` | covered |
| 38 | `@Projects` registration | `decorators.ReadModelTest#registersProjections` | covered |
| 39 | `@sequencedBy` → `readModelSequenceKeys` | `decorators.ReadModelTest#registersTheSequenceKey` | covered |
| 40 | `@CalculatedField` dependencies | `decorators.ReadModelTest#addsTheDependenciesOfCalculatedFields` | covered |
| 41 | `@Role({auth:{}})` | `decorators.RoleTest#registersARoleWithEmptyAuth` | covered |
| 42 | `@Role` auth settings | `decorators.RoleTest#registersTheAuthSettings` | covered |
| i1–i8 | each `index.ts` entry point delegates once with the raw request (and `Booster.config` for the static ones) | `BoosterUserAppTest` (all 8 methods) | covered |
| t1–t2 | `@Trace` keeps `this`; `onStart` and `onEnd` called | `instrumentation.TraceTest#keepsThisAndNotifiesStartAndEndAroundTheMethod` | covered |

### Spec 05 — commands, register, authorization, tokens, global errors, tracing

| # | Pinned behaviour | Java test | Status |
| --- | --- | --- | --- |
| 1 | `The required command "version" was not present` | `BoosterCommandDispatcherTest#failsWhenTheVersionIsMissing` | covered |
| 2 | `Could not find a proper handler for X` | `BoosterCommandDispatcherTest#failsWhenThereIsNoHandler` | covered |
| 3 | role denial is a `NotAuthorizedError`, nothing runs after it | `BoosterCommandDispatcherTest#failsWhenTheUserIsNotAuthorizedAndRunsNothingElse` | covered |
| 4 | `handle` receives the value | `BoosterCommandDispatcherTest#callsTheHandlerWithAnInstanceAndReturnsItsResult` | covered |
| 5 | response headers shared with the context | `BoosterCommandDispatcherTest#sharesTheResponseHeadersWithTheContext` | covered |
| 6 | `RegisterHandler.handle(config, register{requestID, currentUser, eventList})` | `BoosterCommandDispatcherTest#storesTheRegisteredEventsWithTheRequestAndUser` | covered |
| 7 | asynchronous handlers are awaited | `BoosterCommandDispatcherTest#waitsForAsynchronousHandlers` | covered |
| 8 | `before` hook applied before instantiation | `BoosterCommandDispatcherTest#appliesTheBeforeHooksBeforeCreatingTheInstance` | covered |
| 9 | hooks chained in order | `BoosterCommandDispatcherTest#chainsTheBeforeHooksInOrder` | covered |
| 10 | every event wrapped, one `store` call | `BoosterRegisterHandlerTest#storesAllTheEventsInOneCall` | covered |
| 11 | no events → no `store` | `BoosterRegisterHandlerTest#doesNothingWithoutEvents` | covered |
| 12 | exact envelopes given to `store` | `BoosterRegisterHandlerTest#storesAllTheEventsInOneCall` | covered |
| 13 | domain event envelope | `BoosterRegisterHandlerTest#wrapsDomainEvents` | covered |
| 14 | notification defaults `default`/`defaultTopic` | `BoosterRegisterHandlerTest#wrapsNotificationsWithTheDefaults` | covered |
| 15 | `BoosterEntityMigrated` is `superKind: booster` in the old stream | `BoosterRegisterHandlerTest#wrapsInternalEventsEvenWhenAReducerIsRegistered` | covered |
| 16–19 | `allowAccess`, `denyAccess`, `authorizeRoles` accept/reject | `BoosterAuthorizerTest#allowAccessAllows`, `#denyAccessDenies`, `#authorizeRolesAllowsUsersWithOneOfTheRoles`, `#authorizeRolesDeniesOtherUsersAnonymousUsersAndEmptyRoleLists` | covered |
| 20 | custom claims, header `alg`, roles | `BoosterTokenVerifierTest#acceptsCustomClaims` | covered |
| 21 | single string role | `BoosterTokenVerifierTest#acceptsABearerTokenWithASingleRole` | covered |
| 22 | `''` role → `[]` | `BoosterTokenVerifierTest#anEmptyRoleIsNoRole` | covered |
| 23 | role array | `BoosterTokenVerifierTest#acceptsAListOfRoles` | covered |
| 24–25 | `Error: Invalid role format 123. Valid format are Array<string> or string` | `BoosterTokenVerifierTest#rejectsRolesThatAreNotStrings` | covered |
| 26 | other issuer rejected | `BoosterTokenVerifierTest#rejectsAnotherIssuer` | covered |
| 27 | `jwt expired` | `BoosterTokenVerifierTest#rejectsExpiredTokens` | covered |
| 28 | `jwt not active` | `BoosterTokenVerifierTest#rejectsTokensNotActiveYet` | covered |
| 29 | subclass adding checks after `super.verify` | `BoosterTokenVerifierTest#supportsVerifiersWithExtraChecks` | covered |
| 30 | subclass checking the header | `BoosterTokenVerifierTest#supportsVerifiersCheckingTheHeader` | covered |
| 31 | `getJwksClient` with cache, 15 min max age | `services.tokenverifiers.UtilitiesTest#getJwksClientCachesKeysFor15Minutes` | differs: JDK `JwksClient` instead of the `jwks-rsa` factory options |
| 32 | `JWT kid not found` | `UtilitiesTest#getKeyWithClientFailsWithoutKid` | covered |
| 33 | client error passed through | `UtilitiesTest#getKeyWithClientPassesTheClientErrorThrough` | covered |
| 34 | returns the public key | `UtilitiesTest#getKeyWithClientReturnsThePublicKey` | differs: returned instead of a callback |
| 35 | `Bearer ` stripped, RS256 + issuer + complete | `UtilitiesTest#verifyJWTRemovesTheBearerPrefixAndReturnsHeaderAndPayload`, `#verifyJWTOnlyAcceptsRS256WithRsaKeys`, `#verifyJWTChecksNotBeforeExpirationAndIssuerInThatOrder` | differs: JDK verifier instead of `jsonwebtoken` options (same checks) |
| 36 | the library error is rethrown | `UtilitiesTest#verifyJWTReportsMalformedTokens`, `#verifyJWTChecksTheSignature` | covered |
| 37 | `JwksUriTokenVerifier.verify` uses the JWKS key resolver | `services.tokenverifiers.JwksUriTokenVerifierTest#verifiesWithAKeyResolverBackedByTheJwksClient` | covered |
| 38 | `PublicKeyTokenVerifier.verify` uses the resolved key | `PublicKeyTokenVerifierTest#verifiesWithTheResolvedKey` | covered |
| 39 | `DEFAULT_ROLES_CLAIM === 'custom:role'` | `RoleBasedTokenVerifierTest#theDefaultRolesClaimIsCustomRole` | covered |
| 40 | `toUserEnvelope` with the default claim | `RoleBasedTokenVerifierTest#buildsTheUserEnvelopeWithTheDefaultClaim` | covered |
| 41 | configured roles claim | `RoleBasedTokenVerifierTest#usesAConfiguredRolesClaim` | covered |
| 42 | no handler → same error | `BoosterGlobalErrorDispatcherTest#returnsTheOriginalErrorWithoutAHandler` | covered |
| 43 | bare container → only `onError` | `BoosterGlobalErrorDispatcherTest#callsOnlyOnErrorForABareContainer` | covered |
| 44 | error thrown by a specific hook returned, `onError` skipped | `BoosterGlobalErrorDispatcherTest#returnsTheErrorThrownByASpecificHookWithoutCallingOnError` | covered |
| 45 | specific → `onError` chain | `BoosterGlobalErrorDispatcherTest#chainsTheSpecificHookAndOnError` | covered |
| 46–49 | command / event-handler / reducer / projection hooks | `BoosterGlobalErrorDispatcherTest#callsEverySpecificHookWithItsArguments` | covered |
| 50 | specific hook returning nothing swallows | `BoosterGlobalErrorDispatcherTest#swallowsTheErrorWhenTheSpecificHookReturnsNull` | covered |
| 51 | `onError` returning nothing swallows | `BoosterGlobalErrorDispatcherTest#swallowsTheErrorWhenOnErrorReturnsNull` | covered |
| 52–53 | `@Trace` keeps `this`; both handlers run | `instrumentation.TraceTest#keepsThisAndNotifiesStartAndEndAroundTheMethod` | covered |
| 54 | `boosterTriggerScheduledCommands` delegates | `BoosterUserAppTest#boosterTriggerScheduledCommandsAndItsDeprecatedAlias` | covered |
| 55 | `boosterRocketDispatcher` delegates | `BoosterUserAppTest#boosterRocketDispatcher` | covered |
| 56–57 | `loadTokenVerifierFromEnv` (same as spec 04 #11–12) | `BoosterTest#loadsTheTokenVerifierFromTheEnvironment`, `#doesNotLoadTheTokenVerifierWhenAVariableIsMissing` | covered |
| 58 | `@GlobalErrorHandler()` (same as spec 04 #23–24) | `decorators.GlobalErrorHandlerTest` | covered |

### Spec 06 — events

| # | Pinned behaviour | Java test | Status |
| --- | --- | --- | --- |
| 5.1.1 | `entityReducer` runs on the `EventStore` instance | `services.EventStoreTest#reducesThroughTheInstanceEntityReducer` | covered |
| 5.1.2 | snapshot without pending events returned unchanged, nothing reduced or stored | `EventStoreTest#returnsTheSnapshotWhenThereAreNoPendingEvents` | covered |
| 5.1.3 | two pending events chained, stored result returned | `EventStoreTest#reducesAShortListOfPendingEventsAndReturnsWhatStoreSnapshotReturns` | covered |
| 5.1.4 | six pending events chained from the snapshot | `EventStoreTest#reducesALongListOfPendingEventsStartingFromTheLoadedSnapshot` | covered |
| 5.1.5 | no snapshot → origin of time, `null` first | `EventStoreTest#reducesFromTheOriginOfTimeWhenThereIsNoSnapshot` | covered |
| 5.1.6 | no snapshot and no events → nothing | `EventStoreTest#returnsNothingWithoutSnapshotAndEvents` | covered |
| 5.1.7 | entity-migrated events chain; one final snapshot stored | `EventStoreTest#chainsTheReductionsThroughEntityMigratedEventsAndStoresOnlyTheFinalSnapshot` | covered |
| 5.1.8 | failing reducer halts the reduction, nothing stored | `EventStoreTest#haltsTheReductionWhenAReducerFailsAndStoresNothing` | covered |
| 5.1.9 | `storeSnapshot` retried 3 times, nothing thrown, `undefined` | `EventStoreTest#returnsNothingWithoutThrowingWhenStoringTheSnapshotFailsAfterEveryRetry` | covered |
| 5.1.10 | snapshot in the middle of the stream skipped | `EventStoreTest#ignoresASnapshotInTheMiddleOfTheStream` | covered |
| 5.1.11 | `storeSnapshot(snapshot, config)` | `EventStoreTest#storesTheSnapshotInTheEventStore` | covered |
| 5.1.12 | persist failure logged with `EventStore#storeSnapshot`, snapshot, `'\nError:'`, error | `EventStoreTest#logsTheErrorWhenTheSnapshotCannotBeStored` | covered |
| 5.1.13 | `latestEntitySnapshot(config, name, id)` | `EventStoreTest#loadLatestSnapshotLooksForTheLatestSnapshotStoredInTheEventStream` | covered |
| 5.1.14 | `forEntitySince(config, name, id, since)` | `EventStoreTest#loadEventStreamSinceLoadsTheEventStreamFromATimestamp` | covered |
| 5.1.15 | new snapshot shape with a snapshot | `EventStoreTest#entityReducerComputesTheNewSnapshotWithTheReducerOfTheEventGivenASnapshot` | covered |
| 5.1.16 | … without snapshot (`null` entity) | `EventStoreTest#entityReducerGeneratesTheSnapshotWithTheReducerOfTheEventGivenNoSnapshot` | covered |
| 5.1.17 | internal `BoosterEntityMigrated` snapshot | `EventStoreTest#entityReducerCalculatesTheNewInternalSnapshotOfAnInternalEvent` | covered |
| 5.1.18 | reducers receive event instances | `EventStoreTest#aReducerCanCallAnInstanceMethodOfTheEvent` | covered |
| 5.1.19 | reducers receive entity instances | `EventStoreTest#aReducerCanCallAnInstanceMethodOfTheEntity` | covered |
| 5.1.20 | `reducerForEvent` returns the static reducer | `EventStoreTest#reducerForEventReturnsTheReducerMethodOfTheEvent` | differs: a `Method` (no function identity in Java) |
| 5.2.1 | `snapshotAndUpdateReadModels(config, entity, id, stores)` | `BoosterEventProcessorTest#waitsForTheSnapshotGenerationAndReadModelUpdate` | covered |
| 5.2.2 | `dispatchEntityEventsToEventHandlers([event], config)` | `BoosterEventProcessorTest#waitsForTheEventToBeHandledByTheEventHandlers` | covered |
| 5.2.3 | notification topics are not projected | `BoosterEventProcessorTest#doesNotUpdateTheReadModelsWhenTheEntityNameIsANotificationTopic` | covered |
| 5.2.4 | `fetchEntitySnapshot(name, id)` | `BoosterEventProcessorTest#getsTheUpdatedStateOfTheEventEntity` | covered |
| 5.2.5 | `project(snapshot)` | `BoosterEventProcessorTest#projectsTheEntityStateToTheCorrespondingReadModels` | covered |
| 5.2.6 | reduction error logged, not projected | `BoosterEventProcessorTest#logsAndDoesNotThrowAReductionErrorAndDoesNotProject` | covered |
| 5.2.7 | no handlers → nothing | `BoosterEventProcessorTest#doesNothingWithoutEventHandlersAndGlobalHandlers` | covered |
| 5.2.8 | handler gets an event instance | `BoosterEventProcessorTest#callsTheHandlerOfTheEventWithAnEventInstance` | covered |
| 5.2.9 | all handlers of the event | `BoosterEventProcessorTest#callsAllTheHandlersOfTheEvent` | covered |
| 5.2.10 | notification handlers | `BoosterEventProcessorTest#callsAllTheHandlersOfANotification` | covered |
| 5.2.11 | one register per handler | `BoosterEventProcessorTest#storesTheEventsOfEveryHandlerWithItsOwnRegister` | covered |
| 5.2.12 | slow handlers awaited | `BoosterEventProcessorTest#waitsForSlowEventHandlersToFinish` | covered |
| 5.2.13 | a failing handler does not stop the batch; error logged | `BoosterEventProcessorTest#continuesProcessingTheOtherEventsWhenAnEventHandlerFails` | covered |
| 5.2.14 | `PromisesError` reasons logged | `BoosterEventProcessorTest#logsTheFailedReasonsOfAPromisesErrorThrownByAHandler` | covered |
| 5.2.15 | every handler of an event runs even if one fails | `BoosterEventProcessorTest#runsEveryHandlerOfAnEventEvenIfOneFails` | covered |
| 5.2.16 | handlers can call event instance methods | `BoosterEventProcessorTest#handlersReceiveEventInstancesWhoseMethodsCanBeCalled` | covered |
| 5.2.17 | already dispatched → skipped with warning | `BoosterEventProcessorTest#removesTheEventsThatHaveAlreadyBeenDispatched` | covered |
| 5.3.1 | `streamPerEntityEvents(config, envelopes, callback)` | `BoosterEventDispatcherTest#callsTheRawEventsParserOnceWithTheEnvelopesOfTheProvider` | covered |
| 5.3.2 | parser failure logged, never thrown | `BoosterEventDispatcherTest#logsAndIgnoresTheErrorsOfTheRawEventsParser` | covered |
| 5.4.1 | snapshots stripped | `services.RawEventsParserTest#stripsAllTheSnapshotsAndDoesNotCallTheCallbackForThem` | covered |
| 5.4.2 | one call per entity, order kept | `RawEventsParserTest#callsTheCallbackOncePerEntityWithItsEventsInOrder` | covered |
| 5.4.3 | one entity's failure logged, others processed | `RawEventsParserTest#keepsProcessingTheOtherEntitiesAndLogsWhenACallbackFails` | covered |
| 5.5.1 | `Could not find entity metadata for "NonExistingEntity"` | `BoosterEventsReaderTest#failsWhenTheEntityOfAByEntitySearchIsNotFound` | covered |
| 5.5.2 | `Could not find the entity associated to event type …` | `BoosterEventsReaderTest#failsWhenTheEntityAssociatedToAByTypeSearchIsNotFound` | covered |
| 5.5.3 | reducer entity not registered | `BoosterEventsReaderTest#failsWhenTheEntityThatReducesTheEventIsNotRegistered` | covered |
| 5.5.4 | neither entity nor type → `Invalid event search request` | `BoosterEventsReaderTest#failsWhenTheSearchIsNeitherByEntityNorByType` | covered |
| 5.5.5 | both entity and type → same | `BoosterEventsReaderTest#failsWhenTheSearchIsBothByEntityAndByType` | differs: typed parameters cannot hold both; checked on the raw-parameter path (`fetch(requestID, user, raw)`) |
| 5.5.6 | `Access denied` | `BoosterEventsReaderTest#failsWhenTheUserHasNoPermissions` | covered |
| 5.5.7 | provider `search(config, parameters)` result returned | `BoosterEventsReaderTest#callsTheProviderSearchWithTheParametersOfAByEntitySearch` | covered |
| 5.6.1–3 | `boosterEventDispatcher` / `boosterConsumeEventStream` / `boosterProduceEventStream` delegate with `(raw, config)` | `BoosterUserAppTest#boosterEventDispatcher`, `#boosterConsumeAndProduceEventStream` | covered |
| 5.7.1–5 | `Booster.events` / `Booster.entity` (same as spec 04 #6–10) | see spec 04 | covered |
| 5.8.1 | `BoosterEntityMigrated` wrapping (same as spec 05 #15) | `BoosterRegisterHandlerTest#wrapsInternalEventsEvenWhenAReducerIsRegistered` | covered |

### Spec 07 — read models

| # | Pinned behaviour | Java test | Status |
| --- | --- | --- | --- |
| 5.1.1 | no projections → nothing stored or fetched | `services.ReadModelStoreTest#returnsWithoutErrorsAndWithoutPerformingAnyActionsWhenTheEntityHasNoProjections` | covered |
| 5.1.2 | `Delete` → `delete` once per projection, no `store` | `ReadModelStoreTest#deletesTheAssociatedReadModelWhenTheProjectionReturnsDelete` | covered |
| 5.1.3 | `Nothing` → no write | `ReadModelStoreTest#ignoresTheReadModelWhenTheProjectionReturnsNothing` | covered |
| 5.1.4 | new read models: projection `(entity, null)`, `boosterMetadata` version 1, `lastUpdateAt`/`lastProjectionInfo` `1970-01-01T00:00:00.000Z`, expected version 0 | `ReadModelStoreTest#createsNewInstancesOfTheReadModelsWhenTheyDoNotExist` | covered |
| 5.1.5 | existing read models: version + 1, expected version = previous | `ReadModelStoreTest#updatesTheReadModelWhenItExists` | covered |
| 5.1.6 | projection gets an entity instance | `ReadModelStoreTest#theProjectionCanCallInstanceMethodsOfTheEntity` | covered |
| 5.1.7 | projection gets a read-model instance | `ReadModelStoreTest#theProjectionCanCallInstanceMethodsOfTheReadModel` | covered |
| 5.1.8 | optimistic concurrency failing 4 times → 5 `store` calls | `ReadModelStoreTest#retries5TimesWhenOptimisticConcurrencyFails4Times` | covered |
| 5.1.9 | array join key: existing + new read model | `ReadModelStoreTest#createsNonExistentReadModelsAndUpdatesExistingOnesFromAnArrayJoinKey` | covered |
| 5.1.10 | independent retries per id (1 + 5 calls) | `ReadModelStoreTest#retriesAreIndependentForEveryReadModelOfAnArrayJoinKey` | covered (the vacuous TypeScript argument checks, D12, are real assertions in Java) |
| 5.1.11 | sequence metadata passed to `applyProjectionToReadModel` | `ReadModelStoreTest#appliesTheProjectionsWithTheRightSequenceMetadata` | covered |
| 5.1.12 | `fetch(config, name, id, undefined)` → nothing | `ReadModelStoreTest#returnsNothingWhenTheReadModelDoesNotExist` | covered |
| 5.1.13 | `[undefined]` → nothing | `ReadModelStoreTest#returnsNothingWhenTheProviderReturnsAListWithNull` | covered |
| 5.1.14 | instance of the stored read model | `ReadModelStoreTest#returnsAnInstanceOfTheCurrentReadModelValueWhenItExists` | covered |
| 5.1.15 | sequence key passed to `fetch` | `ReadModelStoreTest#passesTheSequenceKeyToTheProvider` | covered |
| 5.1.16 | no sequence key → `undefined` | `ReadModelStoreTest#sequenceKeyForProjectionIsNullWithoutSequenceKey` | covered |
| 5.1.17 | `{ name: 'count', value: 123 }` | `ReadModelStoreTest#sequenceKeyForProjectionReadsTheValueFromTheEntity` | covered |
| 5.1.p | pending TypeScript cases: delete on `Delete`, nothing on `Nothing`, re-fetch with/without sequence key | `ReadModelStoreTest#deletesTheMigratedReadModelOnDelete`, `#doesNothingOnNothing`, `#refetchesTheReadModelWithoutSequenceKeyFromTheSecondTry`, `#refetchesTheReadModelWithTheSequenceKey` | covered |
| 5.2.1 | `"version" was not present` | `BoosterReadModelsReaderTest#throwsAnInvalidParameterErrorWhenTheVersionIsNotPresent` | covered |
| 5.2.2 | `Could not find read model` | `BoosterReadModelsReaderTest#throwsANotFoundErrorWhenItCannotFindTheReadModelMetadata` | covered |
| 5.2.3 | `Access denied` | `BoosterReadModelsReaderTest#throwsANotAuthorizedErrorWhenTheCurrentUserIsNotAllowed` | covered |
| 5.2.4 | `Could not find a sort key` | `BoosterReadModelsReaderTest#throwsAnInvalidParameterErrorWhenTheSequenceKeyIsNotDefinedForTheReadModel` | covered |
| 5.2.5 | no key accepted | `BoosterReadModelsReaderTest#acceptsARequestWithoutSequenceKey` | covered |
| 5.2.6 | valid sequence key accepted | `BoosterReadModelsReaderTest#acceptsAValidSequenceKey` | covered |
| 5.2.7 | `findById` validates and uses the searcher | `BoosterReadModelsReaderTest#validatesAndUsesTheSearcherToFindAReadModelById` | covered |
| 5.2.8 | one instance → `migrate` once | `BoosterReadModelsReaderTest#callsMigrateAfterFindingAReadModelById` | covered |
| 5.2.9 | array of two → `migrate` once | `BoosterReadModelsReaderTest#migratesEveryElementWhenItGotAnArray`, `#migratesOnceForASingleElementArray` | differs (D1): every element of a sequenced result is migrated and returned |
| 5.2.10 | search/subscribe without version → `InvalidParameterError` | `BoosterReadModelsReaderTest#throwsTheRightErrorWhenTheRequestIsMissingVersion` | covered |
| 5.2.11 | unknown read model → `NotFoundError` | `BoosterReadModelsReaderTest#throwsTheRightErrorWhenTheReadModelDoesNotExist` | covered |
| 5.2.12 | wrong roles → `NotAuthorizedError` | `BoosterReadModelsReaderTest#throwsTheRightErrorWhenTheUserIsNotAuthorized` | covered |
| 5.2.13 | provider `search(any, name, filters, {}, undefined, undefined, false, undefined)` | `BoosterReadModelsReaderTest#callsTheProviderSearchFunctionAndReturnsItsResults` | covered |
| 5.2.14 | `migrate` per item | same test | covered |
| 5.2.15 | paginated: `true`, list result, `migrate` per item | `BoosterReadModelsReaderTest#callsMigrateForEveryItemOfAPaginatedSearch` | covered |
| 5.2.16–17 | `select` passed, no migration | `BoosterReadModelsReaderTest#passesTheSelectionAndDoesNotMigrate` | covered |
| 5.2.18 | one hook `(request, currentUser)` | `BoosterReadModelsReaderTest#callsTheBeforeHookFunction` | covered |
| 5.2.19 | hooks chained | `BoosterReadModelsReaderTest#chainsTheBeforeHookFunctions` | covered |
| 5.2.20 | stored subscription `{...request, connectionID, operation, expirationTime}` | `BoosterReadModelsReaderTest#callsTheProviderSubscribeFunction` | covered |
| 5.2.21 | hook filters stored | `BoosterReadModelsReaderTest#storesTheFiltersProducedByTheBeforeHooks` | covered |
| 5.2.22 | `deleteSubscription(any, connectionID, subscriptionID)` | `BoosterReadModelsReaderTest#unsubscribeCallsTheProviderDeleteSubscription` | covered |
| 5.2.23 | `deleteAllSubscriptions(any, connectionID)` | `BoosterReadModelsReaderTest#unsubscribeAllCallsTheProviderDeleteAllSubscriptions` | covered |
| 5.3.24 | `boosterNotifySubscribers(raw)` delegates | `BoosterUserAppTest#boosterNotifySubscribers` | covered |
| 5.3.25 | subscription resolver: `Missing "connectionID"…`, `subscribe` only with `storeSubscriptions`, `asyncIterator(envelope, config)` once | `services.graphql.GraphQLGeneratorTest#throwsWithoutConnectionID`, `#storesTheSubscriptionWhenStoreSubscriptionsIsSet`, `#doesNotStoreTheSubscriptionOtherwise`, `#callsThePubSubWithTheReadModelRequest` | covered |
| 5.3.26 | client contexts carry `NoopReadModelPubSub` and `storeSubscriptions: true` | `BoosterGraphQLDispatcherTest#runsTheOperationWithTheResolverContext` | covered |

### Spec 08 — GraphQL

| # | Pinned behaviour | Java test | Status |
| --- | --- | --- | --- |
| d1 | introspection result with `{}` headers | `BoosterGraphQLDispatcherTest#isEnabledWithTheDefaultConfig` | covered |
| d2 | `Instrospection queries are disabled…` | `BoosterGraphQLDispatcherTest#canBeDisabled` | covered |
| d3 | CONNECT → `(null, {Sec-WebSocket-Protocol: graphql-ws})` | `BoosterGraphQLDispatcherTest#callsHandleResultWithTheGraphQLWebsocketSubprotocolHeaders` | covered |
| d4 | DISCONNECT without id deletes nothing, `handleResult()` | `BoosterGraphQLDispatcherTest#doesNotDeleteConnectionOrSubscriptionDataWhenThereIsNoConnectionID` | covered |
| d5 | DISCONNECT deletes the data then the subscriptions | `BoosterGraphQLDispatcherTest#deletesTheConnectionAndThenItsSubscriptions` | covered |
| d6 | socket MESSAGE goes to the websocket handler | `BoosterGraphQLDispatcherTest#callsTheWebsocketHandlerWithTheEnvelopeAndMarksTheResponse` | covered |
| d7 | HTTP MESSAGE does not | `BoosterGraphQLDispatcherTest#doesNotCallTheWebsocketHandler` | covered |
| d8 | envelope error reported | `BoosterGraphQLDispatcherTest#reportsTheErrorOfTheEnvelope` | covered |
| d9 | `Received an empty GraphQL body` | `BoosterGraphQLDispatcherTest#reportsAnEmptyBody` | covered |
| d10 | `Received an empty GraphQL query` | `BoosterGraphQLDispatcherTest#reportsAnEmptyQuery` | covered |
| d11 | `This API and protocol does not support "subscription" operations` | `BoosterGraphQLDispatcherTest#rejectsSubscriptionOperations` | covered |
| d12 | execution arguments and resolver context | `BoosterGraphQLDispatcherTest#runsTheOperationWithTheResolverContext` | covered |
| d13 | response headers added by resolvers reach `handleResult` | `BoosterGraphQLDispatcherTest#passesTheResponseHeadersTheHandlersAdd` | covered |
| d14 | token verified, user in the context | `BoosterGraphQLDispatcherTest#verifiesTheTokenAndRunsTheOperationAsTheUser` | covered |
| d15–16 | query / mutation errors returned | `BoosterGraphQLDispatcherTest#returnsTheErrorsOfAQueryWithTheirExtensions`, `#returnsTheErrorsOfAMutationWithTheirExtensions` | covered |
| w1 | no connection id → only `Missing websocket connectionID` logged | `GraphQLWebsocketHandlerTest#justLogsAnErrorWithAnEnvelopeWithNoConnectionID` | covered |
| w2 | envelope error → `connection_error` | `GraphQLWebsocketHandlerTest#sendsTheErrorOfAnErrorEnvelopeToTheClient` | covered |
| w3 | empty value → `connection_error` `Received an empty GraphQL body` | `GraphQLWebsocketHandlerTest#sendsTheRightErrorWithAnEmptyValue` | covered |
| w4 | `connection_init` → `connection_ack` | `GraphQLWebsocketHandlerTest#sendsBackAConnectionAck` | covered |
| w5 | connection data `{user: undefined, expirationTime}` | `GraphQLWebsocketHandlerTest#storesConnectionDataBeforeTheAck` | covered |
| w6 | connection data with the verified user | `GraphQLWebsocketHandlerTest#storesConnectionDataIncludingTheUserWithAnAccessToken` | covered |
| w7 | `Missing "id" in start message` | `GraphQLWebsocketHandlerTest#failsIfThereIsNoId` (start) | covered |
| w8–9 | `error` message `Message payload is invalid it must contain at least the "query" property` | `GraphQLWebsocketHandlerTest#failsIfThereIsNoPayload`, `#failsIfThereIsNoQuery` | differs: each error is written `{message}` (JavaScript `JSON.stringify(new Error())` sends `{}`), a foundation decision of `GraphQLErrorMessage` |
| w10 | `onStartOperation({...envelope, currentUser, value: {...payload, id}})` | `GraphQLWebsocketHandlerTest#callsOnStartOperationWithTheRightParameters` | covered |
| w11 | subscription result → nothing sent | `GraphQLWebsocketHandlerTest#doesNotSendAnythingBackForTheResultOfASubscription` | covered |
| w12 | query result → `data` then `complete` | `GraphQLWebsocketHandlerTest#sendsBackTheDataAndCompleteMessagesForAQueryOrMutation` | covered |
| w13 | `Missing "id" in stop message` | `GraphQLWebsocketHandlerTest#failsIfThereIsNoId` (stop) | covered |
| w14 | `onStopOperation(connectionID, id)` | `GraphQLWebsocketHandlerTest#callsOnStopOperationWithTheRightParameters` | covered |
| w15 | `complete` after stop | `GraphQLWebsocketHandlerTest#sendsBackACompleteMessage` | covered |
| w16 | `connection_terminate` → `onTerminate`, nothing sent | `GraphQLWebsocketHandlerTest#callsOnTerminateAndSendsNothingBackForAConnectionTerminateMessage` | covered |
| g1–3 | each root generator called once | `GraphQLGeneratorTest#callsTheQueryGenerator`, `#callsTheMutationGenerator`, `#callsTheSubscriptionGenerator` | covered |
| g4 | schema root types are the generated ones | `GraphQLGeneratorTest#returnsAGraphQLSchemaWithTheGeneratedTypes` | covered |
| g5 | list resolver envelope | `GraphQLGeneratorTest#callsSearchWithTheExpectedPayload` | covered |
| g6 | returns the search result | `GraphQLGeneratorTest#returnsTheSearchResult` | covered |
| g7 | by-id envelope without sequence key | `GraphQLGeneratorTest#buildsAFunctionThatPerformsRequestsById` | covered |
| g8 | by-id envelope with sequence key | `GraphQLGeneratorTest#buildsAFunctionThatPerformsRequestsByIdAndSequenceKey` | covered |
| g9 | command envelope (`requestID`, user, `typeName`, `value`, `version: 1`, context) | `GraphQLGeneratorTest#callsDispatchCommandWithTheExpectedInput` | covered |
| g10 | `true` when the command returns nothing | `GraphQLGeneratorTest#returnsTrueWhenTheCommandReturnsNothing` | covered |
| g11–12 | by-id subscription delegates and returns | `GraphQLGeneratorTest#callsSubscriptionResolverBuilderAndReturnsItsResult` | covered |
| g13 | `Missing "connectionID". It is required for subscriptions` | `GraphQLGeneratorTest#throwsWithoutConnectionID` | covered |
| g14–15 | `subscribe` only with `storeSubscriptions` | `GraphQLGeneratorTest#storesTheSubscriptionWhenStoreSubscriptionsIsSet`, `#doesNotStoreTheSubscriptionOtherwise` | covered |
| g16 | `asyncIterator(envelope, config)` | `GraphQLGeneratorTest#callsThePubSubWithTheReadModelRequest` | covered |
| g17 | returns the iterator | `GraphQLGeneratorTest#returnsThePubSubIteratorAsAPublisher` | differs: wrapped in a `Flow.Publisher` for graphql-java |
| g18–19 | event resolver envelope and result | `GraphQLGeneratorTest#callsFetchWithTheExpectedPayload`, `#returnsTheFetchedEvents` | covered |
| q1 | type informer called 3 times | `GraphQLQueryGeneratorTest#callsTheTypeInformerThriceWithTheReadModelClass` | covered |
| q2 | by-id resolver after the informer | `GraphQLQueryGeneratorTest#callsTheByIdResolverBuilderOnceAfterTheTypeInformer` | covered |
| q3 | `Query`, `<Name>`, `<Name>s` deprecated `[T!]!` | `GraphQLQueryGeneratorTest#returnsTheExpectedResult` | covered |
| q4–5 | same with one property | `GraphQLQueryGeneratorTest#callsTheTypeInformerThriceForAReadModelWithAScalarProperty`, `#callsTheByIdResolverBuilderOnceAfterTheTypeInformer` | covered |
| q6 | `BooleanPropertyFilter {eq, ne, isDefined}`, `<Name>SortBy` with `orderProperty` | `GraphQLQueryGeneratorTest#hasTheExpectedArgumentsForABooleanProperty` | covered |
| q7 | `NumberPropertyFilter` | `GraphQLQueryGeneratorTest#hasTheExpectedArgumentsForANumberProperty` | covered |
| q8 | `StringPropertyFilter` | `GraphQLQueryGeneratorTest#hasTheExpectedArgumentsForAStringProperty` | covered |
| q9 | `eventsByEntity` / `eventsByType` and `EventQueryResponse` | `GraphQLQueryGeneratorTest#returnsTheExpectedQueries`, `#searchesEventsByEntity`, `#searchesEventsByType` | covered |
| q10 | no read models → informer not called | `GraphQLQueryGeneratorTest#doesNotCallTheTypeInformer` | covered |
| q11 | only the event queries | `GraphQLQueryGeneratorTest#returnsOnlyTheEventQueries` | covered |
| q12 | (pending in TypeScript) plural, `List*`, events per entity | `GraphQLSchemaSdlTest#generatesTheSchemaOfAnApplication` | covered |
| k1 | by-id and sequenced queries generated | `GraphqlQueryByKeysGeneratorTest#generatesByIdAndSequencedQueries` | covered |
| k2 | by-id query `id: ID!` | `GraphqlQueryByKeysGeneratorTest#generatesAQueryNamedAfterTheReadModelThatAcceptsAUniqueId` | covered |
| k3 | sequenced query `[T]` with `id` and the key | `GraphqlQueryByKeysGeneratorTest#generatesAQueryNamedAfterTheReadModelThatAcceptsAnIdAndASequenceKey` | covered |
| t1 | object type named after the class | `GraphQLTypeInformerTest#returnsTheExpectedGraphQLType` | covered |
| t2 | nested arrays `[[JSON!]!]!` | `GraphQLTypeInformerTest#processesNestedLists` | covered |
| t3 | promises unwrapped | `GraphQLTypeInformerTest#processesPromises` | covered |
| t4 | excluded properties | `GraphQLTypeInformerTest#ignoresNonExposedProperties`, `#honoursTheExcludedPropertiesOnlyForTheGivenClass` | covered |
| t5 | `UUID` → `ID!` | `GraphQLTypeInformerTest#mapsUUIDToANonNullID` | covered |
| t6 | `Date` → `Date!` | `GraphQLTypeInformerTest#mapsDateToANonNullDateScalar` | covered |
| t7–9 | string / number / boolean | `GraphQLTypeInformerTest#mapsStringsNumbersAndBooleans` | covered |
| t10 | enums | `GraphQLTypeInformerTest#mapsEnumsWithTheirNamesAsValues` | covered |
| t11 | arrays | `GraphQLTypeInformerTest#mapsArrays` | covered |
| t12 | objects → `JSON!` | `GraphQLTypeInformerTest#mapsObjectsToJson` | covered |
| t13 | other groups → `JSON!` | `GraphQLTypeInformerTest#mapsEveryOtherGroupToJson` | covered |
| i | integration naming (`ListXFilter`, `XSortBy`, `XInput`, `StringArrayPropertyFilter`, nested class filters, generic `X_arg_PropertyFilter`, `JSON` for unions/maps, `orderProperty` except class sorts, non-exposed members absent, declaration order then `and`/`or`/`not`) | `GraphQLSchemaSdlTest#generatesTheSchemaOfAnApplication`, `integration.GraphQLTypesIntegrationTest#theSchemaExposesTheApplication` | covered |

### Spec 09 — migrations and health

| # | Pinned behaviour | Java test | Status |
| --- | --- | --- | --- |
| 5.1.1 | `Received an invalid schema version value, 0, for TestConcept` | `SchemaMigratorTest#rejectsVersionsLowerThan1` | covered |
| 5.1.2 | `The current schema version of TestConcept is 3, which is lower than the received version 4` | `SchemaMigratorTest#rejectsVersionsNewerThanTheCurrentOne` | covered |
| 5.1.3 | current version → same object | `SchemaMigratorTest#returnsTheSameEnvelopeWhenItIsCurrent` | covered |
| 5.1.4 | v1 → v3 new envelope, value of the last schema class | `SchemaMigratorTest#migratesStepByStepIntoANewEnvelope` | covered |
| 5.2.1–3 | read model version checks and same object | `ReadModelSchemaMigratorTest#throwsWhenTheSchemaVersionIsLowerThan1`, `#throwsWhenTheSchemaVersionIsHigherThanTheCurrentOne`, `#doesNotMigrateWhenTheSchemaVersionIsTheCurrentOne` | covered |
| 5.2.4 | migrated read model keeps `version`, bumps `schemaVersion` | `ReadModelSchemaMigratorTest#migratesWhenTheSchemaVersionIsLowerThanTheCurrentOne` | covered |
| 5.2.5 | `migrateAll` with no items → 0 | `ReadModelSchemaMigratorTest#returns0WhenThereAreNoReadModelsToMigrate` | covered |
| 5.2.6 | three items, short page ends the loop → 3 | `ReadModelSchemaMigratorTest#migratesTheReadModelsWithAnOldSchemaVersionAndAShortPageEndsTheLoop` | covered |
| 5.3.1–5 | `@ToVersion` / `@SchemaMigration` (same as spec 04 #25–29) | `decorators.MigrationTest` | covered |
| 5.4.1–6 | `isEnabled` / `showChildren` inheritance | `sensor.health.HealthUtilsTest#allEnabled`, `#aDisabledLeafOnlyDisablesItself`, `#aDisabledParentDisablesItsChildren`, `#allShowChildren`, `#aLeafWithoutShowChildrenOnlyAffectsItself`, `#aParentWithoutShowChildrenHidesTheChildrenOfItsDescendants` | covered |
| 5.4.7 | `Unexpected HealthProvider id ` | `HealthUtilsTest#metadataFromId` | covered |
| 5.4.8–10 | `parentId`, `rootHealthProviders`, `childHealthProviders` (order) | `HealthUtilsTest#parentId`, `#rootHealthProviders`, `#childHealthProviders` | covered |
| 5.5.1 | no rockets → `UNKNOWN` `No Rockets found` | `RocketsHealthIndicatorTest#returnsUnknownWhenThereAreNoRockets` | covered |
| 5.5.2–4 | `UP` / `DOWN` / `PARTIALLY_UP` with components in key order | `RocketsHealthIndicatorTest#returnsUpWhenAllRocketsAreUp`, `#returnsDownWhenAllRocketsAreDown`, `#returnsPartiallyUpWhenSomeRocketsAreDown` | covered |
| 5.5.5–6 | one rocket `UP` / `DOWN` | `RocketsHealthIndicatorTest#returnsTheStatusOfASpecificRocketThatIsUp`, `#returnsTheStatusOfASpecificRocketThatIsDown` | covered |
| 5.5.7 | `Rocket "rocket1-func" not found` | `RocketsHealthIndicatorTest#failsWhenTheRequestedRocketDoesNotExist` | covered |
| 5.6.1 | all `UP` tree and details | `sensor.health.BoosterHealthServiceTest#allIndicatorsAreUp` | covered |
| 5.6.2 | all `DOWN` | `BoosterHealthServiceTest#allIndicatorsAreDown` | covered |
| 5.6.3 | provider details passed | `BoosterHealthServiceTest#detailsAreProcessed` | covered |
| 5.6.4 | role-protected request succeeds | `BoosterHealthServiceTest#validatesWithTheExpectedRole` | covered |
| 5.6.5 | wrong role → `NotAuthorizedError` | `BoosterHealthServiceTest#validationFailsWithTheWrongRole` | covered |
| 5.6.6 | only root, no children or details | `BoosterHealthServiceTest#onlyRootEnabledAndWithoutChildrenAndDetails` | covered |
| 5.6.7 | disabled parent → empty | `BoosterHealthServiceTest#ifTheParentIsDisabledTheChildrenAreDisabled` | covered |
| 5.6.8 | only read models | `BoosterHealthServiceTest#onlyReadModelsEnabledAndWithoutChildrenAndDetails` | covered |
| 5.6.9–13 | `isHealthy` rules (rockets `UNKNOWN` healthy) | `BoosterHealthServiceTest#isHealthyWhenAllComponentsAreUp`, `#isNotHealthyWhenAnyComponentIsDown`, `#isHealthyWhenRocketsAreUnknown`, `#isNotHealthyWhenRocketsAreDown`, `#isHealthyWhenRocketsAreUp` | covered (without the TypeScript flag leak between tests) |
| 5.7 | `boosterHealth(raw)` delegates | `BoosterUserAppTest#boosterHealth` | covered |

---

## Part 2 — Documentation checklist (spec 18), core-relevant items

Items about the CLI (§3.P, generators F009–F015, F109, F163, F393), the Azure/local infrastructure (F294, F298–F302,
F333), rockets packages (§3.X, §3.Y, F360–F365) and contributing/testing conventions (F394–F399) are owned by other
modules and are not listed. "Integration" refers to `integration.*IntegrationTest`.

### Commands, events, handlers, entities (§3.B–§3.E)

| ID | Feature | Java test | Status |
| --- | --- | --- | --- |
| F001–F004 | CQRS/ES model; API inferred from code; append-only store; asynchronous pipeline | Integration `CommandToReadModelIntegrationTest#storesTheEventsTheirSnapshotsAndTheReadModelDocument` | covered |
| F016 | first-command walkthrough (`true`, plural query, by id) | Integration `CommandToReadModelIntegrationTest#aMutationIsProjectedIntoTheReadModel`, `ReadModelQueriesIntegrationTest#findsAReadModelById`, `#filtersWithTheDeprecatedPluralQuery` | covered |
| F020 | `@Command` fields are the input fields | `services.graphql.queryhelpers.GraphQLQueryHelpersTest#addsTheInputArgumentOnlyWhenTheClassHasProperties` | covered |
| F021 | static `handle(command, register)` | `decorators.CommandTest#failsWhenTheCommandHasNoStaticHandle`, `BoosterCommandDispatcherTest#callsTheHandlerWithAnInstanceAndReturnsItsResult` | covered |
| F022 | return value is the mutation result; `void` → `true` | `BoosterGraphQLDispatcherTest#returnsTrueForCommandsThatReturnNothing`, `#returnsWhatTheCommandReturns` | differs: a `void` handler is typed `JSON!` as the current TypeScript metadata produces (`void` is not `never`); the docs say `Boolean` |
| F023 | GraphQL validates types before the handler | Integration `ErrorHandlingIntegrationTest#aCommandWithAMissingInputIsAValidationError` | covered |
| F024 | handler error → `{message, path}` without stack trace | Integration `ErrorHandlingIntegrationTest#theGlobalErrorHandlerRewritesACommandFailure`, `BoosterGraphQLDispatcherTest#returnsTheErrorsOfAMutationWithTheirExtensions` | covered (graphql-js also writes `locations` and `extensions`) |
| F025 | error events are ordinary events | — | n/a |
| F026 | commands denied by default | `decorators.CommandTest#setsTheDenyAuthorizerByDefault`, `BoosterAuthorizerTest#denyAccessDenies` | covered |
| F027 | `Name(input: NameInput!)`, scalar mapping | `GraphQLSchemaSdlTest#generatesTheSchemaOfAnApplication` | covered |
| F028 | `Booster.entity` from handlers | `BoosterTest#entityBuildsAnInstanceFromTheSnapshot`, Integration `CommandToReadModelIntegrationTest#theEntityAndTheReadModelCanBeReadFromCode` | differs: returns `Optional` |
| F029 | command `before` hooks: chained, may throw | `BoosterCommandDispatcherTest#chainsTheBeforeHooksInOrder`, Integration `CommandToReadModelIntegrationTest#theBeforeHookOfTheCommandValidatesAndRewritesTheInput` | covered |
| F030 | `@NonExposed` removed from the input | `GraphQLQueryHelpersTest#leavesTheNonExposedPropertiesOutOfTheInput` | covered |
| F031 | naming recommendation | — | n/a |
| F032 | `register.currentUser` in command handlers | `BoosterCommandDispatcherTest#storesTheRegisteredEventsWithTheRequestAndUser` | covered |
| F040 | `@Event`, class name = type name / `EventType` value | `decorators.EventTest#addsTheEventClassAsAnEvent`, `GraphQLQueryGeneratorTest#searchesEventsByType` | covered |
| F041 | `entityID()` required | `BoosterRegisterHandlerTest#failsWhenTheEntityIDIsMissingOrEmpty` | covered |
| F042 | one event store collection | — | elsewhere: providers |
| F043 | register in command and event handlers, not queries | `BoosterEventProcessorTest#storesTheEventsOfEveryHandlerWithItsOwnRegister`, `BoosterQueryDispatcherTest#runsTheHooksAndPassesTheQueryInfo` | covered |
| F044 | `register.events(...)` varargs, chainable | `concepts.ConceptsTest` (framework-types) | elsewhere: framework-types |
| F045–F046 | naming / validate before registering | — | n/a |
| F050 | `@EventHandler` static `handle(event, register)` | `decorators.EventHandlerTest#failsWithoutAStaticHandle`, `BoosterEventProcessorTest#callsTheHandlerOfTheEventWithAnEventInstance` | covered |
| F051 | handlers register events (chaining) | Integration `CommandToReadModelIntegrationTest#eventHandlersAndTheGlobalEventHandlerSeeEveryEvent` | covered |
| F052 | handlers read entities | Integration `EventsIntegrationTest#aScheduledCommandReadsReadModelsAndRegistersEvents`, `BoosterTest#entityBuildsAnInstanceFromTheSnapshot` | covered |
| F053 | handlers of notifications | `BoosterEventProcessorTest#callsAllTheHandlersOfANotification`, Integration `EventsIntegrationTest#notificationsAreDispatchedToTheirHandlersWithoutSnapshots` | covered |
| F054 | `@GlobalEventHandler` for every event and notification, with instances | `BoosterEventProcessorTest#appendsTheGlobalEventHandlersToTheHandlersOfEveryEvent`, Integration `EventsIntegrationTest#notificationsAreDispatchedToTheirHandlersWithoutSnapshots` | covered |
| F055 | `Booster.readModel` from handlers | Integration `EventsIntegrationTest#aScheduledCommandReadsReadModelsAndRegistersEvents` | covered |
| F060 | `@Entity` / `@Entity(authorizeReadEvents)` | `decorators.EntityTest#addsTheEntityAndItsReducers` | covered |
| F061 | static reducers, `null` current state, one reducer per event | `EventStoreTest#reducesFromTheOriginOfTimeWhenThereIsNoSnapshot`, `decorators.EntityTest#failsWhenAnEventIsReducedTwice` | covered |
| F062 | pure reducers | — | n/a |
| F063 | per-entity events reduced strictly in order | `EventStoreTest#reducesALongListOfPendingEventsStartingFromTheLoadedSnapshot`, `RawEventsParserTest#callsTheCallbackOncePerEntityWithItsEventsInOrder` | covered |
| F064 | snapshots | `EventStoreTest#reducesAShortListOfPendingEventsAndReturnsWhatStoreSnapshotReturns` | covered |
| F065 | entities generated lazily | `EventStoreTest#returnsTheSnapshotWhenThereAreNoPendingEvents` | covered |
| F066 | `id` field, `UUID` | — | n/a |
| F067 | entities not in the GraphQL schema | `GraphQLSchemaSdlTest#generatesTheSchemaOfAnApplication` | covered |

### Read models, notifications, queries, event API, schedules (§3.F–§3.J)

| ID | Feature | Java test | Status |
| --- | --- | --- | --- |
| F070 | `@ReadModel` class = GraphQL type | `GraphQLSchemaSdlTest#generatesTheSchemaOfAnApplication` | covered |
| F071 | read models denied by default | `decorators.ReadModelTest#registersTheReadModelWithItsProperties`, `BoosterReadModelsReaderTest#throwsANotAuthorizedErrorWhenTheCurrentUserIsNotAllowed` | covered |
| F072 | `@NonExposed` read-model property hidden | `GraphqlQueryByKeysGeneratorTest#leavesOutTheNonExposedPropertiesOfTheReadModel`, Integration `ReadModelQueriesIntegrationTest#nonExposedPropertiesAreStoredButNotPartOfTheSchema` | covered |
| F073 | `@Projects(Entity, joinKey)` with `(entity, current)` | `ReadModelStoreTest#createsNewInstancesOfTheReadModelsWhenTheyDoNotExist` | covered |
| F074 | several `@Projects` | `decorators.ReadModelTest#registersProjections` | covered |
| F075 | array join key `(entity, readModelID, current)` | `ReadModelStoreTest#createsNonExistentReadModelsAndUpdatesExistingOnesFromAnArrayJoinKey` | covered |
| F076 | join-key function; no match → called once without id | `ReadModelStoreTest#searchesWithTheFilterOfTheFunctionAndProjectsEveryMatch`, `#projectsOnceWithoutIdNorReadModelWhenNothingMatches` | covered |
| F077 | `Delete` / `Nothing` (no write, so no notification) | `ReadModelStoreTest#deletesTheAssociatedReadModelWhenTheProjectionReturnsDelete`, `#ignoresTheReadModelWhenTheProjectionReturnsNothing` | covered |
| F078 | calculated fields, possibly asynchronous, re-executed per query | `GraphQLExecutionTest#waitsForAsynchronousCalculatedFieldsAndTypesThemWithTheirResult`, `#readsTheValuesOfReadModelInstances` | fixed `1a562c579` (asynchronous getters were untested) |
| F079 | `@CalculatedField(dependsOn)` loads the dependencies under `select` | `BoosterReadModelsReaderTest#addsTheDependenciesOfTheSelectedCalculatedFieldsAndReturnsOnlyTheSelection`, Integration `ReadModelQueriesIntegrationTest#selectsCalculatedFieldsTogetherWithTheirDependencies` | covered |
| F080 | `X(id)`, `Xs(filter)`, `ListXs(...)`, subscriptions `X(id)` / `Xs(filter)` | `GraphQLSchemaSdlTest#generatesTheSchemaOfAnApplication`, `GraphQLRootTypeGeneratorsTest#generatesTheByIdAndFilteredSubscriptionsOfEachReadModel` | covered |
| F081 | sequenced by-id query `X(id: ID!, key: ID): [X]` | `GraphqlQueryByKeysGeneratorTest#generatesAQueryNamedAfterTheReadModelThatAcceptsAnIdAndASequenceKey`, Integration `SequencedReadModelIntegrationTest#everyReviewIsARowOfTheSameId`, `#theSequenceKeySelectsOneRow` | covered |
| F082 | `TimeKey.generate()` | — | elsewhere: framework-types |
| F083–F084 | eventual consistency, naming | — | n/a |
| F090 | `@Notification` without `entityID()` | `BoosterRegisterHandlerTest#wrapsNotificationsWithTheDefaults` | covered |
| F091 | default topic `defaultTopic` | `BoosterRegisterHandlerTest#wrapsNotificationsWithTheDefaults` | covered |
| F092 | `@partitionKey`, default `default` | `BoosterRegisterHandlerTest#usesThePartitionIdOrThePartitionKeyAndTopicOfNotifications` | covered |
| F093 | notifications reach handlers and the global handler | Integration `EventsIntegrationTest#notificationsAreDispatchedToTheirHandlersWithoutSnapshots` | covered |
| F100 | `@Query` with static `handle(query, queryInfo)` | `decorators.QueryTest#registersTheQuery`, `BoosterQueryDispatcherTest#runsTheHooksAndPassesTheQueryInfo` | covered |
| F101 | `QueryInfo` user, context, response headers | `BoosterQueryDispatcherTest#runsTheHooksAndPassesTheQueryInfo` | covered |
| F102 | query errors returned like commands | `BoosterQueryDispatcherTest#routesErrorsToTheGlobalErrorHandler` | covered |
| F103 | `@NonExposed` query params set by a hook | Integration `ReadModelQueriesIntegrationTest#aQueryBeforeHookSetsANonExposedField` | covered |
| F104 | queries cannot register events | `BoosterQueryDispatcherTest#runsTheHooksAndPassesTheQueryInfo` (no register is passed) | covered |
| F105 | query hooks chained; static methods of the query as hooks | `services.FilterHelpersTest#chainsQueryAndReadModelHooks` | differs: hooks are classes implementing the hook interface (annotations cannot reference methods) |
| F106 | one query field per `@Query`, `input:` wrapper, non-null result | `GraphQLSchemaSdlTest#generatesTheSchemaOfAnApplication`, `GraphQLQueryHelpersTest#rejectsQueriesWithoutHandle` | covered |
| F107 | no query subscriptions | `GraphQLRootTypeGeneratorsTest#generatesTheByIdAndFilteredSubscriptionsOfEachReadModel` | covered |
| F108 | union results with `__typename` fragments | Integration `GraphQLTypesIntegrationTest#aQueryReturnsAListOfAUnion` | differs: a union is a sealed type of records/classes (no TypeScript union aliases) |
| F150 | event API disabled by default, per-entity authorizer | `BoosterEventsReaderTest#anEntityWithoutAuthorizerCannotBeRead`, `decorators.EntityTest#addsTheEntityAndItsReducers` | covered |
| F151 | `eventsByEntity` / `eventsByType` with enum arguments | `GraphQLQueryGeneratorTest#searchesEventsByEntity`, `#searchesEventsByType` | covered |
| F152 | result fields | Integration `EventsIntegrationTest#eventsByEntityReturnsTheEventsNewestFirstWithTheirUserAndValue` | covered |
| F153 | `from` / `to` compared as strings | `BoosterEventsReaderTest#callsTheProviderSearchWithTheParametersOfAByEntitySearch` (passed through unchanged) | elsewhere: providers compare |
| F154 | no event subscriptions; read-only | `GraphQLRootTypeGeneratorsTest#generatesTheByIdAndFilteredSubscriptionsOfEachReadModel` | covered |
| F160 | `@ScheduledCommand` static `handle` (no parameters in the docs; the code passes the register) | `BoosterScheduledCommandDispatcherTest#runsTheHandlerWithoutUserAndStoresTheEvents`, `internal.UserCodeTest#callsMethodsDeclaringFewerParametersThanTheArguments` | covered |
| F161–F162 | cron fields; empty schedule never triggered | `decorators.ScheduledCommandTest#registersTheScheduleWithAbsentFields` | elsewhere: provider infrastructure builds and skips the cron |

### Logging, error hooks, authentication, authorization (§3.K–§3.N)

| ID | Feature | Java test | Status |
| --- | --- | --- | --- |
| F170–F174 | default logger, `config.logger`, `logLevel`, `logPrefix`, `[prefix]\|module: ` format | `LoggerTest` (framework-common-helpers) | elsewhere: foundation |
| F180 | errors caught and logged by default | `BoosterEventDispatcherTest#logsAndIgnoresTheErrorsOfTheRawEventsParser` | covered |
| F181 | hooks: `null` swallows, an error replaces | `BoosterGlobalErrorDispatcherTest#swallowsTheErrorWhenTheSpecificHookReturnsNull`, `#chainsTheSpecificHookAndOnError` | covered |
| F182 | `onCommandHandlerError(error, envelope, metadata)` | `BoosterGlobalErrorDispatcherTest#callsEverySpecificHookWithItsArguments`, Integration `ErrorHandlingIntegrationTest#theGlobalErrorHandlerRewritesACommandFailure` | covered |
| F183 | `onScheduledCommandHandlerError` | `BoosterScheduledCommandDispatcherTest#rethrowsOrSwallowsHandlerErrors` | covered |
| F184 | `onDispatchEventHandlerError` | `BoosterEventProcessorTest#theGlobalErrorHandlerCanSwallowAHandlerErrorAndTheRegisteredEventsAreStored` | covered |
| F185 | `onReducerError` (`null` snapshot instance); swallowing still stops the reduction | `EventStoreTest#aFailingReducerGoesToTheGlobalHandlerAndItsErrorIsThrown`, `#aReducerErrorSwallowedByTheGlobalHandlerStillStopsTheReduction` | covered |
| F186 | `onEventError` for unknown events | `EventStoreTest#anUnregisteredEventIsSkippedWhenTheGlobalHandlerSwallowsTheError`, `#anUnregisteredEventFailsTheFetchWithTheErrorOfTheGlobalHandler` | covered |
| F187 | `onProjectionError` | `ReadModelStoreTest#theErrorIsDispatchedOnceAndSwallowedWhenTheHandlerReturnsNull`, `#theContainerIsAProjectionGlobalErrorWithTheSnapshot` | covered |
| F188 | `onError` only after an existing specific hook returned an error | `BoosterGlobalErrorDispatcherTest#skipsOnErrorWhenTheSpecificHookIsMissing`, `#swallowsTheErrorWhenTheSpecificHookReturnsNull` | covered |
| F200 | `Authorization: Bearer` over HTTP, `connection_init.payload.Authorization` over websockets | `BoosterGraphQLDispatcherTest#verifiesTheTokenAndRunsTheOperationAsTheUser`, `GraphQLWebsocketHandlerTest#storesConnectionDataIncludingTheUserWithAnAccessToken` | covered |
| F201 | several verifiers: first success in order; expired > not-before > other | `BoosterTokenVerifierTest#theFirstSuccessfulVerifierInConfigurationOrderWins`, `#expiredWinsOverNotBeforeWhichWinsOverOtherReasons` | covered |
| F202 | `JwksUriTokenVerifier(issuer, jwksUri, rolesClaim?)`; default claim is `custom:role` (code) | `JwksUriTokenVerifierTest#exposesItsSettings`, `RoleBasedTokenVerifierTest#theDefaultRolesClaimIsCustomRole` | covered (docs say `roles`: the code wins) |
| F203 | `PublicKeyTokenVerifier(issuer, Promise<pem>, claim?)` | `PublicKeyTokenVerifierTest#verifiesRealTokensAndFailsWhenTheKeyCannotBeResolved` | differs: `CompletionStage<String>` or a PEM string |
| F204–F205 | custom and subclassed verifiers | `BoosterTokenVerifierTest#supportsVerifiersWithExtraChecks`, `#supportsVerifiersCheckingTheHeader` | covered |
| F206 | `getJwksClient`, `getKeyWithClient`, `verifyJWT` exported | `services.tokenverifiers.UtilitiesTest` | covered |
| F207 | JWKS glossary | — | n/a |
| F210 | whitelisting: missing `authorize` denies | `BoosterAuthorizerTest#buildChoosesTheAuthorizer` | covered |
| F211 | `'all'` works without a token | Integration `AuthorizationIntegrationTest#publicResourcesNeedNoToken` | covered |
| F212 | any listed role is enough | `BoosterAuthorizerTest#authorizeRolesAllowsUsersWithOneOfTheRoles` | covered |
| F213 | role name = class name | `decorators.RoleTest#registersARoleWithEmptyAuth` | covered |
| F214 | roles claim may be a single string; missing role rejected | `BoosterTokenVerifierTest#acceptsABearerTokenWithASingleRole`, Integration `AuthorizationIntegrationTest#otherUsersAreDeniedTheAdminCommand` | covered |
| F215 | `@Role({auth})` metadata | `decorators.RoleTest#registersTheAuthSettings` | covered |
| F216 | `CommandAuthorizer(user, envelope)` | `BoosterCommandDispatcherTest#migratesTheInputBeforeTheHooksAndAuthorizesTheOriginalEnvelope` | covered |
| F217 | `ReadModelAuthorizer(user, request)` | `decorators.ReadModelTest#usesACustomAuthorizer`, `BoosterReadModelsReaderTest#throwsANotAuthorizedErrorWhenTheCurrentUserIsNotAllowed` | covered |
| F218 | `EventStreamAuthorizer(user, request)` on every request | `BoosterEventsReaderTest#passesTheUserAndTheRequestToACustomAuthorizer` | covered |
| F219 | `UserEnvelope` fields | `RoleBasedTokenVerifierTest#buildsTheUserEnvelopeWithTheDefaultClaim` | covered |

### GraphQL API (§3.O)

| ID | Feature | Java test | Status |
| --- | --- | --- | --- |
| F230 | operation mapping | `GraphQLSchemaSdlTest#generatesTheSchemaOfAnApplication` | covered |
| F231 | `{query, variables}` → `{data}` / `{errors}` | `BoosterGraphQLDispatcherTest#runsTheOperationWithTheResolverContext` | covered |
| F232 | URL paths | — | elsewhere: providers |
| F233 | queries and mutations over the websocket | Integration `SubscriptionsIntegrationTest#queriesAndMutationsThroughTheSocketAnswerDataAndComplete` | covered |
| F234 | introspection enabled | `BoosterGraphQLDispatcherTest#isEnabledWithTheDefaultConfig` | covered |
| F235 | class-typed nested properties get nested filters and sorts; others `JSON` | `GraphQLQueryHelpersTest#nestsTheFiltersOfApplicationClasses`, `#sortsEveryStoredPropertyWithOrderPropertyExceptApplicationClasses` | covered |
| F236 | `CommandName(input: {...})` → `true` for `void` | `GraphQLGeneratorTest#returnsTrueWhenTheCommandReturnsNothing` | covered |
| F237 | read model by id | Integration `ReadModelQueriesIntegrationTest#findsAReadModelById` | covered |
| F238 | subscription payload = query result shape | Integration `SubscriptionsIntegrationTest#aSubscriptionByIdIsNotifiedOfEveryChangeOfItsReadModel` | covered |
| F239 | legacy `graphql-ws` protocol messages; nothing sent after a successful `start` of a subscription | `GraphQLWebsocketHandlerTest` (all), `BoosterGraphQLDispatcherTest#callsHandleResultWithTheGraphQLWebsocketSubprotocolHeaders` | covered |
| F240 | websocket auth once at `connection_init`, raw or `Bearer ` token | `UtilitiesTest#verifyJWTRemovesTheBearerPrefixAndReturnsHeaderAndPayload`, Integration `SubscriptionsIntegrationTest#theHandshakeStoresTheUserOfTheToken` | covered |
| F241 | `enableSubscriptions = false` | — | elsewhere: provider infrastructure (TypeScript core never reads it) |
| F242 | `@NonExposed` for commands, read models and queries | `decorators.NonExposedTest#registersMethodsFieldsAndConstructorParametersOfTheDeclaringClassOnly` | covered |
| F243 | read-model hooks on queries and subscriptions, chained, may throw | `BoosterReadModelsReaderTest#chainsTheBeforeHookFunctions`, `#storesTheFiltersProducedByTheBeforeHooks`, `FilteredReadModelPubSubTest#appliesTheBeforeHooksWithTheEnvelopeUser`, Integration `ReadModelQueriesIntegrationTest#theReadModelBeforeHookCanRejectAndRewriteRequests` | covered |
| F244 | `ReadModelRequestEnvelope` fields | `GraphQLGeneratorTest#callsSearchWithTheExpectedPayload` | covered |
| F245 | filter operator families, `and`/`or`/`not`, `isDefined` | `GraphQLQueryGeneratorTest#hasTheExpectedArgumentsForAStringProperty`, Integration `ReadModelQueriesIntegrationTest#combinesFiltersWithAndOrAndNot`, `#filtersOnArraysOfObjects` | covered (evaluation is the provider's) |
| F246 | `eq`/`ne` `null`; `regex` support | Integration `ReadModelQueriesIntegrationTest#combinesFiltersWithAndOrAndNot` | elsewhere: providers evaluate filters |
| F247 | `Booster.readModel(X).filter().select().search()`; instances without `select` | `BoosterTest#theSearcherCallsTheProviderSearchOnceWithTheTypeScriptArgumentsAndReturnsInstances`, Integration `ReadModelQueriesIntegrationTest#theSearcherPagesFromCode` | covered |
| F248 | `select` with `[]` array paths | `BoosterReadModelsReaderTest#usesTheTopLevelFieldOfArrayAndNestedPaths`, Integration `ReadModelQueriesIntegrationTest#selectsNestedPathsOfListsAndCountsWithoutItems` | covered |
| F249 | `select` skips schema migrations | `BoosterReadModelsReaderTest#passesTheSelectionAndDoesNotMigrate`, Integration `MigrationsIntegrationTest#oldReadModelsAreMigratedWhenRead` | covered |
| F250 | `sortBy` (one field; nested) | `GraphQLQueryHelpersTest#sortsEveryStoredPropertyWithOrderPropertyExceptApplicationClasses`, Integration `ReadModelQueriesIntegrationTest#pagesThroughASortedListWithTheCursor` | covered (one-field limit is the provider's) |
| F251 | `List*` connection `{items, count, cursor}`, `afterCursor` | `GraphQLGeneratorTest#readsTheConnectionOfAPaginatedSearch`, `BoosterReadModelsReaderTest#sendsTheTransformedSortLimitCursorAndPagination`, Integration `ReadModelQueriesIntegrationTest#pagesThroughASortedListWithTheCursor` | covered |
| F252 | `Authorization: Bearer` header | `BoosterGraphQLDispatcherTest#verifiesTheTokenAndRunsTheOperationAsTheUser` | covered |
| F253 | scalar names (`ID`, `String`, `Float`, `Boolean`, `UUIDPropertyFilter`) | `GraphQLTypeInformerTest#mapsUUIDToANonNullID`, `GraphQLQueryHelpersTest#filtersUuidsDatesAndEnums` | covered |

### Register, configuration, health, tracing, retries (§3.Q–§3.U)

| ID | Feature | Java test | Status |
| --- | --- | --- | --- |
| F280 | register injected into commands, handlers, data migrations | `BoosterEventProcessorTest#storesTheEventsOfEveryHandlerWithItsOwnRegister`, `BoosterDataMigrationsTest#runsTheMigrationsThatNeverStartedInOrderAndStoresTheStartedEventFirst` | covered |
| F281 | events persisted when the handler finishes; a failure stores nothing | `BoosterCommandDispatcherTest#rethrowsHandlerErrorsWithoutStoringEvents` | covered |
| F282 | `register.flush()` persists now; flushed events survive a later failure | `BoosterCommandDispatcherTest#eventsFlushedBeforeAHandlerFailureStayStoredAndTheLaterOnesAreNot`, `BoosterRegisterHandlerTest#flushUsesTheGlobalConfigurationAndClearsTheRegister` | fixed `67d71d31b` (partial success was untested) |
| F283 | `register.currentUser` | `BoosterCommandDispatcherTest#storesTheRegisteredEventsWithTheRequestAndUser` | covered |
| F284 | `register.context` (request and raw context) in commands | `BoosterCommandDispatcherTest#theRegisterCarriesTheRequestContextOfTheCommand` | fixed `67d71d31b` |
| F285 | `register.responseHeaders` reach the HTTP response | `BoosterCommandDispatcherTest#sharesTheResponseHeadersWithTheContext`, `BoosterGraphQLDispatcherTest#passesTheResponseHeadersTheHandlersAdd`, Integration `CommandToReadModelIntegrationTest#theResponseCarriesTheDefaultHeaders` | covered |
| F286 | `new Register('request-id-1')` | — | n/a (outdated docs; the code constructor takes requestID, headers, flusher, user, context) |
| F290 | `configure` per environment, `BOOSTER_ENV` | `BoosterTest#configuresTheCurrentEnvironmentAndRecordsEveryEnvironment`, `#theEnvironmentIsRequired` | covered |
| F291–F292 | `appName`, `providerPackage` | `BoosterTest#configuresTheCurrentEnvironmentAndRecordsEveryEnvironment` | covered |
| F293 | introspection enabled by default | `BoosterGraphQLDispatcherTest#isEnabledWithTheDefaultConfig` | covered |
| F295–F296 | other config fields; `config.readModels` keyed by class name | `ConfigTest` (framework-types), `decorators.ReadModelTest#registersTheReadModelWithItsProperties` | covered |
| F297 | entry points | `BoosterUserAppTest` | differs: one `UserApp` registered by `Booster.start` instead of re-exported functions |
| F310 | `/sensor/health/<path>` | `BoosterHealthServiceTest#aSubtreeRequestReturnsThatComponentWithItsChildren` | covered (URL routing is the provider's) |
| F311 | built-in ids including `rockets/<name>` | `BoosterHealthServiceTest#aRocketPathResolvesOneRocket`, `decorators.HealthSensorTest#seedsTheBuiltInIndicatorsWithTheCurrentSettingsAndAddsTheUserOne` | covered |
| F312 | built-ins disabled by default (`details`, `showChildren` true) | `BoosterHealthServiceTest#everythingDisabledByDefaultGivesAnEmptyHealthyResponse` | covered |
| F313 | `@HealthSensor`, children by path, overriding a built-in | `decorators.HealthSensorTest#aUserIndicatorReplacesABuiltInOneInPlace`, `BoosterHealthServiceTest#userIndicatorsExtendTheBuiltInTreeAndKeepExtraProperties` | covered |
| F314 | `enabled`, `details`, `showChildren` | `BoosterHealthServiceTest#theDetailsFlagRemovesTheDetailsOfAnyIndicator`, `#aDisabledChildLeavesAnEmptyComponentsList`, `#aDownComponentHiddenByShowChildrenDoesNotCount` | covered |
| F315 | response array; a throwing component is `DOWN` | `BoosterHealthIndicatorsTest#anExceptionIsDownWithTheErrorAsDetails`, `BoosterHealthServiceTest#anIndicatorThatThrowsFailsTheWholeRequest` | differs (code wins): built-in indicators report `DOWN`; a user indicator that throws fails the request through `requestFailed`, as in `booster-health-service.ts` |
| F316 | 200 / 503 / 401 | `BoosterHealthServiceTest#isNotHealthyWhenAnyComponentIsDown`, `#validationFailsWithTheWrongRole` | covered (`isHealthy` and the error are computed in core; providers map the status codes) |
| F317 | `globalAuthorizer` | `BoosterHealthServiceTest#validatesWithTheExpectedRole`, `#aCustomAuthorizerCanDeny` | covered |
| F318 | aggregation rules and details | `BoosterHealthIndicatorsTest` (all) | covered |
| F319 | rocket name derivation | — | elsewhere: providers (`areRocketFunctionsUp`) |
| F320 | `config.trace` (`true` / list / `includeInternal`) | `instrumentation.TraceTest#tracesOnlyTheEnabledActions`, `#addsTheInternalInformationWhenAsked`, `#isTraceEnabledFollowsTheTypeScriptRules` | covered |
| F321 | `TraceActionTypes` values | `SensorAndTraceTest` (framework-types), `EventStoreTest#tracesTheFetchAndItsSteps` | covered |
| F322 | trace payload fields | `TraceTest#keepsThisAndNotifiesStartAndEndAroundTheMethod`, `#addsTheInternalInformationWhenAsked` | differs: `internal` is `(target, Method)` |
| F323 | `@Trace(actionType)` on user methods | Integration `TracingIntegrationTest#aMutationIsTracedThroughEveryLayer` | covered |
| F330–F332 | `eventStoreRetry` backoff, rules and log texts | `RetrierTest` (framework-common-helpers), `BoosterRegisterHandlerTest#retriesTheStorageWithBackoffAndRethrowsTheLastError` | covered (algorithm in foundation) |

### Migrations, touch, delete events, rockets (§3.V–§3.W) and provider SPI (§3.Z)

| ID | Feature | Java test | Status |
| --- | --- | --- | --- |
| F340 | `@SchemaMigration` + instance `@ToVersion` methods for events and entities | `decorators.MigrationTest#collectsTheMigrationMethodsInDeclarationOrder`, `SchemaMigratorTest#migratesSnapshots`, Integration `MigrationsIntegrationTest#oldEventsAreMigratedBeforeTheyAreReduced` | covered |
| F341 | lazy migrations, nothing written back | `EventStoreTest#returnsTheMigratedSnapshotWithoutStoringItBackWhenThereAreNoPendingEvents`, `#failsWhenTheLatestSnapshotHasAnUnknownSchemaVersion` | fixed `50d7233d0` (a migrated stored snapshot without pending events was untested) |
| F342 | integer versions, default 1, step-by-step chain, gaps rejected | `SchemaMigratorTest#conceptsWithoutMigrationsAreVersion1`, `#migratesStepByStepIntoANewEnvelope`, `#reportsGapsBetweenVersions`, `BoosterRegisterHandlerTest#stampsTheCurrentSchemaVersion` | covered |
| F343 | `ReadModelSchemaMigrator.migrateAll(name, batchSize)` | `ReadModelSchemaMigratorTest#followsTheCursorWhileThePagesAreFull`, `#searchesWithTheMigrationFilterAndStoresTheNewVersions` | covered |
| F344 | `@DataMigration(order)`, `BoosterDataMigrations.run()` | `BoosterDataMigrationsTest` (all), Integration `MigrationsIntegrationTest#dataMigrationsRunOnceInOrder` | covered |
| F345 | `migrateEntity` emits `BoosterEntityMigrated` | `BoosterDataMigrationsTest#migrateEntityStoresABoosterEntityMigratedEventInTheOldStream` | covered |
| F346 | `Booster.entitiesIDs` | `BoosterTest#entitiesIDsCallsTheProviderWithItsParameterOrder`, Integration `EventsIntegrationTest#entityIdsArePaginated` | covered |
| F347 | `touchEntity` re-reduces and re-projects | `BoosterTouchEntityHandlerTest#storesABoosterEntityTouchedEventForTheEntity`, Integration `EventsIntegrationTest#touchingAnEntityProjectsItAgain` | covered |
| F348 | `Booster.deleteEvent`: tombstone, snapshots removed, still listed | `BoosterDeleteEventDispatcherTest#unProjectsAndDeletesTheSnapshotsOfEachEventThenDeletesTheEvents`, Integration `EventsIntegrationTest#deletingAnEventUnprojectsItsReadModelsAndRemovesItsSnapshot` | covered (the tombstone itself is written by the provider) |
| F349 | `unProject` with `ENTITY_DELETED` | `ReadModelStoreTest#runsTheUnProjectionsWithEntityDeleted` | covered |
| F366 | rocket functions through `boosterRocketDispatcher` | `BoosterRocketDispatcherTest#callsTheRegisteredFunctionWithTheRawRequest`, Integration `EventsIntegrationTest#rocketFunctionsAreDispatchedById` | covered |
| F391–F392 | provider SPI sections; read-model `store` with expected version, non-empty `fetch` arrays | `ReadModelStoreTest#retries5TimesWhenOptimisticConcurrencyFails4Times`, `#failsWhenTheProviderReturnsSeveralReadModels`, Integration `ReadModelQueriesIntegrationTest#injectedConflictsAreRetriedWithTheLatestVersion` | covered (the interfaces are framework-types') |

### Code-check resolutions (§3.AA)

| Ref | Resolution the code imposes | Java test | Status |
| --- | --- | --- | --- |
| F044/F032 | only varargs `register.events`; `flush()` awaits then clears | `BoosterRegisterHandlerTest#flushUsesTheGlobalConfigurationAndClearsTheRegister` | covered |
| F201 | all verifiers in parallel, first success by index, error precedence, reasons joined with `\n` | `BoosterTokenVerifierTest#theFirstSuccessfulVerifierInConfigurationOrderWins`, `#expiredWinsOverNotBeforeWhichWinsOverOtherReasons` | covered |
| F240/F206 | `token.replace('Bearer ', '')`, RS256 only, issuer, `The token could not be decoded` | `UtilitiesTest#verifyJWTRemovesTheBearerPrefixAndReturnsHeaderAndPayload`, `#verifyJWTReportsMalformedTokens` | covered |
| — | legacy JWT environment variables; missing `BOOSTER_ENV` message | `BoosterTest#loadsTheTokenVerifierFromTheEnvironment`, `#theEnvironmentIsRequired` | covered |
| F188 | missing specific hook → `Unhandled error inside the global error handler…`, `onError` not called; `onQueryHandlerError`, `onSnapshotPersistError` exist | `BoosterGlobalErrorDispatcherTest#skipsOnErrorWhenTheSpecificHookIsMissing`, `#callsEverySpecificHookWithItsArguments`, Integration `ErrorHandlingIntegrationTest#aFailingReducerStopsTheReductionAndIsLoggedWhenTheHandlerHasNoReducerHook` | covered |
| F106/F027 | one `input: XInput!`, omitted without properties; queries need a return type | `GraphQLQueryHelpersTest#addsTheInputArgumentOnlyWhenTheClassHasProperties`, `#rejectsQueriesWithoutHandle` | covered |
| F080/F081/F245 | plural deprecation text, `filter` argument, `List*` arguments, sequence key typed `ID` | `GraphQLQueryGeneratorTest#returnsTheExpectedResult`, `GraphQLSchemaSdlTest#generatesTheSchemaOfAnApplication` | covered |
| F151/F152 | `limit` argument, `EventType` from reducers only, `user` and `deletedAt` fields | `GraphQLQueryGeneratorTest#searchesEventsByEntity`, `#searchesEventsByType` | covered |
| F239 | message types; `connection_ack` sent; sub-protocol header | `GraphQLWebsocketHandlerTest#sendsBackAConnectionAck`, `BoosterGraphQLDispatcherTest#callsHandleResultWithTheGraphQLWebsocketSubprotocolHeaders` | covered |
| F331/F332 | retry by error name; `maxRetries` = attempts | `RetrierTest` (framework-common-helpers) | elsewhere: foundation |

### Documentation acceptance cases (spec 18 §5)

| # | Case | Java test | Status |
| --- | --- | --- | --- |
| 1–3 | create, plural query, by id | Integration `CommandToReadModelIntegrationTest#aMutationIsProjectedIntoTheReadModel`, `ReadModelQueriesIntegrationTest#filtersWithTheDeprecatedPluralQuery`, `#findsAReadModelById` | covered |
| 4 | command without `authorize` rejected | `decorators.CommandTest#setsTheDenyAuthorizerByDefault`, `BoosterAuthorizerTest#denyAccessDenies` | covered |
| 5 | `'all'` without token | Integration `AuthorizationIntegrationTest#publicResourcesNeedNoToken` | covered |
| 6 | single-string role claim not matching | `BoosterTokenVerifierTest#acceptsABearerTokenWithASingleRole`, Integration `AuthorizationIntegrationTest#otherUsersAreDeniedTheAdminCommand` | covered |
| 7 | error `message` and `path`, no stack trace | Integration `ErrorHandlingIntegrationTest#theGlobalErrorHandlerRewritesACommandFailure` | covered |
| 8 | handler result returned | `BoosterGraphQLDispatcherTest#returnsWhatTheCommandReturns` | covered |
| 9 | same-entity events reduced in order | `EventStoreTest#reducesALongListOfPendingEventsStartingFromTheLoadedSnapshot` | covered |
| 10 | first reducer gets no current state | `EventStoreTest#reducesFromTheOriginOfTimeWhenThereIsNoSnapshot` | covered |
| 11–12 | `Nothing` / `Delete` | `ReadModelStoreTest#ignoresTheReadModelWhenTheProjectionReturnsNothing`, `#deletesTheAssociatedReadModelWhenTheProjectionReturnsDelete` | covered |
| 13 | array join key runs per id | `ReadModelStoreTest#createsNonExistentReadModelsAndUpdatesExistingOnesFromAnArrayJoinKey` | covered |
| 14 | join-key function without match | `ReadModelStoreTest#projectsOnceWithoutIdNorReadModelWhenNothingMatches` | covered |
| 15 | sequenced read model array / exact match | Integration `SequencedReadModelIntegrationTest#everyReviewIsARowOfTheSameId`, `#theSequenceKeySelectsOneRow` | covered |
| 16 | subscription `data` message | Integration `SubscriptionsIntegrationTest#aSubscriptionByIdIsNotifiedOfEveryChangeOfItsReadModel` | covered |
| 17 | no `graphql-ws` sub-protocol → no connection | `BoosterGraphQLDispatcherTest#callsHandleResultWithTheGraphQLWebsocketSubprotocolHeaders` | elsewhere: providers enforce the handshake |
| 18 | read-model hooks reject/rewrite in order | Integration `ReadModelQueriesIntegrationTest#theReadModelBeforeHookCanRejectAndRewriteRequests` | covered |
| 19 | query hook sets a non-exposed field | Integration `ReadModelQueriesIntegrationTest#aQueryBeforeHookSetsANonExposedField` | covered |
| 20 | non-exposed read-model property absent | Integration `ReadModelQueriesIntegrationTest#nonExposedPropertiesAreStoredButNotPartOfTheSchema` | covered |
| 21 | `from`/`to` day bounds | Integration `EventsIntegrationTest#eventsByTypeFiltersByTimeAndLimit` | elsewhere: providers compare the strings |
| 22 | entity without `authorizeReadEvents` denied | `BoosterEventsReaderTest#theEventsOfTheBuiltInDataMigrationEntityCannotBeRead`, Integration `AuthorizationIntegrationTest#theEventStreamOfAnEntityFollowsItsAuthorizer` | covered |
| 23 | empty schedule never triggered | — | elsewhere: provider infrastructure |
| 24 | `[MyApp]\|Module: m` | `LoggerTest` (framework-common-helpers) | elsewhere: foundation |
| 25 | `onCommandHandlerError` swallowing | Integration `ErrorHandlingIntegrationTest#theGlobalErrorHandlerCanSwallowACommandFailure` | covered |
| 26 | flush, then throw: flushed event persisted, later not | `BoosterCommandDispatcherTest#eventsFlushedBeforeAHandlerFailureStayStoredAndTheLaterOnesAreNot` | fixed `67d71d31b` |
| 27 | response headers on the HTTP response | `BoosterGraphQLDispatcherTest#passesTheResponseHeadersTheHandlersAdd` | covered |
| 28 | notification defaults | `BoosterRegisterHandlerTest#wrapsNotificationsWithTheDefaults` | covered |
| 29 | union results with fragments | Integration `GraphQLTypesIntegrationTest#aQueryReturnsAListOfAUnion` | covered |
| 30 | `select` returns plain objects without migration | `BoosterReadModelsReaderTest#passesTheSelectionAndDoesNotMigrate` | covered |
| 31 | `select` of array element paths | Integration `ReadModelQueriesIntegrationTest#selectsNestedPathsOfListsAndCountsWithoutItems` | covered |
| 32 | two sort fields unsupported | — | elsewhere: providers |
| 33 | stored v1 snapshot migrated on load, nothing written back | `EventStoreTest#returnsTheMigratedSnapshotWithoutStoringItBackWhenThereAreNoPendingEvents`, `#reducesStoresAndReturnsWithTheRegisteredReducersAndSchemaMigrations` | fixed `50d7233d0` |
| 34 | new artifacts are version 1 without migrations | `SchemaMigratorTest#conceptsWithoutMigrationsAreVersion1` | covered |
| 35 | `deleteEvent` effects and `ENTITY_DELETED` | Integration `EventsIntegrationTest#deletingAnEventUnprojectsItsReadModelsAndRemovesItsSnapshot`, `ReadModelStoreTest#runsTheUnProjectionsWithEntityDeleted` | covered |
| 36 | `retryableErrors` / `nonRetryableErrors` | `RetrierTest` (framework-common-helpers) | elsewhere: foundation |
| 37 | health defaults disabled; enabled tree | `BoosterHealthServiceTest#everythingDisabledByDefaultGivesAnEmptyHealthyResponse`, `#allIndicatorsAreUp` | covered |
| 38 | throwing component → `DOWN`, 503 | `BoosterHealthIndicatorsTest#anExceptionIsDownWithTheErrorAsDetails`, `BoosterHealthServiceTest#anIndicatorThatThrowsFailsTheWholeRequest` | differs (code wins, see F315) |
| 39 | rejected `globalAuthorizer` → 401 | `BoosterHealthServiceTest#validationFailsWithTheWrongRole` | covered |
