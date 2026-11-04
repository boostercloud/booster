# Compatibility with Booster TypeScript

This inventory compares the Java implementation with Booster TypeScript 4.1.0
at `6bc5f816ca759db20a9aa2ee814341041ca2f95a`, webhook rocket
`07aa8a806ff6ae5acbdb1ba06ebdc068f6650cbb`, and file-upload rocket
`cc6d6463231846195bb2d5114c8d792003750f03`.

The complete scope remains in [GOAL.md](GOAL.md). **Implemented** below means
working Java code exists for the described contract; it does not mean all
TypeScript behaviors or a real Azure deployment have been verified. Executed
checks, failures and deferred work are recorded in [VALIDATION.md](VALIDATION.md).
AWS is excluded by the user. Real Azure deployment, cloud transfers and cleanup
were explicitly deferred by the user; they remain unverified.

## Implementation inventory

| Area | Java implementation | Evidence and boundary |
| --- | --- | --- |
| Domain registration | Typed builder APIs for entities, events, notifications, commands, queries, projections, named/global handlers and extensions. | Java records and explicit registration replace TypeScript decorators and metadata emission. There is no TypeScript source converter or npm execution bridge. |
| Command transactions | Expected stream versions, atomic event batches and result receipts, identity/context fingerprints, replay of matching requests, explicit register flush and response headers. | In-memory, JDBC and Cosmos journals share the contract. A flush is a real partial commit; a later exception cannot undo it. |
| Context and authorization | Public/default-deny, roles, request-aware and input-aware policies, ordered before hooks, ephemeral request headers/body/raw adapter context, shared response headers. | Authorization is checked before receipt replay. Credentials and raw adapter objects are not placed into event metadata. |
| Authentication | Static RSA and pinned JWKS RS256 verification, bounded cache/rotation, strict or explicit Booster issuer-only claim validation, configurable scalar/array role claim defaulting to `custom:role`. | Verified user exposes subject/id, username, roles, claims and signed token header; signed issuer-only tokens can authenticate without a subject. |
| Events and snapshots | Per-entity and global ordered journals, notifications, entity reduction, schema versions, durable snapshot documents and optimistic concurrency. | Runtime state is reconstructed by replay. Persisted snapshots do not currently eliminate full startup replay. |
| Projections | Multiple entity sources, key/function/filter joins, fan-out, old/new membership, explicit unprojection, store/delete/no-change outcomes, sequence keys, collision-free composite keys and persisted model versions. | Deterministic reducers/projections may run more than once. State-store CAS and event positions protect derived writes from older workers. |
| Read-model queries | By-ID reads, sequence-key queries, nested filters, sorting and keyset pages. | GraphQL selection sets are honored; runtime search currently scans the worker's replayed model cache rather than pushing arbitrary filters into Cosmos SQL. |
| Schema migrations | Consecutive versions and upcasters for commands, events, entities/snapshots and read models. | Missing steps, future versions and invalid identity changes are rejected. This migrates Java envelopes, not a pre-existing TypeScript database format. |
| Data migrations | Durable ordered catalog, parameters, RUNNING/FAILED/FINISHED lifecycle, explicit completion, resumable checkpoints, atomic step receipts, renewable lease, entity rename/ID migration and touch. | See [migrations.md](migrations.md). Returning from `start` does not complete a job; `BoosterDataMigrationFinished` remains explicit. |
| Event administration | Authorized event search, entity-ID paging, entity deletion and event-payload redaction with replay/reprojection. | Redaction preserves journal positions and stream versions. It does not erase independently returned command results, application exports or arbitrary external copies. |
| Errors and hooks | Named before-command/query/read-model hooks; typed stage errors and global handling for command, query, event-handler, reducer, projection, snapshot, event and schedule boundaries. | Explicit error replacement/suppression is stage-dependent. A durable command commit remains committed when later derived work fails. |
| Scheduling | UTC calendar attributes, durable occurrence cursors, renewable leases, backlog/retry, manual timer dispatch and stable occurrence receipts. | Local and Azure hosts use the same scheduler. External effects remain at least once; see [scheduling.md](scheduling.md). |
| GraphQL | Typed records/generic returns, commands/custom queries, by-ID/list/deprecated plural queries, typed filter/sort inputs, JSON cursors, event-history queries and subscriptions. | Contract tests cover names, nullability, number/ID/date types, sequence shape, errors and selections. Additional application-specific schema comparisons remain necessary. |
| Local subscriptions | Real legacy `graphql-ws` on the same `/graphql` listener as HTTP, plus POST SSE. | Initialization, start/stop/terminate, error/data/complete frames, authorization expiry and cleanup are implemented. Local connections are live process state, not resumable event logs. |
| Azure subscriptions | Web PubSub `booster` hub, authenticated CloudEvents, persisted identity/operations with TTL, leased Cosmos notification feed and provider-independent GraphQL rendering. | Operations survive host replacement. Delivery is at least once; stored claims are not a live identity-provider revocation feed. |
| Webhook rocket | Java local/Azure dispatcher, nested routes, GET/POST, legacy origin, request metadata/raw bytes/JSON/multipart, authorization, Register emission, text/JSON/binary/custom-header responses. | Compatibility defaults preserve recorded baseline quirks. Opt-in strict HTTP behavior is separate; see [rockets/webhook.md](rockets/webhook.md). |
| File-upload rocket | `presignedPut`, `presignedGet`, `list`, storage/container/directory selection, real local signed transfers, Azure user-delegation SAS/listing and upload-event/entity propagation. | Real SDK request fixtures are not live Azure SAS transfers. See [rockets/uploads.md](rockets/uploads.md). |
| Health | Component tree, inherited enable/details/children settings, custom authorization/indicators, provider reads, bounded checks, status aggregation and HTTP adapter. | Local hosting enables its standard tree; applications can replace the health handler. See [health.md](health.md). |
| Local provider/lifecycle | Durable event/state databases, exclusive data-directory ownership, application factory, HTTP/WS/rockets/schedules, synth/start/detach/status/stop/rebuild/nuke. | Independent of the demo. Cleanup requires the matching owned directory and excludes an active process. |
| Azure provider/lifecycle | Cosmos, Event Hubs, Web PubSub and Blob SDK clients; ARM synthesis/deploy/update/status/owned-group teardown; Java 27 Container Apps host. | Source and local contract tests exist. Real deployment/service integration/cleanup are deferred, not passed. See [azure.md](azure.md). |
| CLI/toolchain | Java 27 Maven framework, executable JAR, standalone project/domain generators, build/clean, bundled demo and local/Azure lifecycle commands. | Original TypeScript packages, lockfiles and build remain separate. Generated applications run without Node.js. |

