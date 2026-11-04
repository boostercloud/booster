# Compatibility with Booster TypeScript

This directory is an independent Java edition of Booster. It preserves the
event-sourcing and CQRS programming model, but it is not a binary, source, data,
or deployment-compatible replacement for the TypeScript packages. Existing
TypeScript applications, plugins, databases, and deployment resources cannot be
used unchanged.

The comparison baseline is Booster TypeScript 4.1.0 at commit
`6bc5f816ca759db20a9aa2ee814341041ca2f95a`. Features below describe that source
baseline rather than a claim that every provider implements every operation.

## Behavioral contract

The essential application flow is command handling, durable event recording,
entity reduction, read-model projection, and querying the projected state.
An implementation must preserve ordering within an entity stream and make its
durability, concurrency, delivery, and transaction boundaries explicit.

Authorization must fail closed. Public resources require an explicit opt-in.
User identity, correlation IDs, and request context must not be invented by the
HTTP client or silently discarded during event processing. Schema versions are
positive integers; unknown future versions and gaps in a migration chain are
errors.

The TypeScript implementation buffers events in a handler's `Register` and
persists them after successful completion. An explicit flush persists the
current buffer and clears it only after success. An error occurring after an
explicit flush cannot roll back events already stored. Read-model projections
derive disposable state from the event history; snapshots are replay caches,
not the authoritative history.

## Feature comparison

The Java implementation and tests are the source of truth for supported APIs.
Test execution results are recorded separately in [VALIDATION.md](VALIDATION.md).
"Supported" below describes the Java contract, not identical TypeScript APIs.

| Area | TypeScript baseline | Java edition |
| --- | --- | --- |
| Domain registration | Decorators register commands, events, entities, reducers, read models, queries, and handlers. | Explicit typed builder registration, Java records, named operations, duplicate-name checks, and one entity reducer per event type. No annotation scanning. |
| Commands and context | Registers buffer events, allow explicit flush, carry user/context, and expose response headers. | Registers buffer events until handler success and expose request context and entity/history reads. No mid-handler flush or response-header API. |
| Authorization | Deny by default; public, role-based, and request-aware custom authorizers. | Explicit public, authenticated, role, or user-predicate access policies. Command shorthand defaults to deny; queries/read models require a policy. Subscriptions use the read model's policy. Payload/row-specific rules belong in application handlers. |
| Authentication | Public-key and JWKS verifiers; configurable roles claim, default `custom:role`. | Configured RSA public key with RS256, issuer, audience, subject, expiry, nbf/iat validation; roles are a `roles` array. Custom authentication SPI. No built-in JWKS discovery or key rotation. |
| Event persistence | Provider-specific durable event envelopes, per-entity history, snapshots, and duplicate-dispatch records. | Atomic event batches with request receipts, expected stream versions, unique event IDs, and consecutive global positions. Process-local in-memory and transactional H2/JDBC stores. |
| Entity reduction | Ordered replay from persisted snapshots, entity identity, and schema migration. | Ordered journal replay with an in-memory entity cache. Restart reconstructs state from history; snapshots are not persisted. |
| Read models | Projection join keys, fan-out/filter joins, deletion/no-op outcomes, metadata, and optimistic concurrency retries. | One source entity per read model, keyed by the same entity ID. Projection receives previous state; null deletes and an unchanged value produces no update. Models are held in memory and rebuilt from the journal. No joins, fan-out, sequence keys, or independently persisted model versions. |
| Queries | By-ID lookup, nested filters, sorting, field selection, cursor pagination, and custom queries. | By-ID reads, nested filters, deterministic sorts, keyset cursors, and custom Java queries. GraphQL selection sets select response fields; there is no provider-level select API. |
| GraphQL | Generated mutations, custom queries, typed inputs/outputs, lists, and subscriptions. | GraphQL Java execution with generated record types, variables, fragments, validation, introspection, and live subscriptions. Non-record values can use JSON scalars. See wire differences below. |
| Event handlers | Typed/global handlers, notifications, and provider-specific dispatch records. | Typed handlers can emit buffered derived events; successful handler receipts are durable and pending handlers can be resumed. No notification/topic abstraction or separate global-handler API. External side effects need their own idempotency. |
| Scheduling | Cron-like calendar attributes and provider-triggered scheduled handlers. | Cancellable process-local fixed-interval command scheduling with explicit identity. No cron calendar, persisted schedules, or distributed scheduler. |
| Schema migrations | Consecutive migrations for commands, events, snapshots, and read models. | Event upcasters only. Consecutive versions are required; future versions, missing steps, and changes to stored entity IDs are rejected. Other serialized types have no automatic migration API. |
| Data migrations | Named durable jobs, entity renaming/ID migration, deletion, and reprojection. | Journal replay/rebuild only. No data-migration job engine, destructive event administration, or entity rename/ID migration. |
| Hooks and errors | Before hooks, global event handlers, and typed global error handlers. | Before-command, after-command, and error callbacks. No query/read-model before hooks or TypeScript error-class compatibility. |
| Providers and extensions | Runtime/infrastructure APIs, rockets, custom functions, and health sensors. | `EventStore` SPI, authentication SPI, and builder extensions; local HTTP health endpoint. No AWS/Azure provider or infrastructure lifecycle. |
| CLI | Domain/project generators, build, clean, start, synth, deploy, and nuke. | New Maven application generation, bundled demo/start, replay, help, and version. Maven handles build/clean/test. No individual domain generators or cloud synth/deploy/nuke commands. |

## Java transaction and delivery semantics

A command handler's failure leaves its buffered events uncommitted. A successful
journal commit atomically writes all emitted events and the command's result
receipt after checking every expected stream version. A repeated request ID
returns its recorded result only for the matching request fingerprint; a
different fingerprint conflicts. A conflicting concurrent write does not
automatically rerun arbitrary application code.

