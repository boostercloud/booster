# Core runtime, projections and durable recovery

This document describes the Java core APIs. The complete port's acceptance
criteria remain in [GOAL.md](GOAL.md); local core tests do not establish Azure
deployment readiness.

## Providers and concurrency

`Booster.builder(eventStore).stateStore(stateStore)` combines the authoritative
journal with a provider CAS document store. Implementations include
`InMemoryEventStore`/`InMemoryStateStore`, `JdbcEventStore`/`JdbcStateStore`, and
the Azure Cosmos adapters. The JDBC stores use synchronous H2 writes and can
share a database among instances in one JVM. A file database is not a
distributed database.

Every runtime commit atomically checks both the streams read by its `Register`
and the global journal position used to validate reducers and projections.
Unrelated concurrent writes can trigger up to 32 pure validation retries;
application command handlers are not automatically rerun. This global fence
also protects joined projections and prospective event-deletion replay.
Third-party `EventStore` implementations must implement `commitAtPosition`
atomically, in addition to ordinary `commit`.

Snapshots and read models are persisted in `StateStore` namespaces
`snapshots:<Entity>` and `readModels:<Model>`. Their values include journal
`position`, `schemaVersion` and `state`. A null `state` represents deletion.
Read-model envelopes additionally retain logical `id`, and sequence metadata
when configured, so provider notifications preserve deletion identity. CAS
revisions never move backwards, and an older journal position cannot overwrite
newer derived state. State documents retain tombstones to prevent ABA races.

Startup rebuilds the in-memory state from the journal. Independently persisted
rows are not treated as an atomic replay checkpoint. This trades startup cost
for correctness when a process fails between derived writes.

## Joined and sequenced projections

The existing `.readModel(Model.class, Entity.class, policy, projection)` remains
available for one-source, same-ID projections. A joined model is declared once
and can register multiple source projections:

```java
builder.readModel(GroupView.class, AccessPolicy.publicAccess())
    .projection(GroupView.class, Group.class, Join.key(Group::id), context ->
        ProjectionResult.store(projectGroup(context)))
    .projection(GroupView.class, Member.class, Join.keys(Member::groupIds),
        context -> ProjectionResult.store(addMember(context)),
        context -> ProjectionResult.store(removeMember(context)));
```

`Join.key`, `Join.keys` and `Join.filter` select logical read-model IDs from an
entity key, a fan-out collection, or a structured filter over existing models.
`ProjectionContext` provides the selected ID, current and previous entity,
previous model, and whether membership was removed. Explicit
`ProjectionResult.store`, `delete`, and `nothing` outcomes distinguish writes,
removal and no-op behavior. Missing unprojection callbacks default to `nothing`.
Callbacks must be deterministic and free of external effects: validation,
concurrent retries and replay can evaluate them repeatedly.

`.sequenceKey(Model.class, "revision")` stores separate rows for a logical ID
and a scalar sequence value. The source entity and model expose the configured
field. Numeric values such as `1` and `1.0` share one storage identity. By-ID Java
lookup rejects ambiguous results; use
`getReadModel(name, id, sequence, context)` to select one. List pagination uses
the full storage identity as its tie-breaker. Sequence subscription filtering
also works for deleted rows. `Join.composite(...)` is available for explicit
composite IDs and preserves component boundaries and types.

## Schema evolution

Events retain the version declared during event registration. Other serialized
types declare `.schemaVersion(Type.class, currentVersion)`. Register consecutive
`.upcaster(Type.class, fromVersion, migration)` functions for every older
version. `executeCommand(name, input, sourceVersion, context)` migrates an older
command before dispatch; `migrateSchema` exposes the same validated mechanism.

Startup automatically migrates persisted entity/read-model cache documents
using their recorded schema version while retaining their source position.
Before updating a cache document, it commits an idempotent schema-boundary
record containing the migrated JSON and its source position to the journal.
Replay applies that recorded value at the original boundary, so entity reads,
read-model reads, subsequent events and a manual rebuild all observe the
migration. A crash between the journal write and cache update can be resumed.
Future versions, migration gaps, entity-ID changes and sequence-key changes are
rejected. Renaming identities requires the explicit data-migration operations,
not a schema upcaster. A shared schema registry rejects an older application
instance after a newer schema has been published, including an instance that
was already running; old workers cannot overwrite upgraded cache documents.
The event journal remains authoritative after cache migration.

Historical deletion invalidates affected schema boundaries, just as the
TypeScript deletion path discards affected snapshots. An entity boundary is
invalidated when a later deletion marker removes an event at or before its
source position in the same stream. A read-model boundary is invalidated when
such a deletion belongs to one of its projection source entity types. Artifacts
rebuilt after the deletion can be migrated again; that prior deletion does not
invalidate their new boundaries. Those affected artifacts are reconstructed
using the current reducers/projections; an old-shape snapshot upcaster is not
run against an already-current Java object. Unrelated entity streams and models
with independent source types retain their boundaries. This prevents a stored
migration snapshot from restoring values contributed by deleted history.
Invalidated boundary payloads and their owning receipt copies are also redacted;
only the replay descriptor remains, without its stored state. This is logical
redaction of active records, not physical erasure of database backups or logs.
Explicit `migrateEntity` control events remain domain state transformations,
distinct from schema evolution of derived artifacts.