## Transaction, replay and persistence contracts

An unflushed handler failure discards its buffered events. A normal successful
commit atomically records its events and result receipt after checking all
expected stream versions, including read dependencies. Reusing a request ID
with a different fingerprint conflicts. Authorization runs before a cached
result is returned. A conflicting write does not automatically rerun arbitrary
application code.

`Register.flush()` validates and commits the current batch, then clears it only
after success. Subsequent work observes the committed state. Flush receipts and
command/action receipts use stable identities so retries can recover committed
steps. Callers must understand that a failure after a flush can leave durable
events even when the enclosing handler did not finish.

Reducers and projections are evaluated during precommit validation and replay.
They must be pure and deterministic. Named event-handler receipts cover emitted
events and handler completion; a network call performed by the handler can
succeed before its receipt commits. Use destination-side idempotency for such
external effects. Restart recovery retries unfinished named handlers. Rebuilding
projections alone does not replay their already completed external effects.

`StateStore` persists versioned snapshots, read models, migration progress and
scheduler leases with compare-and-set updates and tombstones. The event journal
is authoritative. Each runtime reconstructs an in-process entity/read-model
cache from the journal; durable derived documents also support provider
notifications and schema migration. Startup replay and query memory consumption
must therefore be included in application capacity planning.

JDBC/H2 local storage supports one owning JVM for a local application directory.
Multiple store/runtime objects within that process can exercise competing
transactions. `LocalProvider` holds a stable filesystem lock and checks ownership
before startup or cleanup. A running local application's data cannot be removed
by the lifecycle command. Signed local file storage has its own immutable
version/manifest layout and lock.

The Cosmos journal uses one application partition to preserve atomic
cross-entity commits and consecutive global positions. It inherits Cosmos's
100-operation transactional-batch limit, 2 MiB batch limit and 20 GiB logical
partition limit. The implementation rejects oversized transactions instead of
splitting them into partially committed batches. Event receipts also contain
event copies, which count toward storage and transaction size. This partition
layout differs from the TypeScript provider and requires explicit capacity
planning or a future journal design change.

## GraphQL wire contracts

- Operation names derive from registered Java classes. Command/query records
  produce `<Name>Input`; commands with fields receive `input: <Name>Input!`.
  Generic return types are retained with the builder's Jackson `TypeReference`
  overloads rather than erased to an untyped list.
- Record fields default to non-null. `@GraphQLNullable` and `Optional<T>` express
  optional fields. List elements default to non-null; optional elements can use
  `Optional<T>`. `@GraphQLHidden` removes a field from inputs, outputs, filters
  and sorts. Java metadata must represent the original application's intended
  nullability; copying a class name alone does not establish schema equivalence.