The fingerprint covers the operation, canonical input, subject, roles, claims,
and request metadata. Changing any of those while reusing a request ID conflicts;
this includes changed claims after a token refresh. Internal handler receipt IDs
use a reserved namespace which public command requests cannot claim.

Projection processing, event handlers, and after-command hooks happen after the
journal commit. Their failure cannot undo committed events. Retrying with the
same request ID or explicitly resuming handlers must preserve that distinction;
applications should not retry a committed command with a fresh identifier just
because post-commit processing failed. Reducers, projections, and upcasters must
be deterministic so that replay reconstructs the same state.

Handler receipts cover buffered events and completion records, not arbitrary
network calls made by application handlers. An external effect can happen before
a handler receipt is committed, so external systems must use their own
idempotency keys. Rebuilding read models does not itself repeat those external
effects. Use explicit stable names for durable event handlers. The shorthand
assigns names from event type and registration order; reordering handlers of the
same type changes their identity. After-command hooks are not durable jobs and
are not replayed on an idempotent command retry.

H2 file databases support one JVM at a time. Multiple store objects in that JVM
serialize transactions through a database row lock. Each runtime owns its own
in-memory projection cache; this is not a distributed projection service or a
persisted snapshot optimization. Read-model subscriptions are live,
process-local notifications with bounded buffers, not a durable replay log.

## GraphQL and authentication wire differences

- Operation names follow the registered Java classes. Commands receive an
  `input` argument; primitive record components are non-null GraphQL fields.
  Reference components are nullable unless application validation rejects them.
- Read-model operations include `<Model>(id: ID!)` and `List<PluralModel>` with
  `{items,count,cursor}`. Filters and sort inputs are JSON scalars; cursors are
  strings. TypeScript generates typed filter/sort inputs and uses JSON cursors.
- Pluralization uses simple English `s`, `es`, and `ies` rules, not TypeScript's
  full inflection library. Inspect the generated schema for irregular nouns.
- Lists use keyset pagination with a stable ID tie-breaker. A cursor is bound to
  its filter and sort, but it is not a database snapshot across concurrent
  changes. Limits are bounded from 1 to 1000. Java's `count` is the total matching
  result count; the TypeScript local provider reports the current page length.
- Structured filters support equality, scalar comparisons, membership, string
  operations, nested fields, and `and`/`or`/`not`. Regular expressions use RE2/J
  for bounded-time matching; backreferences and lookaround from other regex
  engines are unsupported. `isDefined` distinguishes an absent field from a
  field containing JSON null.
- HTTP queries/mutations use `POST /graphql`. Subscriptions use
  `POST /graphql/stream` and server-sent `next`, `complete`, and `error` events.
  There is no GraphQL WebSocket protocol implementation or durable resume token.
- By-ID subscriptions can deliver null when a projection is deleted. Filtered
  subscriptions emit matching nondeleted values; they do not emit removal
  notices when a value is deleted or stops matching the filter.
- HTTP binds to loopback by default. Authentication and resource authorization
  are separate. Anonymous mode rejects supplied credentials rather than silently
  accepting them. Identity and roles are not trusted from arbitrary HTTP headers.
- The built-in JWT verifier accepts only RS256 with an explicitly configured
  public key. It requires issuer, audience, subject, and expiration, uses no
  implicit clock-skew allowance, and reads roles from an array named `roles`.
  Subscriptions recheck authentication before delivery and at heartbeat intervals.
- Entity and event-history access inside trusted Java handlers is not a
  separately exposed or separately authorized GraphQL resource. A custom query
  exposing it must enforce the application's access rules.

## Compatibility boundaries

- Java classes and annotations replace TypeScript classes and decorators. There
  is no automatic TypeScript-to-Java source converter.
- The storage format is a Java-provider contract. Existing DynamoDB, Cosmos DB,
  or local TypeScript provider data requires an explicit migration tool; merely
  pointing the Java runtime at that data is unsupported.
- Generated GraphQL is a Java API contract. Matching an operation's purpose does
  not establish matching field names, nullability, pagination, error shape,
  custom scalars, or subscription transport. Clients must validate the generated
  schema and documented transport before migrating.
- AWS and Azure infrastructure generation, cloud deployment, remote event
  delivery, IAM, distributed subscriptions, and managed scheduling are not
  provided by a local provider or by a provider interface alone. Cloud runtime
  and deployment parity require separately implemented and validated providers.
- Existing npm rockets, including webhook, file-upload, static-site, and Docker
  rockets, cannot execute inside the Java runtime. Extension points permit new
  Java implementations; they do not automatically port those plugins.
- A local scheduler does not establish distributed exactly-once execution. A
  local stream or subscriber callback does not establish compatibility with the
  TypeScript GraphQL WebSocket protocol.
- Tests of in-memory or local-file persistence do not establish distributed
  consistency, durable delivery of side effects, or behavior under cloud
  provider outages.

## Migration acceptance checklist

Before migrating an existing application, validate its actual contracts:

1. Port the domain types, reducers, projections, command/query handlers, hooks,
   authorization rules, and migration functions.
2. Compare the generated GraphQL schema and exercise existing client operations,
   including subscriptions and error handling.
3. Define and test an explicit event-history import/export format. Keep original
   event IDs, ordering, schema versions, and correlation metadata.
4. Rebuild entity and read-model state from imported history and compare the
   results with the original application.
5. Exercise denied access, failed commands, repeated delivery, competing writes,
   process restart, replay, and schema upgrades using the selected provider.
6. Validate deployment, backups, secrets, observability, and operational recovery
   separately for the intended production platform.
