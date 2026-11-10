# booster-framework-provider-azure (`com.boostercloud.framework.provider.azure`)

Java port of `packages/framework-provider-azure`: the Azure runtime provider. It stores events, snapshots, read
models, subscriptions and connections in **Cosmos DB**, pushes websocket messages through **Web PubSub**, streams
events through **Event Hubs**, and adapts the **Azure Functions** trigger payloads and return values to the Booster
provider SPI (`ProviderLibrary`).

```java
config.providerPackage = "com.boostercloud.framework.provider.azure";   // Booster loads Provider.provider(rockets)
```

Like TypeScript, the provider depends only on `framework-types` and `framework-common-helpers`; it reaches the running
application through `UserAppRegistry` (registered by core's `Booster.start`).

## Architecture

```
Azure Functions Java worker
  └─ functions.BoosterAzureFunctions      one method per deployed function (= generated functions.js)
       │  wraps the trigger input ({ request, context }, { documents, context }, ...)
       │  boots the app once per worker (BoosterAzureApplication: BOOSTER_APP_CLASS → Booster.start)
       ▼
UserApp (core)  ──calls──►  ProviderLibrary = AzureProviderLibrary  (Provider.provider(rockets))
                                │  binds each adapter to the shared clients (index.ts' fn.bind(null, client))
                                ▼
library.*Adapter / helpers.QueryHelper     ports of src/library/*.ts and src/helpers/query-helper.ts
                                │  talk to small ports mirroring the @azure/* call chains
                                ▼
cosmos.CosmosClient ── cosmos.rest.CosmosRestClient            Cosmos DB SQL REST API
eventhubs.EventHubProducerClient ── eventhubs.rest.EventHubsRestProducerClient   Event Hubs REST "send batch"
webpubsub.WebPubSubServiceClient ── webpubsub.rest.WebPubSubRestServiceClient    Web PubSub data-plane REST
```

| Java | TypeScript | Notes |
| --- | --- | --- |
| `Provider`, `AzureProviderLibrary`, `AzureClients` | `index.ts` | lazily created process-wide clients |
| `Constants` | `constants.ts` | container attributes, app setting names, 409/412 |
| `types.*` (`AzureHttpFunctionInput`, `AzureCosmosDBFunctionInput`, `AzureEventHubFunctionInput`, `AzureTimerFunctionInput`, `AzureWebPubSubFunctionInput`, `AzureHttpRequest`, `AzureInvocationContext`, `TimerInfo`, `WebPubSubConnectionContext`, `AzureFunctionTypes` guards) | `types/azure-func-types.ts` | guards are structural: records or raw maps |
| `helpers.QueryHelper` | `helpers/query-helper.ts` | exact SQL text, placeholders, pagination |
| `helpers.JsValues`, `helpers.JsNumbers` | (JavaScript semantics) | truthiness, `parseInt`, `String()`, `Number()` |
| `library.PartitionKeys` | `partition-keys.ts` | |
| `library.EventsStoreAdapter` | `events-store-adapter.ts` | |
| `library.EventsAdapter` | `events-adapter.ts` | |
| `library.EventsSearcherAdapter`, `EventsSearcherBuilder` | `events-searcher-adapter.ts`, `events-searcher-builder.ts` | |
| `library.EventDeleteAdapter` | `event-delete-adapter.ts` | |
| `library.EventsStreamProducerAdapter` | `events-stream-producer-adapter.ts` | |
| `library.EventsStreamConsumerAdapter`, `EventHubConsumerInput` | `events-stream-consumer-adapter.ts` | |
| `library.ReadModelAdapter`, `SearcherAdapter` | `read-model-adapter.ts`, `searcher-adapter.ts` | |
| `library.SubscriptionModel`, `SubscriptionAdapter` | `subscription-model.ts`, `subscription-adapter.ts` | `RawEvent`/`SubscriptionIndexRecord` are raw maps |
| `library.ConnectionsAdapter` | `connections-adapter.ts` | `ConnectionIndexRecord` is a raw map |
| `library.GraphQLAdapter` | `graphql-adapter.ts` | |
| `library.ApiAdapter`, `AzureHttpResponse`, `WebPubSubConnectResponse`, `WebPubSubMessageResponse` | `api-adapter.ts` | |
| `library.ScheduledAdapter`, `RocketAdapter`, `HealthAdapter` | `scheduled-adapter.ts`, `rocket-adapter.ts`, `health-adapter.ts` | |
| `library.EventDocuments` | (none) | maps envelopes back to the documents they came from, see below |
| `functions.BoosterAzureFunctions`, `BoosterAzureApplication`, `FunctionMappings` | the generated `functions.js` (infrastructure package) | Java worker entry points |

## App settings (environment variables)

All read through `EnvironmentVariables` (in-process overrides, then the process environment).

| Setting | Read by | Effect |
| --- | --- | --- |
| `COSMOSDB_CONNECTION_STRING` | `AzureClients` | `AccountEndpoint=https://…:443/;AccountKey=…;`. Unset → no Cosmos client (using it fails with a clear error) |
| `COSMOSDB_MAX_RETRIES`, `COSMOSDB_RETRY_INTERVAL`, `COSMOSDB_MAX_WAIT_TIME` | `CosmosRetryOptions` | throttling (429) retries; each non-empty value overrides the SDK default (9 / server retry-after / 30 s), `Number()` semantics |
| `EVENTHUB_CONNECTION_STRING`, `EVENTHUB_NAME` | `AzureClients` | Event Hubs producer; unset **or empty** → no producer (streaming disabled) |
| `EVENTHUB_MAX_RETRIES`, `EVENTHUB_MODE` | `EventHubRetryOptions` | default 5 retries, exponential; `FIXED` only honoured when `EVENTHUB_MAX_RETRIES` is set (quirk Q5) |
| `WebPubSubConnectionString` | `ConnectionsAdapter` | read on every message (a client per message, as TypeScript) |
| `BOOSTER_REST_API_URL` | `HealthAdapter` | GraphQL URL `<url>/graphql` (`undefined/graphql` when unset) |
| `ROCKET_PACKAGE_MAPPING` | `HealthAdapter` | `pkgA:appA;pkgB:appB`, rocket health checks |
| `BOOSTER_ROCKET_FUNCTION_ID` | `RocketAdapter` | rocket id of a rocket function app |
| `WEBSITE_INSTANCE_ID` | `EventsStreamConsumerAdapter` | debug log only |
| `BOOSTER_APP_CLASS` (**Java only**) | `BoosterAzureApplication` | fully qualified application class passed to `Booster.start` by the entry points |
| `BOOSTER_ENV` | core | current environment |

`BOOSTER_WEBSOCKET_API_URL` and `ROCKET_FUNCTION_APP_NAMES` are defined in `Constants` but not read (as in TypeScript).

## Azure Functions entry points (for the infrastructure module)

`function.json` files reference `"entryPoint": "<class>.<method>"`; the constants live in `BoosterAzureFunctions`
(`*_ENTRY_POINT`, `ENTRY_POINTS`). Binding names must be the ones below. Annotation attributes such as database and
container names are placeholders: the generated `function.json` is authoritative.

| Function name(s) | Entry point (`com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions.`) | Trigger binding | Calls |
| --- | --- | --- | --- |
| `graphql` | `graphql` | `httpTrigger` `request` (POST, anonymous) + `http` `$return` | `boosterServeGraphQL({ request, context })` |
| `sensor-health` | `sensorHealth` | `httpTrigger` `request` (GET, route `sensor/health/{*componentPath}`) + `$return` | `boosterHealth` |
| `eventHandler` | `eventHandler` | `cosmosDBTrigger` `documents` (string), lease container `leases` | `boosterEventDispatcher({ documents, context })` |
| `eventProducer` | `eventProducer` | `cosmosDBTrigger` `documents` | `boosterProduceEventStream` |
| `eventConsumer` (consumer app) | `eventConsumer` | `eventHubTrigger` `messages`, cardinality `many`, dataType `string` | `boosterConsumeEventStream({ messages, context })` |
| `connect` / `disconnect` / `messages` | `connect` / `disconnect` / `messages` | `webPubSubTrigger` `request` (hub `booster`) + `$return`; metadata `connectionContext` | `boosterServeGraphQL({ request, context })`; error responses as `functions.js` |
| `<ReadModel>-subscriptions-notifier` | `subscriptionsNotifier` | `cosmosDBTrigger` `documents` on the read model container | `boosterNotifySubscribers({ documents, context, typeName })`, `typeName` from the function name |
| `scheduleFunction-<Command>` | `scheduledCommand` | `timerTrigger` `timer` (NCRONTAB) | `boosterTriggerScheduledCommands({ timer, context, typeName })`, `typeName` from the function name |
| HTTP rocket functions | `rocketHttp` | `httpTrigger` `request` (GET, POST) + `$return` | `boosterRocketDispatcher({ req: { method, url, originalUrl, headers, query, params, rawBody, body } })` (webhook rocket shape) |
| blob rocket functions | `rocketBlob` | `blobTrigger` `blob` (binary), metadata `BlobTrigger`, `Uri` | `boosterRocketDispatcher({ blob, context })` (file-uploads rocket shape) |

The application is booted once per worker: `BOOSTER_APP_CLASS` is loaded and
`com.boostercloud.framework.core.Booster.start(Class)` is called reflectively; an application already registered in
`UserAppRegistry` is used as is.

## Differences from TypeScript

* **REST instead of the Azure SDKs.** `@azure/cosmos`, `@azure/event-hubs` (AMQP) and `@azure/web-pubsub` are replaced
  by small ports with `java.net.http` REST adapters (no Azure SDK, as the architecture requires). The adapters keep
  the SDK behaviour the TypeScript code relies on:
  * Cosmos DB: master-key HMAC authorization, `x-ms-version 2020-07-15`, partition key headers, a random UUID `id`
    for documents without one (`create`, `upsert`, batch `Create`), the partition key of `create`/`upsert` read from
    the document with the container's partition key definition (one `GET colls/<id>` per container, cached),
    `read()` of a missing document returning a 404 response instead of throwing, `ResourceThrottleRetryPolicy` for
    429, transactional batches returned (not thrown) when they fail (code 207).
  * Cosmos DB queries: single-partition queries (with a partition key) go straight to the gateway. Cross-partition
    queries first ask for a **query plan**; plain queries are fanned out by the gateway and paged with its
    continuation tokens, the others (`ORDER BY`, `DISTINCT`, `OFFSET/LIMIT`, `TOP`, `VALUE` aggregates) run on every
    overlapping partition key range and are merged by the client, as the SDK's query pipeline does. Paginated
    pipeline queries use client tokens `booster-pipeline-skip:<n>` (opaque to Booster, which only passes them back);
    each page re-runs the ranges. `GROUP BY` and non-`VALUE` aggregates are rejected (Booster issues neither). A
    gateway refusal (400/1004) falls back to the pipeline.
  * Event Hubs: the REST "send batch events" API with a SAS token; each message is
    `{"Body": "<JSON>", "BrokerProperties": {"PartitionKey": "<entity>-<id>-event"}}`; batches are limited to 1 MiB of
    REST payload (the JS SDK measures AMQP size); transient failures use the core-amqp policy (30 s base, 90 s cap).
  * Web PubSub: `sendToConnection` with the access-key JWT the JS SDK signs (`aud` = request URL, 1 h, HS256), API
    version `2021-10-01`, and the core-rest-pipeline default retry policy.
* **Queries that pin the partition key pass it** (`readEntityEventsSince`, `readEntityLatestSnapshot`,
  `fetchSubscriptions`, connection queries), so they run as single-partition queries without a query plan. The SQL
  text and parameters are the TypeScript ones.
* **Envelopes are closed records.** TypeScript hands the Cosmos documents themselves to core as `EventEnvelope`s, so
  system properties survive. In Java:
  * `events().rawToEnvelopes` and `rawStreamToEnvelopes` **drop documents whose `kind` is not `event`** (snapshots):
    Java envelopes have a constant `kind()`, so core's `kind == 'event'` filter cannot see it. Soft-deleted events
    are kept (core filters `deletedAt`).
  * `EventDocuments` remembers (weakly) the document each returned envelope was read from. `produce` publishes that
    document (with its `_etag`, the consumer's dedup key) and `deleteEvent` replaces that document (system fields and
    the `boosterMetadata` added by `search` included), exactly as TypeScript does. For envelopes the provider did not
    return, `produce` sends the envelope JSON and `deleteEvent` rebuilds the document adding the partition key
    attribute; the consumer then de-duplicates on the event `id` when a message has no `_etag`.
  * `searchEntitiesIDs` items keep only `entityID` (TypeScript items also carry `boosterMetadata`).
  * Error envelopes of the GraphQL adapter have no `context` (the foundation record has none).
* **GraphQL request values are typed.** HTTP bodies become `GraphQLOperation`s (a truthy non-object body, e.g. a
  non-JSON string, becomes an empty operation, so core reports `Received an empty GraphQL query` as in TypeScript);
  websocket data becomes a `GraphQLClientMessage`, and data that is not a known client message becomes an error
  envelope with the error core would raise (`Unknown message type. Message=<String(data)>`). The raw parsed body
  stays in `context.request.body`.
* **SPI shapes**: `readModels().fetch` returns an empty list for a missing read model (TypeScript `[undefined]`);
  `readModels().search` always returns a `ReadModelListResult`; `readModels().delete(null)` is a no-op (TypeScript
  throws a `TypeError`); `latestEntitySnapshot`/`connections().fetchData` return `Optional`s.
* **Clients are created lazily** on first use instead of at module load, so a misconfigured setting fails at first use
  (an empty `COSMOSDB_CONNECTION_STRING` throws `Could not parse the provided connection string` then).
* **Infrastructure loading**: `<package>.infrastructure.Infrastructure` through `ProviderLoader`; the error keeps the
  TypeScript first sentence and replaces the npm instructions by the Maven dependency
  `com.boostercloud:booster-framework-provider-azure-infrastructure`.
* **Function inputs and outputs**: the wrappers are records built by the Java entry points from the worker's
  `HttpRequestMessage`/`ExecutionContext` (headers lower-cased like Fetch `Headers`; `json()`/`text()` work on the
  buffered body, so a non-JSON body is kept as text, the intended TypeScript behaviour). HTTP results
  (`AzureHttpResponse`) are turned into `HttpResponseMessage`s with `jsonBody` serialised by `BoosterJson`; Web
  PubSub results are returned as JSON strings.
* **Subscriptions do not store `context.rawContext`** (the foundation's `ContextEnvelope` never serialises it;
  TypeScript stores the serialisable part of the Web PubSub input).
* **Stringification** follows JavaScript: `JSON.stringify(undefined)` renders `undefined` in log lines and error
  messages (`Error undefined storing events`), `${object}` renders `[object Object]`.
* Logger locations and messages are the TypeScript ones (including typos such as `ocurred`).

### TypeScript quirks kept on purpose

Q1 dead duplicate-snapshot guard (`c.partitionKey`/`c.sortKey` never match), Q2 per-item `ttl` = absolute
`expirationTime`, Q3 unbalanced quote in the subscription validation message, Q4 `expectedCurrentVersion` and
`sequenceKey` ignored, Q5 `EVENTHUB_MODE` needs `EVENTHUB_MAX_RETRIES`, Q6 `bindingData` never provided by the
consumer function, Q7 every search row gets a `boosterMetadata`, Q8/Q10 projection and nesting rules, Q9 the two
independent SQL/parameter walks (e.g. `IN ()` for an empty list), Q11 legacy OFFSET cursors, Q12 `searchEvents`
returns soft-deleted events, Q13 `deleteReadModel` swallows every error, Q14 health URL/path rules, Q15 timer guard
ignores `typeName`, H1 batch results grouped by partition key, H3 same-millisecond `createdAt` in a batch chunk. Where
JavaScript would throw a `TypeError` the port throws `IllegalArgumentException` with the JavaScript message (`Cannot
convert undefined or null to object`, `value.map is not a function`, `Invalid array length`).

## Verification

Nothing contacts Azure. `mvn -B -pl framework-provider-azure -am verify` runs:

* **Contract tests** (`*ContractTest`): the production REST clients run against an in-JVM HTTP server
  (`support.FakeAzureServer`, `com.sun.net.httpserver`) answering with recorded service responses
  (`src/test/resources/fixtures`, shaped after the public REST API documentation). They pin method, path, query,
  headers, bodies, the Cosmos master-key signature and the Event Hubs SAS token (both recomputed independently in the
  tests), the Web PubSub JWT (decoded and its HMAC verified), continuation handling, query plans and the client-side
  merge, batch responses, error mapping (404, 409, 412, 429, 400/1004, 401, 503) and retries.
  `contract.AzureProviderContractTest` runs the whole `AzureProviderLibrary` with the REST clients against the fake
  server (events store, reads, dispatched markers, read model optimistic concurrency, paginated searches, entity ids,
  stream produce + dedup, subscriptions, websocket messages, health).
* **Unit tests** port every TypeScript test of the package (`query-helper`, `api-adapter`, `events-adapter`,
  `events-searcher-adapter`, `events-store-adapter`, `graphql-adapter`, `partition-keys`, `read-model-adapter`;
  Mockito replaces sinon, `mockStatic` replaces `stub(searchModule, 'search')`) and cover the adapters TypeScript does
  not test (subscriptions, connections, deletion, stream producer/consumer, scheduled, rocket, health, the function
  types, the provider wiring and the Functions entry points, including a once-per-worker boot under concurrency
  through a test stand-in of core's `Booster.start`).

What cannot be verified without deploying: the Azure Functions Java worker's delivery of the `webPubSubTrigger` value
and `connectionContext` metadata to `String` parameters and its handling of the JSON returned to the Web PubSub
extension; the exact query-plan and `x-ms-documentdb-partitionkeyrangeid` conventions of the live gateway (the
fixtures follow the documented formats and the SDKs' behaviour).