- Numeric domain fields use GraphQL `Float`, matching TypeScript `Number`.
  Integral Java fields still reject fractional or out-of-range values. GraphQL
  `Float` has IEEE-754 precision limits; do not use it to exchange arbitrary
  64-bit identifiers. `UUID` maps to `ID`, while `String` maps to `String`.
- Java instant/offset date-time values use the `Date` scalar and normalized
  millisecond ISO strings. Non-record external values remain JSON scalars.
  A sealed interface whose permitted implementations are records can form an
  output union. Serialized union values need a matching `__typename`
  discriminator, for example Jackson `@JsonTypeInfo`/`@JsonTypeName` metadata;
  the runtime does not guess between ambiguous object shapes.
- Read-model queries include `<Model>(id: ID!)`, `List<PluralModel>` returning
  `<Model>Connection!`, and the deprecated plural collection query. For a
  sequence-key model, `<Model>(id: ID!, <sequence>: ID)` returns `[Model]`, even
  when a sequence selects one row. Omitting the sequence returns matching rows.
- Filters use the baseline names such as `List<Model>Filter`,
  `<Model>SubscriptionFilter`, `StringPropertyFilter`, `NumberPropertyFilter`
  and array property filters. Sort inputs use `<Model>SortBy` and
  `orderProperty { ASC DESC }`. Nested objects, logical combinations and
  existence tests are validated before opening subscriptions.
- English pluralization is translated from the exact pinned `inflected@2.1.0`
  rules. Recorded fixtures were generated by executing that npm distribution;
  Java runtime/tests do not depend on Node.js. Its MIT notice is packaged in
  `META-INF/licenses/inflected-MIT.txt`.
- `afterCursor` and response `cursor` are JSON values. Java emits an opaque
  `{token: ...}` keyset cursor and accepts that value on the next request.
  It is bound to filter/sort and is not a snapshot across concurrent changes.
  Existing TypeScript provider cursor bytes are not importable. GraphQL `count`
  is the returned page length; the Java core's `Page.count()` reports total
  matches. Page limits are bounded to 1–1000.
- `eventsByEntity` and `eventsByType` use generated `EntityType`/`EventType`
  enums and the baseline `EventQueryResponse` fields. Access is default-deny
  per entity and can use a request-aware policy. Internal control events are
  not presented as ordinary declared event types.
- Error extensions use baseline codes such as `NotAuthorizedError`,
  `NotFoundError`, `InvalidParameterError` and
  `OptimisticConcurrencyUnexpectedVersionError`. An explicit
  `GraphQLClientException` exposes an application message/code/data. Unexpected
  implementation exceptions retain generic messages and do not expose secrets
  or Java stack traces.
- POST `/graphql` executes queries/mutations. A WebSocket upgrade at the same
  path negotiates **legacy `graphql-ws`**, not `graphql-transport-ws`.
  POST `/graphql/stream` additionally supports SSE.

The legacy protocol implements `connection_init`, `start`, `stop` and
`connection_terminate`, and responds with `connection_ack`, `connection_error`,
`data`, `error` and `complete`. Query/mutation operations complete after their
result. Completed IDs can be reused for a later operation. Duplicate active IDs
are rejected. By-ID subscriptions can emit null on deletion; filtered
subscriptions emit matching nondeleted values and do not emit removal notices
when a row stops matching. Before hooks and authorization apply consistently to
local subscriptions and persisted Azure notification rendering.

The local transport bounds request/message size, query depth/complexity,
connections, operations and queued messages. Authentication is rechecked before
local deliveries and on heartbeat. Changed identity/roles/claims terminate the
connection so authorization must run again. Slow local subscribers can exceed
the bounded core publisher buffer; that is not a durable subscription replay
mechanism.

## Authentication and contextual data

JWT verification accepts only RS256 with an RSA key of at least 2048 bits. JWKS
keys come only from the configured HTTPS URL, never token `jku`/`x5u` headers.
Loopback HTTP is available for tests. Fetches have a complete-response deadline,
a document/key-count bound, no redirects, bounded key lifetime and a cooldown
for unknown-key refresh. Expired keys are not silently reused after a failed
refresh. The standard role claim is `custom:role`; a configured alternative such
as `roles` may contain a string or an array of strings.

Existing `JwtVerifier` and `JwksVerifier` constructors retain strict validation:
configured audience, nonblank subject and integral expiration are required;
future issued-at is rejected. `JwtVerifier.boosterCompatible(key, issuer)` and
`JwksVerifier.boosterCompatible(uri, issuer)` explicitly select Booster's
issuer-only policy. They do not require or compare audience, permit absent
subject/expiration, ignore issued-at for validity, and check present numeric
expiration/not-before dates, including fractions. Both profiles enforce RSA
signature validation, configured issuer, token bounds and unambiguous JSON.
Signed identities lacking a subject retain an explicit verified flag so
authenticated resource policies do not mistake them for anonymous requests.

