# Goal: complete Booster Java with Azure and the requested rockets

**Status: IMPLEMENTED AND LOCALLY VERIFIED — final closure is blocked on queued GitHub CI.**

Continue implementation in the existing [pull request #1607](https://github.com/boostercloud/booster/pull/1607),
branch `feat/booster-java`. All implementation, tests and documentation belong
under `java/`. Preserve the existing TypeScript implementation, its dependencies,
lockfiles and build. Use the latest stable Java release (currently Java 27,
verified against the official distribution), without silently downgrading it.

This is the persistent repository record of the user's goal. It is not a native
Codex goal and does not create or guarantee background task execution.

## Required outcome

Deliver a Java implementation covering Booster's existing core behavior, the
**Azure provider**, advanced projections, schema and data migrations,
subscriptions, authentication, existing API contracts, and the **webhook** and
**file-upload** rockets. Include a complete **local provider and local
infrastructure**, capable of running real applications and both rockets with
persistent state, lifecycle commands and end-to-end tests. A bundled demo alone
does not satisfy this goal.
Do not reduce this scope or substitute interfaces, mocks or documentation for
working implementations. Report blockers and continue independent work.

**AWS is explicitly excluded.** Docker-deployment and static-site rockets are
not requested. This does not prohibit using a Java container as an Azure runtime
if that is necessary to support the selected Java release.

**User scope update, 2026-11-04:** continue implementing Azure, but do not deploy
or execute tests against real Azure resources because the user does not yet have
access. Real Azure validation is deferred by explicit user instruction and does
not block completing the currently authorized work. Preserve it as an unverified
follow-up; do not claim Azure has been deployed or cloud-validated. Complete and
exercise the local infrastructure/provider instead, alongside Azure contract tests.

## Source baselines

- Booster TypeScript 4.1.0: `6bc5f816ca759db20a9aa2ee814341041ca2f95a`.
- Webhook rocket: `boostercloud/rocket-webhook`, `07aa8a806ff6ae5acbdb1ba06ebdc068f6650cbb`.
- File-upload rocket: `boostercloud/rocket-file-uploads`, `cc6d6463231846195bb2d5114c8d792003750f03`.

Inspect these implementations and their tests to establish behavior. Maintain
`COMPATIBILITY.md` as an actual-versus-required inventory. Java-native source
APIs may be idiomatic, but observable behavior and existing client contracts
must be implemented and verified; differences remain open goal items unless
explicitly accepted by the user.

## Acceptance checklist

### Core and provider contracts

- [x] Establish a typed Java local foundation: commands, events, reducers,
  simple projections, queries, transactional journal, CLI and real GraphQL.
- [x] Complete the remaining core/provider behavior used by the TypeScript
  baseline, including contextual registration/flush, event handling, notifications,
  hooks, errors, health and extension functions; inventory each item explicitly.
- [x] Preserve ordering, optimistic concurrency, idempotency, authorization,
  correlation propagation and recovery across provider implementations.

### Azure provider — required, AWS excluded

- [x] Implement actual Azure runtime adapters for events, snapshots, read models,
  searching, dispatch/deduplication, subscriptions/connections, scheduling and
  the provider's event-stream interfaces.
- [x] Implement Azure infrastructure and CLI lifecycle: configuration,
  synthesis, deployment/update, startup/health and intentional teardown.
- [x] Cover the baseline's Cosmos DB, Azure Functions/hosting, Event Hubs,
  Web PubSub, Storage, managed identity and role-assignment responsibilities.
  Select and document a hosting option that really supports Java 27.
- [x] Preserve Cosmos change-feed/lease processing, Event Hubs producer/consumer
  flow, HTTP/timer/change-feed triggers, the Web PubSub `booster` hub and durable
  subscription/connection records required by the existing Azure provider.
- [x] Verify service serialization, partition/concurrency semantics, retries,
  pagination, failures and restarts using contract tests. The optional Cosmos emulator could not be downloaded
  because the managed proxy blocks its official CDN; emulator results are unverified.
- [ ] DEFERRED BY USER: Run an isolated Azure deployment with real command/event/projection/query,
  subscription and rocket checks, then verify cleanup of created resources.
  Do not run this until separately authorized when access becomes available.
  This is a future validation follow-up, not a blocker for the updated scope;
  mocks do not prove cloud readiness.

### Local provider and infrastructure — required

- [x] Provide a reusable local provider with durable events, snapshots/read models,
  migration state, scheduling, subscriptions, filesystem uploads and webhook routing.
- [x] Supply application configuration and infrastructure lifecycle commands to
  synthesize, start/stop, inspect health, rebuild and intentionally clean up local
  applications, beyond the bundled demo; retain data across ordinary restarts.
- [x] Make generated applications use the complete local provider and document
  reproducible build/start/configuration/cleanup without a cloud account.
- [x] Test an actual local application in separate processes, including restart,
  concurrent commands, projections/joins, migrations, authenticated WebSocket
  subscriptions and both rockets with real HTTP and file transfers.

### Advanced projections and read models

- [x] Implement joined projections across entities, join keys and filter joins,
  fan-out, unprojection, deletion and no-change outcomes.
- [x] Implement persisted read-model versions, concurrency handling/retries,
  sequence/composite-key behavior and deterministic rebuild/reprojection.
- [x] Test duplicate/out-of-order delivery, concurrent updates, joins changing
  membership, deletion, pagination and restart consistency.

### Schema and data migrations

- [x] Complete schema migrations for commands, events, entities/snapshots and
  read models, with consecutive versions, invalid-gap/future-version rejection
  and preserved historical data.
- [x] Implement the data-migration mechanism: named jobs, durable progress,
  completion/failure records, resumability and idempotency.
- [x] Support baseline entity rename/ID migration, touch/reprojection and
  migration parameters through real provider operations.
- [x] Test interrupted and retried migrations against populated persistent data,
  ensuring no event loss or duplicate side effects.

### Existing APIs, authentication and subscriptions

- [x] Inventory and implement the existing GraphQL operation names, generated
  input/output/filter/sort types, nullability, selection behavior, cursor/list
  semantics, schema versions and error behavior.
- [x] Implement the baseline WebSocket subscription protocol and lifecycle,
  including connection initialization, authentication, start/stop, cleanup,
  expiration, by-ID and filtered subscriptions, and Azure delivery.
  The current POST SSE transport alone is insufficient.
- [x] Verify the baseline legacy `graphql-ws` message contract:
  `connection_init`, `start`, `stop`, `connection_terminate`, acknowledgements,
  data, errors and completion. Do not substitute a different protocol merely
  because its name is similar.
- [x] Complete JWT/public-key/JWKS verification, key rotation, issuer/audience,
  token timing, configured role claims and baseline authorizer/hook behavior.
- [x] Verify compatibility with representative existing clients and recorded
  TypeScript contract fixtures, including rejection and authorization cases.

### Webhook rocket — Java local and Azure

- [x] Port the rocket's configuration, routes, methods, request metadata/body,
  authentication/authorization, handler/event dispatch and response behavior.
- [x] Implement the baseline JSON, text, binary and multipart behavior and
  request/response bounds without forwarding work to Node.js.
- [x] Integrate real local and Azure routing/infrastructure; test valid requests,
  malformed/unauthorized input, duplicate delivery and handler failures.

### File-upload rocket — Java local and Azure

- [x] Port upload/download URL generation, authorization, file listing,
  metadata/key handling and the rocket's public command/query contracts.
- [x] Implement real local file storage and Azure Blob Storage/SAS behavior,
  upload notifications, configured handlers and file/entity/event propagation.
- [x] Provide corresponding Azure infrastructure, storage and role permissions.
- [x] Test upload, download, listing, expiration, access rejection, unsafe paths,
  duplicate notifications, handler failures and persistence across restart.

### Validation and delivery

- [x] Expand Java unit, provider-contract, security and integration tests to cover
  every required subsystem; add compatibility fixtures from the baseline.
- [x] Exercise end-to-end applications locally, including both
  rockets, advanced projections, migrations and subscription/authentication flows.
  Azure end-to-end execution is deferred by the user's scope update above.
- [x] Keep `VALIDATION.md` current with executed commands and distinct passed,
  failed, skipped, blocked and unrun outcomes. Passing local tests are not evidence
  that unimplemented provider/API functionality works.
- [x] Keep README, compatibility inventory and reproducible build/start/deploy
  instructions aligned with the implemented behavior.
- [ ] Resolve relevant PR checks and keep all changes in PR #1607. Do not merge
  without a separate instruction. Keep the PR draft while required work remains.
- [ ] Mark this goal complete only when every currently required item is implemented
  and validated. Keep explicitly deferred Azure live validation separate and visible;
  remaining implementation gaps must not be relabeled as done.

## Implementation and verification record

The required Java core, full local provider/infrastructure, Azure SDK adapters
and ARM lifecycle, advanced projections, schema/data migrations, APIs,
authentication/subscriptions and both rockets are implemented. The compatibility
inventory records the exact contracts and operational limits.

The final combined clean Maven install passed 277 unit/contract tests and four
separate-process integration tests (281 total), with no failures, errors or skips.
This includes the final schema deletion/recovery and GraphQL compatibility fixes. A separately generated
Maven application also passed its test and real HTTP/GraphQL requests, including
ECMAScript regular-expression filtering.

Implementation and validation are committed at
`86d06e5a869ea6947f824107e8c1276ddade6491` in PR #1607. Its repository checks
were created at 17:30 UTC on 2026-11-04 and remained queued without an assigned
runner during the delivery observation window. This is not a test failure or
an implementation gap. The PR remains a draft; do not mark the two final delivery
items complete until the checks have actually run. Resume by checking the latest
PR head's CI results, investigating any relevant failures, then closing this
checklist and marking the PR ready for review. Do not merge. No additional Java
build is needed unless source changes or a failure warrants it.

Evidence and follow-ups are maintained in [VALIDATION.md](VALIDATION.md).
Real Azure deployment is deferred by the user's explicit instruction; no live
Azure success is claimed. Only `java/` belongs to this implementation.

## Concrete baseline contracts to preserve

- Data-migration jobs have ordered execution, `RUNNING`/`FINISHED` state,
  duration and lifecycle events. Returning from `start` does not itself mean
  completion: preserve the explicit `BoosterDataMigrationFinished` mechanism.
  Include `migrateEntity` with prior name/ID and replacement entity, and touch.
- Match configured authentication claims, including the TypeScript default
  `custom:role`; the foundation's fixed `roles` array alone is insufficient.
- Azure Web PubSub uses hub `booster`, `system/connect`, `user/message` and
  `system/disconnect`, with Cosmos-driven notifications and persisted TTL state.
- Webhooks use `/webhook/<route>`, nested routes, GET/POST, `route` and legacy
  `origin`, headers/query/params/rawBody/JSON/multipart, and text/JSON/binary
  responses with headers. Preserve Booster authorization and Register events.
- File uploads expose `presignedPut`, `presignedGet` and `list`, support multiple
  configured stores/containers/directories and defaults, and use Azure identity
  plus user-delegation SAS rather than embedding account credentials.
  Verify SAS permissions/expiry, metadata, listing and actual transfers.
  Blob triggers propagate `UploadedFileEvent` → `UploadedFileEntity` → projections,
  respecting container/directory/glob selection and duplicate notifications.
  Provision the required storage permissions, including the baseline's
  `Storage Blob Data Contributor` responsibilities.

## Resume and blocker protocol

1. Read this file, `COMPATIBILITY.md`, `VALIDATION.md`, current branch/status and
   PR state before resuming. Preserve user changes and use the existing checkout.
2. Confirm that tools and all agents use the active environment; a publication
   previously left agents on an older container. Transfer and verify work before
   relying on another agent's results.
3. Work through unchecked acceptance items, recording implementation and test
   evidence. Do not silently remove requirements or announce the goal completed
   because a smaller milestone passes.
4. Inspect configured identities, variable presence and network policy before
   requesting Azure access. Never ask for credential values in chat.
5. Report the precise failing operation and required external action promptly;
   continue useful independent work. Do not invent successful cloud validation.
6. Commit and push recoverable checkpoints on `feat/booster-java`; update the
   same PR. A file, commit or PR is not a native Codex goal or a guarantee that
   the task keeps running when the client closes.
