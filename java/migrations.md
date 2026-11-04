# Data migrations

Data migrations use the Java event journal and provider `StateStore`. Configure
both with durable implementations before running migrations on persistent data.
The local provider supplies `JdbcEventStore` and `JdbcStateStore`; Azure supplies
the corresponding Cosmos adapters. In-memory stores are useful for tests and
lose migration progress when the process stops.

Install the lifecycle types when configuring the application, then construct an
engine with stable names, order, parameters and an explicit execution identity:

```java
var builder = DataMigrations.install(Booster.builder(journal).stateStore(state));
// Register the application's entities, events, projections and operations.
var booster = builder.build();
var migration = new DataMigration("rename-accounts-v1", 100,
    Json.MAPPER.createObjectNode().put("batchSize", 100), context -> {
  // Work can use context.checkpoint() and context.parameters().
  context.migrateEntity("account:a", "Account", "a", new RenamedAccount("new-a", 25));
  context.touch("touch:new-a", RenamedAccount.class, "new-a");
  context.checkpoint(Json.MAPPER.createObjectNode().put("lastAccount", "a"));
  context.finish();
});
var engine = new DataMigrations(booster, List.of(migration), migrationIdentity);
engine.run();
```

The identity should remain stable across retries, because request receipts bind
the operation, payload, roles, claims and metadata. It is a trusted application
execution identity, never an identity copied from unverified HTTP headers.

## Lifecycle and ordering

The engine sorts definitions by order, preserving declaration order when priorities
are equal. As in the TypeScript baseline, the default orders callback starts:
later jobs can start after an earlier callback returns while its job remains
RUNNING. Names, parameters and ordering are recorded in a durable catalog;
existing definitions cannot be changed or removed. Add a new migration after the
existing catalog to change data again.

Applications with dependent jobs can opt into a completion barrier by constructing
the engine with `DataMigrations.Ordering.COMPLETION_ORDER` as its fourth argument.
In that mode, a later migration starts only after all earlier jobs have explicitly
finished. The selected policy is durable and must match on every worker.

`run()` starts new work, leaves existing RUNNING or FAILED jobs untouched, and
returns true if it started work or found unfinished work. It returns false when
every configured job was already finished. `resume()` explicitly retries each
unfinished job in start order; the optional completion-order policy stops at the
first callback that returns without finishing. Handler failures stop the current
run and record FAILED status, retaining successful earlier steps.

`BoosterDataMigrationStarted` records RUNNING status. Returning from a handler
does **not** mark it complete. `context.finish()` buffers a
`BoosterDataMigrationFinished` event in the current Register, or a handler can
emit that event directly to preserve the baseline contract. A failed handler
discards its unflushed completion event and records FAILED status. Failure
records do not erase successful earlier steps.

An asynchronous migration may return while RUNNING and later call
`engine.finish(name)`. Completion is idempotent. It requires the job to have
started and the execution lease to be available. A failed job must be resumed
before it can finish. Lifecycle events preserve the first start time, final end
time, elapsed milliseconds, attempt count and last failure across replay.

## Durable steps, checkpoints and recovery

Use `context.step(stableKey, input, (input, register) -> result)` for work that
must survive interruption. Its emitted events and result receipt commit in one
journal transaction. Repeating the same key and input replays the stored result
without invoking the step again; reusing the key with different input conflicts.
Step keys are scoped to the migration name, not the execution attempt.
Lifecycle and step receipts use the reserved framework request namespace, so a
public command cannot preclaim a migration's idempotency key.

After a step commits, the engine records its key in the progress document. If
the process stops between those writes, retrying the step still finds its
journal receipt and does not emit duplicate events. `checkpoint(value)` stores
application progress independently; write it **after** the corresponding work
has committed. Do not checkpoint past buffered events in `context.register()`.
Checkpoints cannot make arbitrary network calls atomic with the event journal.
External side effects must use the destination's idempotency mechanism.

`context.migrateEntity(stepKey, previousName, previousId, replacement)` uses the
core's atomic entity migration control events. The old event history remains
available; the old state is removed and the replacement is projected at its new
type and ID. Target collisions fail. Both entity types must be registered.
`context.touch(stepKey, entityType, id)` records a reprojection request without
rewriting historical events. Both helpers use durable step receipts, so retries
do not repeat a completed rename or touch.

A renewable compare-and-set execution lease excludes concurrent migration
runners. A lost or expired lease prevents further context operations and final
buffered completion. Checkpoint writes also check the captured execution attempt
on every compare-and-set retry, preventing an expired worker from overwriting a
new worker's progress. A new runner can recover after lease expiry and must use
`resume()` for existing incomplete jobs. The lease coordinates execution; it does
not make arbitrary user code or external calls exactly-once. Keep steps
idempotent and keep the same migration definitions on every worker.

`engine.status(name)` and `engine.statuses()` expose event-sourced lifecycle,
checkpoint and last completed step. Projections can also subscribe to the
registered lifecycle events. Inspect failures before choosing to resume.

## Validation

`DataMigrationsTest` exercises actual H2 reopen and replay, explicit and deferred
completion, ordered jobs, durable parameters and checkpoints, atomic step
deduplication, failure recovery, rename/ID migration and touch, competing workers,
expired-worker rejection and lifecycle duration. These local tests do not by
themselves establish live Azure deployment readiness.