See [migrations.md](migrations.md) for ordered, leased, resumable data jobs and
explicit lifecycle completion. `Register.migrateEntity(oldType, oldId, entity)`,
`touch(type, id)` and `deleteEntity(type, id)` append durable control records
without rewriting historical domain events. Migration destinations must be
absent unless the original and replacement identity are the same.

## Actions, notifications and partial flushes

`executeAction(name, input, context, policy, handler)` gives rockets and provider
functions an authorized, idempotent unit of work without exposing an additional
GraphQL command. `executeSystemAction` is reserved for trusted orchestration and
requires a request ID beginning with `__booster_`. Ordinary commands and actions
reject that namespace, preventing clients from preclaiming scheduler or migration
receipts. Actions do not run command-only hooks or command error suppression;
system job failures must remain visible to their orchestrator.

Named raw provider functions use `.function(name, policy, RocketFunction)` and
`executeFunction(name, rawRequest, context)`. The function receives the Booster
runtime, the original provider object and request context, and can return a raw
provider response or binary value. It is not automatically serialized into a
command receipt; functions invoke `executeAction` explicitly when their work
requires a Register. Names are unique, missing functions fail, and the shorthand
registration denies access unless an explicit policy is supplied. HTTP adapters
can dispatch the registered function through their normal route mounting API.

`.notification(Type.class)` registers an event without an entity reducer. It
uses topic `defaultTopic` and the notification's textual `partitionId`, or
partition `default` when absent. The overload accepts an explicit topic,
partition function and schema version. Notification topics cannot collide with
entity names. Typed `.eventHandler(stableName, Type.class, handler)` and named
`.globalEventHandler(stableName, handler)` both use durable completion receipts.
Reordering named handlers does not change their identity.

`Register.flush()` persists the current buffer and clears it only after success.
A later failure cannot roll back flushed events. Each flush receipt records the
pre-batch entity states and versions. A retry reconstructs that historical
prefix for `Register.entity` reads and reducers, then skips already committed
batches. This supports guards and non-idempotent reducers such as withdrawals;
it does not charge the account twice simply because a handler is retried.
After the committed prefix, normal reads resume against current state.
`Register.currentEntity` provides a live fencing read for orchestration state,
preserving pending writes while checking their original version. External
effects and reads outside the Register still require application-level retry
and idempotency design.

## Authorization, context, hooks and errors

Policies deny by default. `AccessPolicy.request` and `AccessPolicy.input`
support authenticated request context and payload-aware decisions in addition
to public, authenticated and role policies. Authorization precedes receipt
replay. The fingerprint covers the operation, canonical input, identity,
claims, roles, metadata and hashes of stable request headers/body. Credentials
and volatile transport headers are excluded where identity already covers
them; raw request context is never copied into durable event metadata.

`RequestData` carries immutable request headers/body and a provider raw context
for live handlers. It is excluded from JSON serialization of `RequestContext`.
`withRequestId` preserves this ephemeral context and a shared `ResponseHeaders`
bag across GraphQL fields. Both `Register` and `QueryContext` expose
`responseHeader`; header names and control characters are validated, and the
HTTP transport owns response framing. Arbitrary response headers are not part
of the core durable command result; a rocket can include its response headers
in its own returned result, as the webhook rocket does.

Named `beforeCommand`, `beforeQuery` and `beforeReadModel` hooks transform
isolated JSON inputs in registration order. Read-model hooks also apply to
provider-delivered subscriptions through the same subscription filtering path.
The original input remains the idempotency identity. The legacy Register-based
before-command callback remains supported.

Typed `onError(FrameworkError.Stage, handler)` and `globalErrorHandler` can
replace errors or explicitly suppress them at implemented command, query,
event-handler, projection and derived-persistence boundaries. Suppressing a
command/handler failure permits its buffered events to commit, matching the
baseline's explicit policy. A reducer cannot invent a valid next state from a
suppressed error and still fails. The existing `onError(Consumer<Throwable>)`
remains an observation callback and cannot change commit success.

The `EVENT` boundary handles an unregistered historical event type: the default
is failure, replacement errors are preserved, and explicit suppression skips the
invalid event without breaking stream positions or invoking handlers with an
untyped object. Schema gaps and future versions remain invalid. Scheduled
handlers use `executeScheduledAction` and their separate `SCHEDULE` boundary.
Framework lease/commit guards run outside that suppressible boundary, before
the handler and before each commit attempt, including explicit flushes. An
application's error policy cannot convert a lost lease into a successful receipt.

## History and event administration

History search is separately authorized by
`.eventHistoryAccess(Entity.class, policy)` and defaults to denial. Searches
select exactly one entity or event type, support entity ID and inclusive time
bounds, and return the newest events first with a deterministic position
tie-breaker. `entitiesIDs` returns sorted, distinct IDs with an opaque cursor.
Framework control records and deleted events are excluded.

`.eventAdministrationAccess(Entity.class, policy)` separately authorizes
`deleteEvent(entityType, entityId, createdAt, context)`. Deletion validates a
prospective replay, commits a durable marker with a whole-journal position
fence, clears the event payload and receipt event copies, then rebuilds the
affected state. An incompatible deletion is rejected before the marker is
written. Other runtimes discover the marker during catch-up; startup retries
unfinished payload redactions. Journal positions and stream versions remain
consecutive. Deleting an event does not undo external handler effects or erase
independent application response values recorded in command receipts.

The provider `redactEventPayload` operation must preserve the envelope and
position while clearing the payload and receipt event copies atomically. It is
invoked only after the durable deletion marker is present. It is distinct from
physically removing a journal row, which would break replay ordering.
