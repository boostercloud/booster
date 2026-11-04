# Validation record

Status: implementation checkpoint before complete integrated verification.

Toolchain verified on 2026-11-04: Oracle JDK 27.0.1.0.3 and Maven 3.10.0.
Official archive checksums were verified. The committed JDK bootstrap and Maven
launcher were also exercised using fresh tool caches. Dependencies were fetched
with TLS verification and Maven strict checksum verification.

Completed checks before this checkpoint:

| Check | Result |
| --- | --- |
| JDBC store (isolated Maven suite) | 12 tests passed; no failures/errors/skips |
| GraphQL/HTTP and JWT focused Maven suite | 16 tests passed; no failures/errors/skips |
| CLI and example focused Maven suite | 13 tests passed; no failures/errors/skips |
| Generated application sources | Compiled by the CLI test suite |
| Runtime acceptance regressions (isolated suite) | 6 passed, 2 failed: entity/read-model output ID consistency remains to fix |

The JDBC suite covers file reopening, multistream atomic commits, optimistic
concurrency, duplicate requests, rollback, ordering and schema compatibility.
Transport checks cover real GraphQL requests, authorization, subscriptions and
JWT signature/claim validation. The CLI/example checks cover domain behavior,
restart/idempotency and scaffold safety.

Pending before delivery:

- Finish core behavioral tests and acceptance regression tests.
- Correct the new filter tests' Java string syntax and enforce entity/read-model ID invariants.
- Run the full Maven `verify` lifecycle against the final shared source tree.
- Run the shaded JAR's separate-process smoke test, including database restart.
- Install the library locally and run Maven verification in a generated project.
- Review compatibility documentation against final implementation and test results.
- Commit the final result, push the branch, and open the requested pull request.

Individual focused successes are not a substitute for the pending full build.
The GitHub API step is currently blocked by environment network policy; the
configuration draft includes `api.github.com` for the user to activate.
