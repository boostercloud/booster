# Azure provider parity ledger

Audit of the Java port of `packages/framework-provider-azure` (module `framework-provider-azure`) and
`packages/framework-provider-azure-infrastructure` (module `framework-provider-azure-infrastructure`) against the
TypeScript sources, item by item, following the reader notes `11-provider-azure.md` (runtime), `12-azure-infrastructure.md`
(deploy time) and the Azure rows of `18-docs-feature-checklist.md`. Where a note and the code disagreed, the code won.

Status values:

* **Test**: behaviour checked and pinned by the named Java test (class `#` method; runtime tests live in
  `framework-provider-azure/src/test`, infrastructure tests in `framework-provider-azure-infrastructure/src/test`).
* **Fixed**: a gap found by this audit, fixed in the named commit (and pinned by the named test).
* **Different**: intentionally different, with the reason (also listed in the module README's "Differences from
  TypeScript").
* **Unverifiable**: depends on Azure, the Azure SDKs or cdktn, none of which is available offline; what was done to
  narrow the risk is stated.

Audit date: 2026-11-10. TypeScript sources: the `packages/` tree of this branch (commit `7e5a8574` of the notes).

## 1. Audit fixes

| Commit | Finding | Pinned by |
| --- | --- | --- |
| `a81f45a30` | The Web PubSub access-key JWT used the request path **without** the `api-version` query and **with** the connection string port as `aud`. `webPubSubKeyCredentialPolicy` signs `new URL(request.url)` with `port = ''`: query kept, port removed. | `WebPubSubRestServiceClientContractTest#sendsTextMessagesToTheConnectionWithAnAccessKeyJwt`, `#theAudienceIsTheRequestUrlWithItsQueryAndWithoutPort`, `SignatureKnownAnswerTest#webPubSubAccessKeyJwt` |
| `403a5b280` | The signature tests recomputed the expected values with the same Java code paths. Added known-answer vectors computed with OpenSSL from the documented string-to-sign rules (master key, SAS, HS256 JWT). Also pinned that the REST client refuses cross-partition non-`VALUE` aggregates and `GROUP BY` instead of returning partial results. | `SignatureKnownAnswerTest` (4 tests), `CosmosRestClientContractTest#nonValueAggregatesAndGroupByAreRejectedInsteadOfReturningPartialResults` |
| `6e7576b69` | `isEventHubConsumerInput` only recognised the `EventHubConsumerInput` record; TypeScript's guard is structural (`bindingData` object with a `partitionKeyArray` array), so raw maps were ignored. | `EventsStreamAdaptersTest#theBindingDataGuardIsStructural` |
| `cbf0d9cc8` | The gateway NSG's `security_rule` objects omitted the attributes TypeScript does not set. `security_rule` is a `list(object)` attribute in the azurerm schema, Terraform's JSON syntax requires every attribute of those objects, and the cdktn bindings render the unset ones as `null`: every synthesized stack failed `terraform validate`. Found by the Azure contract suites of `framework-integration-tests` (opt-in `AzureTerraformValidateIT`). | `ApplicationSynthTest#every_security_rule_attribute_is_rendered_because_terraform_json_requires_them_all`, the golden files, the opt-in `TerraformValidateTest` |
| `f2680ddbb` | `AzureCounters` ran TypeScript's `SELECT COUNT(1) as total FROM c`, a non-`VALUE` aggregate that a cross-partition query can only run through the `NonValueAggregate` GROUP BY pipeline the REST client does not implement: every counter failed against a real account. Now `SELECT VALUE COUNT(1) FROM c [WHERE c.kind="event"]`, same counts. | `AzureTestHelperTest#counts_the_documents_of_each_container` |

## 2. Runtime provider (`11-provider-azure.md`)

### 2.1 Exports and wiring (§1, §2)

| Item | Status | Where |
| --- | --- | --- |
| `Provider(rockets)` → `ProviderLibrary`; new object per call, shared clients | Test | `ProviderTest#booterLoadsTheProviderFromTheProviderPackage`, `#theLibraryRoutesEveryCallToItsAdapterAndClients` |
| Every `ProviderLibrary` member of §2.2 bound to its adapter (events, read models, GraphQL, API, connections, scheduled, rockets, sensor, infrastructure) | Test | `ProviderTest#theLibraryRoutesEveryCallToItsAdapterAndClients` |
| Argument order quirk (`store(envelopes, config)` …) | Different | The Java SPI (`framework-types`) fixes the parameter order; no positional emulation is needed |
| `loadInfrastructurePackage` + two-sentence error | Test / Different | `ProviderTest#theInfrastructureLoadFailureIsReportedWithTheTypeScriptMessage`; the npm instructions are replaced by the Maven coordinates |
| `export * from './constants'` | Test | `Constants` (values compared with `constants.ts`), used by the infrastructure module |
| `export * from './types/azure-func-types'` | Test | `AzureFunctionTypesTest` (all guards) |
| `@azure/identity`, `@effect-ts/core` declared, never imported | Different | Not needed (Q16) |

### 2.2 Constants, module initialisation (§3.1, §3.2)

| Item | Status | Where |
| --- | --- | --- |
| Container attributes, env-var names, 409/412, hub `booster`, page size 100, chunk 100, dedup TTL 86400, `originOfTime` | Test | `Constants`, `QueryHelper.DEFAULT_PAGE_SIZE`, `EventsStreamConsumerAdapter.DEFAULT_DEDUP_TTL`, `EventsAdapter.ORIGIN_OF_TIME`; exercised by the adapter tests |
| `COSMOSDB_CONNECTION_STRING` undefined → no client; empty string → parse failure | Different | Lazy client (`ProviderTest#clientsAreCreatedLazilyFromTheAppSettings`): the failure happens at first use, not at load |
| Cosmos retry overrides only for non-empty settings, `Number()` semantics, SDK defaults 9 / server interval / 30 s | Test | `CosmosRestClientContractTest#readsTheRetryOptionsFromTheAppSettingsLikeIndexTs`, `#retriesThrottledRequestsAfterTheServerRetryAfter`, `#usesTheFixedIntervalWhenConfigured`, `#givesUpAfterMaxRetryAttemptCount`, `#givesUpWhenTheCumulativeWaitExceedsTheMaximum` |
| Event Hub producer dummy when name or connection string undefined **or empty**; retries default 5, `FIXED` only with `EVENTHUB_MAX_RETRIES` (Q5) | Test | `EventHubsRestProducerClientContractTest#readsTheRetryOptionsLikeIndexTs`, `ProviderTest#clientsAreCreatedLazilyFromTheAppSettings` |
| AMQP retry delays (30 s base, 90 s cap, exponential jitter) | Test | `EventHubsRestProducerClientContractTest#retriesServerBusyWithExponentialDelays`, `#fixedModeWaitsTheBaseDelayAndGivesUpAfterMaxRetries`, `#authenticationFailuresAreNotRetried` |

### 2.3 Function inputs and guards (§3.3)

| Item | Status | Where |
| --- | --- | --- |
| HTTP / Cosmos / Event Hub / timer / Web PubSub guards, structural, `'k' in input` | Test | `AzureFunctionTypesTest#httpInputsNeedRequestContextAndAStringMethod`, `#cosmosAndEventHubInputsNeedAnArray`, `#timerInputsNeedABooleanIsPastDue`, `#webPubSubInputsNeedAConnectionIdInEitherLocation` |
| `getWebPubSubConnectionContext`: request location wins when not null | Test | `AzureFunctionTypesTest#theRequestConnectionContextWinsWheneverItIsPresent` |
| `isSubscriptionNotifierInput` | Test | `ReadModelAdapterTest#stripsTheSystemPropertiesAndKeepsEverythingElse`, `#unexpectedInputsGiveNothing` |
| `isScheduledCommandInput` = timer guard, no `typeName` check (Q15) | Test | `ScheduledRocketAndSearcherAdapterTest#theTypeNameIsNotValidatedButTheTimerIs` |
| private `isEventHubConsumerInput` | Fixed `6e7576b69` | `EventsStreamAdaptersTest#theBindingDataGuardIsStructural` |
| Fetch `Headers` semantics (lower-cased, `get`, `forEach`) | Test | `AzureFunctionTypesTest#httpRequestsBehaveLikeFetchRequests` |

### 2.4 Partition keys and query helper (§3.4, §3.5)

| Item | Status | Where |
| --- | --- | --- |
| P1, P2 | Test | `PartitionKeysTest#shouldReturnTheCorrectPartitionKeyForAnEvent`, `#shouldReturnTheCorrectPartitionKeyForAnEntitySnapshot`, `#absentValuesRenderLikeUndefinedInATemplateLiteral` |
| `replaceOrDeleteItem` | Test | `QueryHelperTest#replacesWhenANewValueIsGiven`, `#deletesOtherwise` |
| Q1 … Q19 (exact SQL, placeholders, parameter order) | Test | `QueryHelperTest`: Q1 `executesASqlQueryWithoutFiltersInTheReadModelTable`, Q2 `executesASqlQueryWithAStringProjectionInTheReadModelTable`, Q3 `executesASqlQueryWithAProjectionForProjectionInTheReadModelTable`, Q4 `executesASqlQueryWithAProjectionThatHasArrayFieldsAndNestedObjects`, Q5 `executesASqlQueryWithAProjectionThatHasADeeplyNestedArrayOfObjects`, Q6 `executesASqlQueryWithAStarProjectionInTheReadModelTable`, Q7 `executesASqlQueryWithFiltersInTheReadModelTable`, Q8 `supportsNotFilterCombinator`, Q9 `supportsAndAndOrFilterCombinators`, Q10 `supportsNestedPropertiesFilters`, Q11 `supportsArrayIncludesFilter`, Q12 `supportsOrderFor1Field`, Q13 `supportsOrderForAnyNumberOfFields`, Q14 `supportsOrderForNestedFields`, Q15 `supportsLimitedResults`, Q16 `supportsPaginatedAndLimitedResults`, Q17 `supportsIsDefinedFilters`, Q18 `supportsIsDefinedFiltersWithComplexFilters`, Q19 `throwsAnErrorWithNonSupportedFilters` |
| Every SQL literal of the TypeScript tests appears verbatim in the Java tests | Test | Checked mechanically during the audit: the 18 non-template SQL literals of `packages/framework-provider-azure*/test/**` (adjacent `+` literals joined) are all present in the Java test sources; the 2 template literals resolve to the strings asserted by `EventsAdapterTest#queriesTheEventsTableToFindAllEventsRelatedToASpecificEntity` and `EventsAdapterTest#findsTheLatestEntitySnapshot` |
| `regex` / `iRegex` | Test | `QueryHelperTest#supportsRegexAndCaseInsensitiveRegex` |
| Placeholder collision drops the sub-index | Test | `QueryHelperTest#dropsTheSubIndexWhenAnInPlaceholderCollides` |
| Q9 mismatches kept: empty `IN ()`, `null` property filter, non-array `in` | Test | `#anEmptyInListProducesAnEmptyInClauseAsInTypeScript`, `#aNullPropertyFilterFailsLikeObjectEntriesOfNull`, `#aNonArrayInValueFailsLikeValueMap` |
| Q10 bare root + children | Test | `#aBareRootTogetherWithItsChildrenSelectsOnlyTheChildren` |
| Pagination decision table, `limit = 0`, cursors (Q11), `parseInt` prefix semantics | Test | `QueryHelperTest` (`limitZeroAddsNoLimitWhenNotPaginated`, `firstPageUsesContinuationTokensWithTheLimitAsPageSize`, `nextPagesPassTheContinuationTokenAndAdvanceTheIdByTheLimit`, `theDefaultPageSizeIs100AndAnEmptyLastPageHasNoCursor`, `aNumericCursorWithoutTokenUsesTheLegacyOffsetPathAndAlwaysReturnsACursor`, `distinctQueriesAlwaysUseOffsetLimit`, `distinctQueriesParseANonNumericCursorPrefixLikeParseInt`, `distinctQueriesWithATokenUseTheContinuationPath`, `aNonNumericCursorIdIsNotALegacyCursorButStillCountsAsOffset`, `aNumberCursorIdIsNotALegacyCursor`) |
| `processResources` / `nestProperties` (Q7, Q8) | Test | `QueryHelperTest#nestsDottedAliasesOfTopLevelKeysOnlyAndAddsTheEtagAsOptimisticConcurrencyValue`, `#rowsWithoutEtagGetAnEmptyBoosterMetadata`, `#nestPropertiesOverwritesFalsyIntermediates` |
| Filter values converted to raw JSON (UUIDs, dates) | Test | `#convertsFilterValuesToRawJsonFirst` |

### 2.5 Events, snapshots, dispatched markers (§3.6, §3.7)

| Item | Status | Where |
| --- | --- | --- |
| ST1 batch call `([Create…], pk, {})` | Test | `EventsStoreAdapterTest#publishesTheEventEnvelopesPassedViaParameterInBatches` |
| ST2 one-by-one `items.create` | Test | `#publishesTheEventEnvelopesOneByOneIfBatchingIsDisabled` |
| Group by partition key (H1), chunk size, request options, `Error <substatus> storing events: <JSON>`, `ocurred` log, rethrow + log of SDK errors, `Array.from` chunk semantics, every envelope field kept | Test | `#groupsByPartitionKeyAndReturnsTheEnvelopesGroupedByPartition`, `#splitsEachPartitionInChunksOfTheConfiguredBatchSize`, `#passesTheConfiguredRequestOptions`, `#aFailedBatchThrowsWithItsSubstatusAndResponse`, `#anUnexpectedErrorIsLoggedAndRethrown`, `#chunkingFollowsArrayFromSemantics`, `#documentsKeepEveryEnvelopeField`; end to end `AzureProviderContractTest#storingEventsSendsOneTransactionalBatchPerEntity`, `#aRejectedBatchFailsTheStore` |
| Same-millisecond `createdAt` in a chunk (H3) | Different (kept) | Existing behaviour, not changed |
| E1 `rawEventsToEnvelopes` | Test / Different | `EventsAdapterTest#generatesEnvelopesCorrectlyFromACosmosDbEvent`, `#acceptsRawMapsAndPlainArrays`, `#rejectsOtherInputs`; snapshots are dropped here because Java envelopes have a constant `kind` (`#keepsOnlyEventDocumentsBecauseJavaEnvelopesCannotCarryTheKind`) |
| E2 `readEntityEventsSince` SQL, `since` truthiness | Test | `EventsAdapterTest#queriesTheEventsTableToFindAllEventsRelatedToASpecificEntity`, `#usesTheGivenSinceAndTreatsTheEmptyStringAsAbsent`, `#returnsTheDocumentsAsEnvelopes` |
| E3 latest snapshot SQL | Test | `EventsAdapterTest#findsTheLatestEntitySnapshot`, `#returnsTheFirstResult` |
| `storeSnapshot` dead duplicate guard (Q1) | Test | `EventsAdapterTest#keepsTheDeadDuplicateGuardAndStoresTheSnapshotWithItsKeys`, `#throwsWhenTheGuardFindsASnapshot` |
| E4 `storeDispatchedEvent`, 409 → `false`, other errors rethrown | Test | `EventsAdapterTest#persistsTheIdsOfTheEventEnvelopesPassedViaParameters`, `#returnsFalseWhenTheEventWasAlreadyDispatched`, `#rethrowsOtherErrors`; `AzureProviderContractTest#dispatchedMarkersUseTheUniqueKeyConflictToDetectDuplicates` |
| Pinned-partition queries run as single-partition queries | Different | Same SQL and results, no query plan round trip (`AzureProviderContractTest#readingTheEventsOfAnEntityIsASinglePartitionQuery`) |
| Document `id` generated for documents without one (H2) | Test | `CosmosRestClientContractTest#createReadsThePartitionKeyDefinitionOnceAndGeneratesAnId`, `CosmosRestClientContractTest#sendsAnAtomicBatchWithGeneratedIdsAndRequestOptions` |

### 2.6 Search, deletion, stream (§3.8 – §3.11)

| Item | Status | Where |
| --- | --- | --- |
| S1 … S5 `searchEvents` filters and arguments | Test | `EventsSearcherAdapterTest` (`generatesFiltersForEntityEntityIdAndTimeWhenTheParametersHaveAllFields`, `generatesFiltersForEntityEntityIdAndTimeWhenTheParametersHaveAllFieldsAndLimited`, `generatesFiltersForEntityAndEntityIdWhenTheParametersHaveEntityAndEntityIdFields`, `generatesFiltersForTypeWhenTheParametersHaveTypeField`, `generatesFiltersForEntityWhenTheParametersHaveOnlyEntityField`, `onlyFromOrOnlyToProduceOneBound`, `neitherEntityNorTypeIsAnError`, `mapsTheResultsToEventSearchResponses`) |
| S6, S7 `searchEntitiesIds` | Test / Different | `EventsSearcherAdapterTest#generatesQueryForEntityTypeNameLimitAndAfterCursor`, `#generatesQueryForEntityTypeNameAndLimit`, `#returnsTheDistinctIdsPage`; Java items keep only `entityID` (closed record) |
| Soft-deleted events returned by `searchEvents` (Q12) | Test | `#mapsTheResultsToEventSearchResponses` |
| `findDeletableEvent` / `findDeletableSnapshot` filter key orders | Test | `EventDeleteAdapterTest#findsDeletableEventsWithTheEventFilter`, `#findsDeletableSnapshotsWithTheSnapshotFilterOrder` |
| Soft delete = full replace of the read document (`value: {}`, `deletedAt`, system fields + `boosterMetadata` sent back); hard delete of snapshots; warnings | Test | `#softDeletesEventsByReplacingTheDocumentTheyWereReadFrom`, `#eventsThisProviderDidNotReadAreRebuiltWithTheirPartitionKey`, `#hardDeletesSnapshots`, `#nothingToDeleteOnlyWarns` |
| Producer: partition key, greedy batches, too-large message, `Not all messages were sent`, empty input | Test | `EventsStreamAdaptersTest#sendsTheChangeFeedDocumentsWithTheEntityPartitionKeyInGreedyBatches`, `#envelopesItDidNotReadAreSentAsJson`, `#aSingleEventTooLargeForAnEmptyBatchFails`, `#anEmptyInputSendsNothing` |
| Message body = change feed document with `_etag` | Test | `AzureProviderContractTest#theStreamCarriesTheChangeFeedDocumentsAndTheConsumerDeduplicatesOnTheirEtag` |
| Dedup record, 409 skip with warning, other errors, unknown inputs | Test / Different | `EventsStreamAdaptersTest#storesADedupRecordPerMessageAndKeepsTheNewOnes`, `#otherErrorsPropagateAndUnknownInputsHaveNoMessages`; a message without `_etag` (produced from an envelope this provider did not read) is keyed by its `id` (`#theDefaultTtlIsOneDayAndMessagesWithoutEtagUseTheirId`) — TypeScript would store a record without `primaryKey` |
| `rawEventsStreamToEnvelopes`, `CONSUMED_EVENT` log (Q6) | Test / Fixed `6e7576b69` | `#mapsTheDeduplicatedMessagesToEventEnvelopes`, `#logsTheConsumedEventLineWhenBindingDataIsPresent`, `#theBindingDataGuardIsStructural` |
| Event Hubs REST batch: `{"Body", "BrokerProperties": {"PartitionKey"}}`, 1 MiB REST size | Different | AMQP replaced by REST (`EventHubsRestProducerClientContractTest#sendsTheBatchToTheMessagesEndpointWithASasTokenAndPartitionKeys`, `#tryAddRefusesEventsThatWouldExceedTheMaximumSize`, `#anEmptyBatchIsNotSent`, `#theEntityPathMustMatchTheEventHubName`); the JS SDK measures AMQP size |

### 2.7 Read models, subscriptions, connections (§3.12 – §3.15)

| Item | Status | Where |
| --- | --- | --- |
| R1 fetch `item(id, id)` | Test / Different | `ReadModelAdapterTest#respondsWithAReadModelWhenItExists`; a missing read model is `[]` instead of `[undefined]` (`#aMissingReadModelIsAnEmptyList`) |
| R2 insert path, version 1, `id.toString()`, If-Match upsert, `'*'`, 409/412 → `OptimisticConcurrencyUnexpectedVersionError`, other errors rethrown (Q4) | Test | `ReadModelAdapterTest#savesAReadModel`, `#version1AlsoInsertsAndTheIdIsAString`, `#laterVersionsUpsertWithIfMatchOnTheEtag`, `#withoutEtagTheConditionIsAStar`, `#anInsertConflictIsAnOptimisticConcurrencyError`, `#anUpdatePreconditionFailureIsAnOptimisticConcurrencyError`, `#otherErrorsAreRethrown`; `AzureProviderContractTest#readModelsAreInsertedUpdatedWithTheirEtagAndConflictsBecomeOptimisticConcurrencyErrors` |
| Delete swallows every error (Q13); `delete(null)` | Test / Different | `ReadModelAdapterTest#deletesByIdAndPartitionKey`, `#swallowsEveryErrorAsNotFound`; `null` is a no-op instead of a `TypeError` (`#aNullReadModelIsANoOp`) |
| `rawReadModelEventsToEnvelopes` strips `_rid _self _st _etag _lsn _ts` | Test | `ReadModelAdapterTest#stripsTheSystemPropertiesAndKeepsEverythingElse`, `#unexpectedInputsGiveNothing` |
| `searchReadModel` argument re-ordering | Test | `ScheduledRocketAndSearcherAdapterTest#readModelSearchesRunOnTheReadModelContainerWithReorderedArguments`; `AzureProviderContractTest#paginatedReadModelSearchesUseTheGatewayContinuationTokens` |
| Subscriptions: validation message with the unbalanced quote (Q3), stored attributes, per-item `ttl` = `expirationTime` (Q2), query whitespace, delete first match, delete all with `allSettled` | Test | `SubscriptionAndConnectionsAdapterTest$Subscriptions` (7 tests) |
| `context.rawContext` of the subscription envelope | Different | The foundation `ContextEnvelope` does not serialise `rawContext` |
| Connections: store with `ttl`, fetch/delete first match, logs | Test | `SubscriptionAndConnectionsAdapterTest$Connections` (5 tests) |
| `sendMessageToConnection`: new client per call, hub `booster`, `text/plain` JSON | Test | `SubscriptionAndConnectionsAdapterTest#sendsJsonTextThroughANewWebPubSubClientOnTheBoosterHub`, `WebPubSubRestServiceClientContractTest` (6 tests) |

### 2.8 GraphQL, API, scheduled, rockets, health (§3.16 – §3.20)

| Item | Status | Where |
| --- | --- | --- |
| G1 HTTP envelope (`requestID`, `token`, `value`, `context.request.body`, `rawContext` identity) | Test | `GraphQLAdapterTest#generatesAnEnvelopeCorrectlyFromAnAzureHttpRequest`, `#copiesTheLowerCasedHeadersAndDropsAnEmptyToken`, `#aNonJsonBodyIsKeptAsTextAndAnEmptyBodyIsAbsent` (A1 decided: raw text / absent) |
| Unknown input → error envelope `requestID 'unknown'` | Test / Different | `#anUnknownInputIsAnErrorEnvelope`; error envelopes have no `context` (foundation record) |
| Web PubSub: `system/connect|disconnect` mapping, data parsing, trigger metadata location, missing context | Test / Different | `GraphQLAdapterTest#systemConnectAndDisconnectMapToTheirEventTypes`, `#userMessagesAreParsedIntoClientMessages`, `#theConnectionContextMayComeFromTheTriggerMetadata`, `#aMissingConnectionContextIsNotAWebPubSubInput`; data that is not a client message becomes the error core would raise (`#unknownMessagesBecomeTheErrorCoreWouldRaise`) |
| A1 … A4 `requestFailed` status / title | Test | `ApiAdapterTest#returnsTheProperBodyForTheError` (parameterised: 400, 401, 404, 422), `#anyOtherErrorIsA500` |
| `requestSucceeded` decision table (4 cases), header filtering, `healthRequestResult` | Test | `ApiAdapterTest$TheRequestSucceededMethod` (5 tests), `ApiAdapterTest#healthyIs200AndUnhealthyIs503` |
| Scheduled envelope, fresh `requestID`, error message | Test | `ScheduledRocketAndSearcherAdapterTest#timerInputsBecomeScheduledCommandEnvelopesWithAFreshRequestId`, `#theTypeNameIsNotValidatedButTheTimerIs` |
| Rocket envelope `{ rocketId: BOOSTER_ROCKET_FUNCTION_ID }` | Test | `#theRocketIdComesFromTheFunctionAppSetting` |
| Health: URLs (`dbs/<db>`, `dbs/<db>/colls/<c>` as the JS SDK `url`), `TOP 1 1`, `VALUE COUNT(1)`, read model details sequential, `Promise.all` checks, `undefined/graphql`, rocket mapping parsing, component path regex (Q14), health envelope with `??` token | Test | `HealthAdapterTest` (9 tests), `AzureProviderContractTest#healthChecksRunAggregateQueriesOnEveryRange` |
| Functions entry points (the generated `functions.js` handlers, error responses of connect/disconnect/messages) | Test / Different | `BoosterAzureFunctionsTest` (19 tests); Java worker methods instead of `app.http(...)` registrations |

### 2.9 Request signatures (task: re-derive from the public specs)

| Item | Status | Where |
| --- | --- | --- |
| Cosmos DB master key: HMAC-SHA256 with the base64-decoded key over `verb\nresourceType\nresourceLink\ndate\n\n` (verb, type, date lower-cased; link case-sensitive), header `encodeURIComponent("type=master&ver=1.0&sig=…")`, `x-ms-date` RFC 1123 with two-digit day, `x-ms-version 2020-07-15` | Test / Fixed `403a5b280` | `SignatureKnownAnswerTest#cosmosMasterKeyAuthorizationOfAFeedRequest`, `#cosmosMasterKeyAuthorizationOfADocumentRequest` (OpenSSL vectors); every recorded request in `CosmosRestClientContractTest` is checked with `assertSigned` |
| Event Hubs SAS: HMAC-SHA256 with the UTF-8 key over `encodeURIComponent(uri)\nexpiry`, `SharedAccessSignature sr=&sig=&se=&skn=`, 1 h validity | Test / Fixed `403a5b280` | `SignatureKnownAnswerTest#eventHubsSharedAccessSignature`, `EventHubsRestProducerClientContractTest#theSasTokenFieldsMatchTheServiceBusFormat` |
| Web PubSub JWT: HS256 with the UTF-8 access key, `{"alg":"HS256","typ":"JWT"}`, `iat`, `exp = iat + 3600`, `aud` = request URL with query, without port | Fixed `a81f45a30`, `403a5b280` | `SignatureKnownAnswerTest#webPubSubAccessKeyJwt`, `WebPubSubRestServiceClientContractTest#sendsTextMessagesToTheConnectionWithAnAccessKeyJwt` |
| Data-plane API versions (`2021-10-01` Web PubSub, `2014-01` Event Hubs, `2020-07-15` Cosmos) | Unverifiable | The exact versions the installed JS SDKs send are not in the repository (no `node_modules`); the chosen versions support every call used |

### 2.10 Data shapes (§4) and quirks (§6.3)

| Item | Status | Where |
| --- | --- | --- |
| §4.1 – §4.7 persisted documents (field names, omitted `undefined`, kept `null`) | Test | Store/subscription/connection/dedup tests above assert the documents; `EventsAdapterTest#envelopesRoundTripTheirDocuments` |
| §4.10 function return values, §4.11 envelopes, §4.12 search results | Test | `ApiAdapterTest`, `BoosterAzureFunctionsTest`, `GraphQLAdapterTest`, `HealthAdapterTest`, `QueryHelperTest` |
| Q1 – Q16, H1 – H5 | Test (kept) | Listed in the module README "TypeScript quirks kept on purpose"; each has a test named above |
| A2 upsert + If-Match on a missing document | Unverifiable | Service-defined; the request shape is pinned by `CosmosRestClientContractTest#upsertSendsTheUpsertHeaderAndTheIfMatchCondition` |
| Cross-partition query execution (query plan, gateway vs client pipeline, `ORDER BY`, `DISTINCT`, `OFFSET/LIMIT`, `TOP`, `VALUE` aggregates) | Test / Unverifiable | `CosmosRestClientContractTest$Queries` (14 tests) against documented formats; the live gateway's exact plan payloads could not be recorded |

## 3. Infrastructure (`12-azure-infrastructure.md`)

### 3.1 Entry point, rocket SPI, types (§2)

| Item | Status | Where |
| --- | --- | --- |
| `Infrastructure(rocketDescriptors)` loads rockets eagerly; `undefined` vs `[]` kept apart | Test | `InfrastructureTest#loads_every_rocket_eagerly_through_the_rocket_loader`, `#keeps_undefined_and_empty_rocket_lists_apart`, `#fails_with_the_typescript_message_for_a_missing_rocket_package`, `#the_azure_provider_finds_this_infrastructure` |
| No `start` | Test | `AzureInfrastructureTest#start_is_not_supported` |
| `InfrastructureRocket` hooks, `RocketUtils.toTerraformName`, stack bag, function-definition types, `ZipResource`, `ResourceGroup`, `Resource`, `templates.policy` | Test / Different | `RocketBuilderTest#detects_the_defined_hooks`, `TemplatesTest`; Java SPI (`defines(Hook)`), `FunctionAppV4Definition` carries `function.json` definitions instead of `functionsCode` |

### 3.2 Settings and naming (§3.1 – §3.3, tests 8 – 18)

| Item | Status | Where |
| --- | --- | --- |
| Tests 8 – 18 (`toTerraformName`, `toAzureName`, `buildAppPrefix`, `createResourceGroupName`) | Test | `UtilsTest$BuildTheTerraformName`, `UtilsTest$CleanAnAzureName`, `UtilsTest$BuildTheAppPrefix`, `UtilsTest$CreateAResourceGroupName` |
| Worked examples of §3.2 (23-char branch, leading digit → `B`, `mystoreproductionsubscr`, …) | Test | `UtilsTest#follows_the_typescript_rules`, golden files |
| `getDeployRegion` message, URL region, `waitForIt` delay `(timeout - elapsed) % every`, `renderToFile` | Test | `UtilsTest$Region`, `UtilsTest$WaitForIt`, `UtilsTest$RenderToFile` |
| Env-derived constants (`parseInt` leniency, `NaN` → `null`), `BASIC_SERVICE_PLAN`, `USE_WAF` | Test / Different | `ApplicationSynthTest#writes_nan_throughputs_as_null_like_parse_int`; read when used instead of at module load |
| `readProjectConfig` | Different | One configuration is passed explicitly (hazard §3.6 removed) |
| `azureCredentials`, management clients | Test / Different | `ClientSecretCredentialTest`, `ArmClientTest`, `InfrastructureContextTest#builds_the_management_clients_with_the_configuration`; REST clients instead of the ARM SDKs |

### 3.3 Synthesis (§3.4 – §3.8)

| Item | Status | Where |
| --- | --- | --- |
| `buildApplication` order (base zip, stack, rockets, app settings, synth, zips, rocket zips) | Test | `AzureInfrastructureTest#synth_builds_the_terraform_json_and_the_packages_without_running_anything`, `#the_build_logs_the_typescript_messages` |
| `cdktf.json` | Different | Not written: it only configured `cdktn-cli`, which the port does not run |
| App settings map, key order, `config.env` override rules, consumer vs main bag state, `merge(defaults, rocket vars)` | Test | `ApplicationSynthTest#the_app_settings_keep_config_env_positions_and_let_booster_settings_win`, `AzureStackTest#the_default_settings_are_computed_with_the_whole_stack`, golden files |
| `ROCKET_FUNCTION_APP_NAMES`, `ROCKET_PACKAGE_MAPPING` (first rocket's app for every package) | Test | `AzureStackTest#joins_every_rocket_function_app_name_and_maps_packages_to_the_first_one`, `#duplicate_packages_overwrite_and_pairs_without_function_app_are_skipped`, `#without_rockets_the_rocket_settings_are_empty`, `#fails_without_function_app` |
| Resource order, conditions, guards with their messages | Test | `ApplicationSynthTest#fills_the_stack_bag`, `#the_builders_check_their_prerequisites`, `#without_subscriptions_the_dispatched_events_container_is_not_in_the_bag` |
| Resources 1 – 19 (type, construct id, name, attributes, references, `depends_on`, `lifecycle`, `provider` only where set) | Test | `SynthGoldenTest` (4 golden files) and the hand walk of §5 below |
| `BOOSTER_USE_WAF`, `BOOSTER_AZURE_SERVICE_PLAN_BASIC`, `BOOSTER_ENV` (default `azure`) | Test | `SynthGoldenTest#synthesizes_an_application_with_rockets_waf_and_basic_plan`, `ApplicationSynthTest#the_gateway_and_outputs_use_booster_env_with_azure_as_default` |
| Construct-id collisions (`constructs` message) | Test | `AzureStackTest#read_models_whose_construct_id_collides_fail_the_synthesis`, `TerraformStackTest#rejects_duplicated_construct_ids_with_the_constructs_message` |
| Output logical ids (outputs scoped to the provider) | Test / Unverifiable | `LogicalIdsTest` reproduces `makeUniqueId` (`azureFeature<id>_<md5>`); not checked against cdktn |
| `cdk.tf.json` key order, `"//"` metadata, `required_providers.azurerm.version` | Unverifiable | Keys are sorted (deterministic); `~> 5.0` constraint; the opt-in `TerraformValidateTest` (and `framework-integration-tests`' `AzureTerraformValidateIT` on the sample app) run `terraform validate` with `BOOSTER_TERRAFORM_VALIDATE=true` (passed with Terraform 1.15 and azurerm 5.x after `cbf0d9cc8`) |
| `site_config.application_stack` | Different | `java_version = $BOOSTER_AZURE_JAVA_VERSION` (default 21) instead of `node_version = '~22'` |
| Dead code (`TerraformWebPubSubExtensionKey`, `TerraformSleep`, `TerraformFunctionAppData`, time/null providers) | Different | Not ported (never instantiated) |

### 3.4 Rockets (§3.9)

| Item | Status | Where |
| --- | --- | --- |
| `mountStack` for every rocket in order, failures aggregated | Test / Different | `RocketBuilderTest#mounts_every_rocket_in_order_with_fresh_utils`, `#runs_every_rocket_and_then_throws_the_failures_together`; run sequentially (TypeScript starts them in order and awaits together; the hooks mutate the shared bag) |
| Hook priority V4 > `mountCode` > `mountFunctions`, `rocket_<\W+ → _>.zip`, sanitized vs unsanitized app names, errors with `[object Object]` | Test | `#uses_only_the_first_defined_hook_by_priority_and_sanitizes_names`, `#the_deprecated_hook_keeps_the_unsanitized_function_app_name`, `#the_deprecated_hook_needs_the_function_app_name`, `#rockets_without_package_hooks_contribute_nothing`, `#without_rockets_there_is_nothing_to_mount_or_upload` |
| Uploads concurrent to the application resource group; `unmountStack` at nuke | Test | `AzureInfrastructureTest#deploy_with_event_streaming_and_rockets_uploads_every_package`, `#nuke_unmounts_the_rockets_and_deletes_the_resource_group` |

### 3.5 Functions and packages (§3.10, §3.12, §3.13, tests 1 – 7)

| Item | Status | Where |
| --- | --- | --- |
| Function set per configuration (graphql, sensor-health, eventHandler/eventProducer, connect/disconnect/messages, notifiers per read model in order, timers with non-empty schedules), consumer `eventConsumer` with `events-stream` fallback | Test / Different | `FunctionsCodeGeneratorTest` (10 tests); `function.json` definitions for the Java worker instead of `functions.js` (binding names `request`/`documents`/`messages`/`timer`, `dataType: string`) |
| NCRONTAB `0 m h d M w`, `year` ignored, empty strings verbatim | Test | `SchedulesFunctionsTest#create_the_expected_nCronTab`, `#keeps_empty_strings_verbatim`, `FunctionsCodeGeneratorTest#writes_the_timer_with_the_ncrontab_schedule` |
| Tests 1 – 7 (legacy definitions) | Test | `EventHandlerFunctionsTest`, `GraphqlFunctionsTest`, `SchedulesFunctionsTest` (`is_an_undefined_object`, `is_one_definition_with_the_proper_fields`, `is_two_definitions_with_the_proper_fields`, `create_the_expected_nCronTab`, `skip_the_function`), `LegacyFunctionsTest` |
| Default `host.json`, custom/empty `host.json`, legacy layout, base Web PubSub package | Test / Different | `FunctionZipTest` (11 tests); Java package layout (`<app>.jar`, `lib/`, `function.json` per function), no generated `package.json` |
| Kudu upload: publishing credentials, host from `credentials.name`, status not checked | Test | `FunctionZipTest#uploads_a_package_through_kudu_with_the_publishing_credentials`, `#the_kudu_host_comes_from_the_credentials_name_and_the_status_is_not_checked` |
| A `host.json` listed in `config.assets` (F294) | Different (code kept) | The TypeScript glob ignores it and appends the default one; the documentation claims otherwise |

### 3.6 Deploy, nuke, az CLI, test helper (§3.11, §3.14 – §3.16)

| Item | Status | Where |
| --- | --- | --- |
| Deploy: Terraform, main/consumer zips, rocket uploads, hub update; failure message `Deployment of application … failed. Check Terraform logs. \n…` | Test / Different | `AzureInfrastructureTest#deploy_runs_terraform_uploads_the_package_and_updates_the_web_pub_sub_hub`, `#deploy_reports_terraform_failures_and_stops`, `#deploy_uses_the_configured_terraform_binary`; `terraform init/plan/apply` instead of `npx cdktn-cli deploy` |
| Cold start body and 10 s timeout, 30 s wait, 30 × 10 s polling, re-trigger every 5, final message, hub PUT body | Test | `#the_hub_update_gives_up_after_thirty_attempts_re_triggering_the_host_every_five`, `#the_hub_update_logs_and_rethrows_arm_failures` |
| Nuke deletes the resource group, ARM errors propagate | Test | `#nuke_unmounts_the_rockets_and_deletes_the_resource_group`, `#nuke_propagates_resource_manager_errors` |
| `az` helper commands and rejections | Test | `AzCliHelperTest` (4 tests) |
| Test helper outputs (`DEFAULT` vs `azure` defaults, trailing slash), configuration checks, connection string | Test | `AzureTestHelperTest#builds_the_outputs_counters_and_queries`, `#the_url_defaults_follow_typescript`, `#checks_the_azure_configuration`, `#fails_without_cosmos_connection_strings` |
| Counters | Fixed `f2680ddbb` | `AzureTestHelperTest#counts_the_documents_of_each_container`, `#counts_minus_one_when_the_query_returns_nothing` |
| Queries (`events` SQL verbatim, point read `[resource]`) | Test | `#queries_events_and_read_models` |
| §6.6 defects 1 – 13 | Test (kept) | Listed in the infrastructure README "TypeScript quirks kept on purpose" |
| §6.7 ambiguities | Unverifiable | Hub PUT and `anonymousConnectionsEnabled`, `.deploy-base` content, cdktn layout: see the infrastructure README |

## 4. Documentation checklist (`18-docs-feature-checklist.md`, Azure rows)

| Item | Status | Where |
| --- | --- | --- |
| F162 cron defaults, `year` ignored, empty schedule skipped | Test | §3.5 above |
| F246 `regex` / `iRegex`, `eq`/`ne` with `null` | Test | `QueryHelperTest#supportsRegexAndCaseInsensitiveRegex`, Q18 |
| F250 single sort field, nested sort | Test | Q12 – Q14 |
| F294 `config.assets` / `host.json` | Different | §3.5 above |
| F299 deploy env vars, Azure base URL | Test | `UtilsTest`, golden files, `ApplicationSynthTest` |
| F300 event batching (`enableEventBatching`, `batchSize`, `requestOptions`) | Test | §2.5 above |
| F301 `synth` output (`cdktf.out/stacks/<app><env>/cdk.tf.json`, `functionApp.zip`) | Test / Different | `SynthGoldenTest`, `AzureInfrastructureTest#synth_builds_the_terraform_json_and_the_packages_without_running_anything`; no `cdktf.json` |
| F302 event stream scaling (two apps, dedup container, Event Hub, partition count = consumer workers) | Test | `SynthGoldenTest#synthesizes_an_application_with_event_streaming` |
| F310, F318 health endpoints and Azure details (`url`, `count`, per read model array) | Test | `HealthAdapterTest`, `FunctionsCodeGeneratorTest#writes_the_graphql_and_health_http_triggers` |
| F333 `COSMOSDB_MAX_RETRIES`, `COSMOSDB_RETRY_INTERVAL`, `COSMOSDB_MAX_WAIT_TIME` | Test | §2.2 above |
| F346 `entitiesIDs` | Test | S6, S7, `AzureProviderContractTest#entityIdsAreDistinctPagesComputedFromEveryRange` |
| F348 delete events (tombstone, snapshots removed) | Test | §2.6 above |
| F361, F363 rockets (descriptors, v4 hooks, deprecated hooks) | Test / Different | §3.4 above; Java rocket SPI |
| F344 data migrations, F370 webhook rocket, F380 – F383 file uploads rocket | Out of scope | Core and rocket modules; this provider only supplies `entitiesIDs` and the rocket dispatcher entry points (`BoosterAzureFunctionsTest#httpRocketsGetTheWebhookRequestShapeAndMapTheResult`, `#blobRocketsGetTheBlobAndItsPath`) |
| F008 Node versions | Different | Not applicable (Java worker; see `BOOSTER_AZURE_JAVA_VERSION`) |

## 5. Hand walk of the TypeScript synthesis for one configuration

Configuration `Fixtures.config()`: `appName = my-store`, `environmentName = production`, `REGION = East US`,
`BOOSTER_ENV = production`, subscriptions on, streaming off, read models `CartReadModel` and `ProductReadModel`,
`config.env = { CUSTOM_SETTING: 'custom value' }`, default throughputs, consumption plan, no WAF. Compared attribute by
attribute with `src/test/resources/golden/subscriptions.cdk.tf.json` (infrastructure module); every row matched.

| TypeScript computation | Value | Golden file |
| --- | --- | --- |
| `buildAppPrefix` = clean(`my-store` + `production`) | `mystoreproduction` (17) | ids below |
| `createResourceGroupName` = `toAzureName('my-storeproduction', 20)` + `rg` | `mystoreproductionrg` | `azurerm_resource_group.mystoreproductionrg.name` |
| `TerraformResourceGroup`: id `toTerraformName(P, 'rg')`, `location: getDeployRegion()`, `provider` | `mystoreproductionrg`, `East US`, `azurerm` | equal |
| NSG id `toTerraformName(P, 'sg')`, name `RG + 'rjsg'`, two rules `Allow-All-<name>` / `…b`, priority 100, Tcp, `*`, no provider | `mystoreproductionsg` | equal |
| VNet `vn`, `10.0.0.0/16`; subnet `vsn`, `10.0.1.0/24`, `virtual_network_name` ref; association `snsg` refs; none with provider | | equal |
| Public IP id `'pip'` (literal), `Static`, `Standard`, `domain_name_label = RG + 'apis'`, no provider | `mystoreproductionrgapis` | equal |
| Gateway id `'ag'`, name `RG + 'apigw'`, `Standard_v2` ×1, pool fqdn `RG + 'fpr.azurewebsites.net'`, paths `/production/graphql/*`, `/production/sensor`, rewrite `/production/sensor/health(/.*)?` → `/api/sensor/health{var_request_uri_1}`, routing priority 100, provider | | equal |
| `datapip` id `toTerraformName(P, 'datapip')` (exactly 24), `name` ref `azurerm_public_ip.pip.name` | `mystoreproductiondatapip` | equal |
| Cosmos account `dba`, name `RG + 'cdba'`, `Standard`, `GlobalDocumentDB`, failover on, geo location ref, `Session`, `depends_on` RG, provider | | equal |
| SQL database `dbd`, name `resourceNames.applicationStack`, autoscale `parseInt('1000')` | `my-store-app`, 1000 | equal |
| Containers: `events` → `mystoreproductionevents`; `dispatched-events` (branch 4: drop 9 name chars) → `oductiondispatchedevents`, TTL 300, unique `/eventId`; `subscriptions-table` (branch 3: 23 chars) → `mystoreproductionsubscr`, TTL -1; `connections-table` → `oductionconnectionstable`, TTL -1; read models `toTerraformName(name)` → `cartreadmodel`, `productreadmodel`, `/id`; all `partition_key_version = 2`, refs to the account `resource_group_name`/`name` and the database `name` | | equal |
| Web PubSub `wps`, `Free_F1` ×1 | `mystoreproductionrgwps` | equal |
| Plan `psp`, `Y1`, 1 worker, Windows; storage `sp`, LRS, Standard | | equal |
| Function app `func`, name `RG + 'fpr'`, refs to plan and storage, `depends_on` RG, `ignore_changes = ['app_settings["WEBSITE_RUN_FROM_PACKAGE"]']`, `functions_extension_version = ~4`, `zip_deploy_file = <root>/baseWebPubSubBinding.zip` | | equal (plus `java_version`, see §3.3) |
| App settings: `WEBSITE_RUN_FROM_PACKAGE 1`, `WEBSITE_CONTENTSHARE = toTerraformName(P, 'func')`, `CUSTOM_SETTING`, `WebPubSubConnectionString` ref, `BOOSTER_ENV production`, `BOOSTER_REST_API_URL http://mystoreproductionrgapis.eastus.cloudapp.azure.com/production`, `EVENTHUB_CONNECTION_STRING ''`, `EVENTHUB_NAME booster_events`, `EVENTHUB_MAX_RETRIES 5`, `EVENTHUB_MODE exponential`, `COSMOSDB_CONNECTION_STRING` with the account name and primary key refs, `WEBSITE_CONTENTAZUREFILECONNECTIONSTRING` ref, `BOOSTER_APP_NAME ''`, then `merge(…, { ROCKET_FUNCTION_APP_NAMES: '', ROCKET_PACKAGE_MAPPING: '' })` | | equal (plus Java-only `BOOSTER_APP_CLASS`) |
| Hub `wpsh`, name `booster`, URL template with the function app name ref and `code=PLACEHOLDER`, `connect`/`disconnected`, `*`, anonymous, `depends_on` [func, wps], `ignore_changes = ['event_handler']` | | equal |
| Outputs (scope provider): `httpURL`, `graphqlURL`, `sensorHealthURL` on `http://${data…fqdn}/production`, `websocketURL wss://${wps.hostname}/client/hubs/booster_events` (event hub name quirk) | | equal |

The streaming configuration (`Fixtures.streamingConfig()`: topic `cart-events`, 4 partitions, 2 days, `maxRetries 0`,
`fixed`) was walked the same way for the extra resources: namespace `ehn` (`Basic`, capacity 2, no auto inflate),
hub `eh` (`partition_count 4`, `message_retention 2`), consumer plan `psc` (`B1`, `worker_count` = 4), storage `sc`,
consumer app `fhub` (plain settings map with `WebPubSubConnectionString ''`, `EVENTHUB_MAX_RETRIES '0'`,
`EVENTHUB_MODE fixed`, `EntityPath=${eh.name}`, storage `sc`), dedup container `toreproductiondeduptable`
(`/primaryKey`, unique, TTL -1): all equal to `event-stream.cdk.tf.json`.
