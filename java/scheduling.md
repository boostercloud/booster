# Persistent calendar scheduling

`PersistentScheduler` executes named Java scheduled tasks with durable cursors
and renewable leases in the configured `StateStore`. The corresponding event
journal stores each nominal occurrence's idempotency receipt. Use the JDBC
provider locally or the Cosmos state/journal adapters on Azure; an in-memory
store only gives process-local durability.

```java
var schedule = CalendarSchedule.fromAzureAttributes(Map.of(
    "minute", "*/15", "hour", "9-17", "weekDay", "MON-FRI"));
var task = new ScheduledTask("RefreshOrders", schedule, Json.MAPPER.createObjectNode(),
    serviceIdentity, (occurrence, register) -> {
      register.emit(new RefreshRequested("orders", occurrence.scheduledAt()));
    });
var scheduler = new PersistentScheduler(booster, List.of(task));
scheduler.start(Duration.ofSeconds(1), 100);
```

Local and Azure applications expose these definitions through `schedules()`;
their host owns scheduler startup and shutdown. A standalone owner must close
the scheduler before the Booster runtime and stores. Scheduled handlers are
trusted application callbacks with an explicitly configured identity; they do
not obtain an identity from caller-supplied HTTP headers.

The source contract is Booster 4.1.0's `ScheduleInterface` and Azure function
generator. `fromAzureAttributes` accepts `minute`, `hour`, `day`, `month`,
`weekDay` and `year`. It generates the same six-field Azure NCRONTAB expression:
seconds are zero, unspecified fields are `*`, and **Azure ignores `year`**.
An empty attributes map disables the schedule, as the Azure generator does.
The explicit six-argument Java constructor additionally supports a year field.
Calendars use UTC, support lists/ranges/steps and full or abbreviated month/weekday
names, and require both day-of-month and weekday to match. Sunday is zero.
Invalid expressions fail during configuration.

Initial registration begins at the next matching minute, including the current
minute only when registration occurs at its exact boundary. Restart reads the
existing cursor and catches up missed occurrences in nominal order per schedule.
`runDue(maxOccurrences)` bounds a polling batch and reports independent schedule
failures. A failed callback leaves that occurrence due and rolls back its
unflushed events; other schedules continue. Bounded batches rotate their starting
schedule to avoid starving later names.

The core's `FrameworkError.Stage.SCHEDULE` policy can explicitly replace an
application exception or suppress it and commit the callback's buffered events.
Command hooks/error policies are separate. Lease ownership is checked outside
that suppressible application boundary before commit, so suppressing an
application exception cannot turn a lost lease into a completed occurrence.

`dispatch(name, scheduledAt)` handles an explicit timer trigger. The timestamp
must match the calendar and cannot be in the future. It uses the same receipt as
background polling, so duplicate triggers/restarts do not duplicate committed
events. A manual out-of-order trigger does not advance past earlier pending
occurrences. Its already committed result is replayed when polling later reaches
that timestamp.

If a process dies after committing events but before advancing the cursor, the
next process replays the receipt and advances the cursor without invoking the
callback again. Leases renew while callbacks run. After lease expiry, another
replica can take over; stale buffered work cannot produce a second event batch
for the same occurrence. An external call made by a callback can still happen
more than once after a crash/lease expiry, so external side effects must use the
nominal occurrence as their own idempotency key. Explicit register flushes retain
the core's partial-commit semantics.

Schedule names, calendars, inputs and execution identities form a durable
definition. An incompatible definition under the same name requires an explicit
generation update. Callback implementation changes alone do not reset its
cursor. `status(name)`/`inspect(booster, name)` expose the current generation,
next/last occurrence, attempts, completed cursor advances, failure and lease.

For a deployment update, stop/drain the old worker, then explicitly reconfigure
before constructing the new scheduler:

```java
PersistentScheduler.reconfigure(booster, replacementTask, "1", "release-2",
    PersistentScheduler.CursorPolicy.AFTER_LAST_COMPLETED);
```

The instance form is `scheduler.reconfigure(replacementTask, "release-2", policy)`.
The initial generation is `1`. `AFTER_LAST_COMPLETED` applies the new calendar to
the outstanding time range; `FROM_NOW` intentionally retires the old backlog and
begins strictly after the update's current time. Configuration, generation and
cursor change in a single compare-and-set. Repeating the same update is
idempotent and does not reset the cursor. Stale generations cannot replace a
newer definition, and retired generation names cannot be reused. The catalog
retains up to 1024 generation identities per stable schedule name. A later update
cannot move the cursor back across a time range deliberately retired by an
earlier `FROM_NOW` update.

An update rejects a leased schedule, including an unrecovered expired owner.
Recover it with the old worker definition before deploying the update. If an
old occurrence committed before its cursor write failed, reconfiguration checks
that journal receipt and advances from the actual completion. Receipt identities
include the generation, and `Occurrence.generation()` is available for external
idempotency keys. Late timer messages before the new generation's time boundary
are rejected. Old replicas are fenced until restarted with the new definition.

`CalendarScheduleTest` and `PersistentSchedulerTest` cover calendar contracts,
JDBC restart/backlog, competing replicas, failed handlers, a failed cursor write
after event commit, explicit timer replay, lease takeover, hosted polling,
fairness and durable generation updates. See the root validation record for executed results; these tests do
not replace real Azure deployment/restart validation.