Verified identity preserves claims and the signed header as immutable values.
`id()` aliases subject; `username()` follows email, phone number, then subject.
Raw bearer-token bytes are not persisted as identity. HTTP/WS request headers,
body and provider context are ephemeral `RequestData`, separate from durable
metadata. Transport-owned framing/content headers cannot be overridden through
`Register.responseHeader`.

Azure persists verified connection identity and absolute expiry instead of raw
credentials. It applies current resource policies when rendering notifications
and expires connections, but cannot reread a removed raw token from JWKS or
observe identity-provider revocation automatically. Reconnect with refreshed
credentials to update claims; application policies may consult their own
revocation state. This differs from local authentication-provider rechecks and
must not be described as automatic cross-provider claim refresh.

## Provider and rocket boundaries

Azure hosting uses a Java 27 Container Apps container. HTTP routes, timers,
Cosmos leased change feeds, Event Hubs consumers, Web PubSub callbacks and Blob
Event Grid notifications are Java implementations. The deployment does not
silently downgrade to an older Azure Functions Java runtime. It creates a
separate resource layout, rather than updating TypeScript Functions artifacts
in place. See [azure.md](azure.md) for exact inputs, image requirements, roles,
endpoints and teardown ownership rules.

Cosmos change feed to Event Hubs and Web PubSub delivery are at least once.
Checkpoints advance after successful work; a send may succeed before checkpoint
persistence, allowing duplicate delivery. The latest-version projection feed
can coalesce intermediate states. The journal, rather than a subscription, is
the complete durable event history.

The webhook port preserves tested baseline defaults, including legacy `origin`,
GET handling, failure envelopes, CORS and empty falsy JSON responses. Its strict
HTTP mode is opt-in. Multipart processing keeps binary bytes intact and bounds
body/part/header sizes. Response receipts preserve bytes and headers across
retries. Java handlers and register events execute in the same process as the
application, both locally and in the Azure host.

File-upload methods belong to `FileHandler`; the baseline rocket does not add
global GraphQL operation names automatically. Application commands/queries
expose them under their own contracts. Runtime providers support multiple
configured stores with explicit container/directory selection. Local signed URLs
perform actual bounded transfers and retain failed notifications for restart
replay. Azure uses managed identity and user-delegation read/write SAS, real
paged Blob listing, and BlobCreated-to-event/entity propagation. Directory
pattern fixtures include recorded results from the pinned minimatch dependency.

The Java event/state/file formats, schema names and ARM resources are independent
of existing TypeScript data and deployments. Source, database and infrastructure
migration require deliberate conversion and validation. Merely pointing the
Java provider at a TypeScript Cosmos container or local data directory is not
supported. No automatic TypeScript database import/export converter is supplied.

## Validation follow-up and migration guidance

The following boundaries describe how to use the implementation and interpret
the evidence. They do not add requirements to the agreed goal:

1. **Live Azure validation is deferred by the user.** ARM acceptance, deployment/update,
   managed-identity/RBAC propagation, real Cosmos/Event Hubs/Web PubSub behavior,
   real SAS upload/download, restart recovery and verified cleanup have not been
   demonstrated against an Azure subscription. They remain future validation
   follow-up, outside the currently required acceptance checks. Local SDK and
   transport fixtures must not be presented as successful live deployment.
2. **Regular-expression compatibility is being completed.** Current expressions use RE2/J; JavaScript
   lookaround/backreferences and other unsupported ECMAScript regex behavior are
   rejected. Representative baseline regex patterns work; arbitrary JavaScript
   regex support is under implementation and is not yet marked complete here.
3. **Application migration should compare its own wire contracts.** The
   included tests cover concrete baseline contracts and captured dependency
   behavior of the required framework features. Each application port should
   additionally compare its own schema, custom scalar, nullability, hook and
   authorizer contracts. That application-specific work is migration guidance,
   not an additional framework implementation requirement.
4. **Storage/deployment interoperability is not automatic.** Data migrations
   operate on Java envelopes. Importing existing TypeScript journals, local
   files, provider cursors or infrastructure state needs a deliberate conversion
   path with identity/order/schema validation. An automatic TypeScript database
   or infrastructure converter was not requested; the implemented data-migration
   engine is a separate capability.

Keep this inventory and the goal checklist synchronized with implementation and
executed evidence. A synthesized template, SDK mocks or a passing demo do not
establish live Azure deployment behavior.
