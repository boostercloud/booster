# Validation record

Validated on 2026-11-04 with Oracle JDK **27.0.1.0.3** and Maven **3.10.0**.
Official archive checksums, TLS verification and Maven strict checksums remained
enabled. Only `java/` changes relative to the TypeScript baseline.

## Combined framework build

The latest combined `clean install` passed **277 unit/contract tests and four
integration tests (281 total), with zero failures, errors or skips**. This includes
the final GraphQL nullability and issuer-only JWT history regressions.
The lifecycle compiles all sources, packages normal and executable JARs, runs
separate-process integration tests and installs the artifacts locally.

Reproduce with JDK 27:

```sh
cd java
./mvnw -B -C clean install
```

This managed environment used the same Maven lifecycle with its proxy settings
and CA truststore supplied outside the repository:

```sh
source /workspace/.java-tools/activate.sh
mvn -s /workspace/.java-tools/settings.xml -B -C clean install
```

JUnit XML evidence is under `target/surefire-reports` and
`target/failsafe-reports`. The environment build log is
`/workspace/.java-tools/goal-final-install.log`.

Coverage includes:

- Atomic in-memory/JDBC/Cosmos provider contracts, optimistic concurrency,
  identity-scoped receipts, durable partial flush, recovery and deletion.
- Joined, filtered, fan-out and sequence projections; persisted versions,
  schema upcasters, rebuild/restart and logical deletion of copied state.
- Ordered durable data migrations, checkpoints, explicit completion, lease
  fencing and interrupted recovery; health component authorization and bounds.
- Real local HTTP, legacy `graphql-ws` and JWKS servers; typed GraphQL schemas,
  filters/cursors/history, JWT issuer/role/timing/key rotation and expiry.
- Persistent calendar schedules, duplicate dispatch, competing owners,
  generation changes, retries, restart and shutdown races.
- Real webhook JSON/text/binary/multipart requests and durable response receipts;
  real signed local file PUT/GET/list, unsafe-path rejection, duplicate upload
  notifications, handler failures and restart persistence.
- Azure SDK request serialization, transactions, CAS, pagination, retries,
  subscription identity/TTL, callback validation, Blob SAS and ARM synthesis/
  lifecycle against controlled local responses. These are contract tests.
- CLI generation and lifecycle ownership, real child-process start/stop,
  failed-start cleanup, classloader handling, rebuild and intentional nuke.
- Recorded baseline dependency fixtures: 70 pluralization cases, 100 directory
  patterns and 45 ECMAScript regular-expression cases. Runtime/tests execute Java;
  Node.js was used only to capture baseline oracle data.

The four integration suites exercise the executable JAR across process restarts,
the packaged ECMAScript engine in a separate JVM, persistent projection/migration
recovery, and a complete application in two successive JVMs. That full local
application uses signed JWTs, WebSocket subscriptions, multi-entity joins,
webhooks, signed file transfers, interrupted migrations and verified cleanup.

## Independently generated application

A fresh `dev.booster.verified` project outside the framework checkout resolved
the installed normal Maven artifact, compiled and passed its own test:
**one passed, zero failures/errors/skips**. Its documented `mvn exec:java`
startup was exercised on an ephemeral local port with health and GraphQL requests.
Adding three items at 500 cents produced quantity three and total 1500. A
lookbehind regular-expression filter selected that row, exercising the engine
through the application's Maven classloader. Authenticated shutdown completed
and the Maven process exited successfully.

The first manual request mistakenly used the bundled demo's `OpenCart` operation,
which the generated sample does not declare. GraphQL correctly rejected it.
Validation then used the generated project's actual `AddItem` contract and passed.
No framework change was needed for that request error.

## Container execution

The Java 27 Docker image built with checksum/TLS verification and ran as UID
10001. The initial check found unreadable JAR permissions for that user; the
Dockerfile now explicitly sets artifact ownership and mode. The final rebuilt image
passed real local health, commands, projection queries and an ECMAScript
lookbehind filter using the final shaded JAR. It reported Java 27.0.1.0.3, ran as
UID 10001 and stopped via SIGTERM (exit 143); the test container was then removed.
No image was published. The final local image digest is
`sha256:20ea8805cf64334f4ad4896a0978d7f4bde351ddcfbbab98147b9c5f10358f1a`;
evidence is retained in `/workspace/.java-tools/final-docker-evidence.json`.

## Deferred and blocked checks

**Real Azure deployment and tests were not run**, by the user's explicit
2026-11-04 instruction. ARM acceptance, managed-identity/RBAC propagation, real
Cosmos/Event Hubs/Web PubSub behavior, SAS transfers, cloud restart and resource
cleanup remain a future exercise when access is available. Passing SDK fixtures
are not evidence of deployed-cloud readiness.

An additional local Cosmos emulator check could not start. The official image
`mcr.microsoft.com/cosmosdb/linux/azure-cosmos-emulator:vnext-latest` redirects
layer downloads to `centralus.data.mcr.microsoft.com`; the managed proxy returned
HTTP 403 because that destination is outside its network policy. No emulator
container was created and no emulator test passed. Evidence is retained in
`/workspace/.java-tools/cosmos-emulator-attempt.json`. This optional check remains
unrun; the complete local provider tests use actual H2 and filesystem storage.

AWS is excluded from the requested implementation. No merge, environment
publication, repository-selection change or deployment is part of this work.

## Interpretation and reproducibility

Earlier intermediate runs exposed and led to fixes for pluralization, SDK query
encoding/proxy handling, scheduler polling, union serialization, migration
replay/deletion and lifecycle races. Those earlier failures are superseded by
the passing combined build; they are not omitted tests. Maven Shade reports
upstream duplicate license/Netty metadata resources; packaged service loading
and runtime execution are covered by the JAR/container checks.

The normal library and executable use an independent Java storage format and
source API. This validation does not demonstrate automatic conversion of an
existing TypeScript database or application. Operational limits, source/API
mapping and explicit compatibility profiles are documented in
[COMPATIBILITY.md](COMPATIBILITY.md), [local.md](local.md) and [azure.md](azure.md).
