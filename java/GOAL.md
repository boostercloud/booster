# Goal: an independent Java edition of Booster

Build a usable Java implementation of Booster's event-sourced CQRS programming
model under `java/`, with no changes to the existing TypeScript implementation,
its dependencies, its lockfile, or its build. Use the current stable Java release
(Java 27, verified against the official Oracle distribution on 2026-11-04),
without preview features.

## Acceptance criteria

- A Java application defines commands, events, entity reducers, read-model
  projections and queries using typed Java records and handlers.
- Command failures do not persist their buffered events. Durable atomic commits,
  optimistic concurrency and request idempotency protect against lost writes
  and duplicate requests. State can be reconstructed after restart.
- Public and role-based authorization is explicit; protected operations deny
  unauthenticated callers. The HTTP boundary supports verified JWT identities.
- A real GraphQL engine exposes the registered application, validates requests,
  and exercises the command → journal → entity → projection → query workflow.
- An executable CLI runs a complete sample application and scaffolds an
  independent compilable Java project. The sample uses durable local storage.
- Automated tests cover domain behavior, persistence, concurrency, migrations,
  authorization, transport, restart/replay, and CLI behavior.
- The build and tests use a pinned, verified toolchain; documentation includes
  runnable commands and accurately describes compatibility with TypeScript.
- All implementation changes stay inside `java/`. Open a pull request with the
  actual validation results and any remaining limitations.

## Architecture decisions

Use Maven, Java records, explicit typed registration, Jackson for JSON,
GraphQL Java for standards-based query execution, a provider interface with
in-memory and transactional H2/JDBC implementations, and the JDK HTTP server.
The CLI and sample ship in an executable JAR. The local runtime has no Node.js,
Docker, cloud account, or external database prerequisite.

Java's type system and runtime APIs are the public contract. This is an
independent Java edition, with a compatibility matrix for behavior and wire
protocol differences. Existing TypeScript binaries, npm Rockets and provider
deployment code are not Java artifacts. Cloud provider implementations and
the deployment lifecycle must be described honestly in the compatibility matrix.

## Delivery evidence

The final test results and runtime checks are recorded in `VALIDATION.md`;
the pull request links the implementation and those results. Do not mark an
unexecuted or unsupported feature as verified.
