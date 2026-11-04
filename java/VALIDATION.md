# Validation record

Validated on 2026-11-04 with Oracle JDK **27.0.1.0.3** and Maven **3.10.0**.
The Java and Maven official archive checksums were verified. The committed JDK
bootstrap and Maven launcher were independently exercised with fresh tool caches.
TLS and Maven strict checksum verification remained enabled.

## Complete framework build

Executed the Maven `verify` and then `install` lifecycles in `java/`. The final
`install` run includes compilation, unit/acceptance tests, executable JAR
packaging, separate-process integration tests, and local artifact installation.
Result: **73 tests passed; 0 failures, 0 errors, 0 skipped**.

| Suite | Tests |
| --- | ---: |
| Transactional JDBC journal | 12 |
| Core commands, events, projections, auth, migration, scheduling and recovery | 14 |
| Structured filtering and cursor pagination | 5 |
| Runtime concurrency, identity and transaction regressions | 10 |
| HTTP/GraphQL and subscription security | 8 |
| JWT signature and claims validation | 10 |
| CLI and project generation | 9 |
| Shopping-cart domain and persistence | 4 |
| Executable JAR in separate JVMs | 1 |

The executable test starts the shaded JAR, makes real health and GraphQL
requests, repeats a mutation with the same idempotency key, terminates the JVM,
starts another JVM on the same H2 file, repeats the request again, checks the
restored projection and checks out the cart. Successful HTTP responses and the
expected persisted state are asserted; an open port alone is not the check.

The regression suite covers identity-scoped idempotency (including distinct
role sets with ambiguous display strings), denial before receipt replay,
projection failure without journal poisoning, stream/read-model identity
invariants, internal handler namespaces, concurrent runtimes, concurrent
identical requests and durable handler retry.

Subscription checks include nullable deletion events, authentication expiry and
termination when a custom authenticator removes the previously authorized role.
The JWT suite verifies valid signatures, wrong keys/algorithms, tampering,
issuer/audience, time constraints, malformed JSON and duplicate claims.

## Independently generated project

The final executable generated a fresh `dev.booster.sample` application outside
the framework checkout. Maven `verify` compiled its own application and ran its
domain test: **1 passed; 0 failures, errors or skipped**. It resolved the
framework from the locally installed Maven artifact, without workspace source
links or a dependency on the TypeScript implementation.

The generated project's documented `mvn exec:java` startup was also exercised
with an ephemeral port. Its health response and a real GraphQL mutation/query
were checked: adding three items at 500 cents produced a projection with
quantity 3 and total 1500 cents. The process was stopped after validation.

## Scope and reproducibility

Only `java/` changes relative to the TypeScript baseline. No original package,
source, lockfile, Rush setting or environment repository selection was changed.
All checks ran in the active environment after transferring the preserved
post-publication edits; earlier isolated-agent checks are not the final evidence.

The standard local command is `./mvnw install` with JDK 27. This managed
environment additionally supplied its Maven proxy settings and Java CA
truststore through options outside the checkout. Maven XML test reports are
generated under `target/surefire-reports` and `target/failsafe-reports`.

This validates the documented Java local edition. It does not establish AWS or
Azure deployment, TypeScript storage compatibility, distributed delivery,
WebSocket protocol compatibility or automatic JWKS rotation. See
[COMPATIBILITY.md](COMPATIBILITY.md) for the precise contract and boundaries.
