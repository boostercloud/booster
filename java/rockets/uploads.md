# File-upload rocket

`FileUploadRocket` ports the existing rocket's `FileHandler.presignedPut`,
`presignedGet` and `list` methods. The TypeScript rocket does not itself install
global GraphQL command/query names; applications expose those operations through
their own authorized handlers. Java follows that contract.

Configure one or more `StorageConfiguration(storageName, containerName,
directories)` entries and a map of storage names to `FileStorage` providers.
Storage lookup is case-insensitive; omitting the name is allowed only for a
single configured store. Directory matching supports `*`, `**`, `?`, character classes, brace alternatives
and ranges, extglobs, leading negation and escapes. Matching uses memoized
character-position sets instead of backtracking regular expressions. Fixtures
include 36 TypeScript directory cases and 64 recorded results from the exact
baseline minimatch 3.1.5 dependency. Pattern length, nesting and expansion are bounded.

Construct `FileUploadRocket(configurations, providers, uploadPolicy, readPolicy)`
with explicit policies. Call `rocket.install(builder)` before building Booster;
this registers `UploadedFileEvent` and its `UploadedFileEntity` reducer. Register
application projections and named event handlers using those types. Inside an
application command/query, obtain `rocket.handler(storageName, requestContext)`
and call `presignedPut(directory, filename)`, `presignedGet(directory, filename)`
or `list(directory)`. URL issuance and listing enforce the appropriate policy.
Signed URLs are bearer capabilities; possession grants only their specified
operation and path until expiry.

## Local provider

`LocalFileStorage(dataDirectory, origin, signingKey, configurations, notificationConsumer)`
implements `FileStorage` and `HttpHandler`. The origin is HTTPS, or loopback HTTP
for local development. Supply at least 32 random signing-key bytes from application
configuration and retain that key across restarts. For an ephemeral HTTP port, use the `Supplier<URI>` overload, for example
`() -> URI.create("http://127.0.0.1:" + server.port())`; the origin is resolved when
a URL is issued after the server has bound. Mount the handler with
`server.route("/files", storage)` before starting the server. Connect its
notification consumer to `notification -> rocket.fileUploaded(booster, notification)`
after constructing the rocket and runtime (references can resolve this wiring
cycle). Invoke `storage.resumeNotifications()` during startup, and close the
storage when the host stops.

The provider streams bounded uploads into immutable version files and commits
metadata using atomic renames. One filesystem lock excludes competing local
writers. A successful PUT returns the baseline `{status:"success",result:{...}}`
shape with actual content type and size. Downloads stream bytes and return stored
metadata through `x-ms-meta-*` headers. Listing returns names, timestamps, lengths,
content types and metadata. Repeating a PUT with the same signed URL and content
reuses its version; changing content with that URL returns 409. Obtain a new PUT
URL to create another version.

Each version retains an undelivered notification until its consumer succeeds.
A notification failure returns 503 while preserving the file and pending work;
replay after restart uses the same stable notification identity. Booster commits
the upload event with a durable receipt, so redelivery does not duplicate it.
Immutable versions are retained on disk; there is currently no automatic retention
or garbage-collection policy. The data directory must be private to the application.
Symlink paths, traversal, oversized uploads and altered/expired signatures are
rejected. URL expiry defaults to one day and is bounded to seven days.

## Azure provider and notifications

`AzureBlobStorage(accountName)` builds the real Azure Blob SDK client using
`DefaultAzureCredential`, inherited proxy settings and JVM CA trust. It obtains
user-delegation keys and issues HTTPS-only read or write SAS URLs. No account key
is embedded. The injected `BlobServiceClient` constructor supports explicit
credential/client configuration and provider contract tests. The identity needs
the applicable account/container permissions, including `Storage Blob Data
Contributor` for the baseline write and delegation responsibilities.

Mount `AzureFileUploadHttpHandler(booster, rocket, authentication, senderPolicy)`
at `/azure/files` for Event Grid. The handler requires an authenticated sender,
handles subscription validation, and dispatches BlobCreated events. Configure
Event Grid's authenticated delivery to that endpoint. A validation code alone
does not authenticate subsequent events. `handleCloudEvent` accepts Event Grid
and CloudEvents JSON shapes after the host authenticates them. The alternative
`azureFileUploaded` adapter accepts the baseline Functions v4
`context.triggerMetadata` shape. A stable blob ETag is required for deduplication;
invocation IDs are not upload identities. The source account must match the configured Blob client endpoint; exact container
and configured directory selection are enforced before recording any event. Local
stores do not accept Azure Blob notifications. The infrastructure exposes
`BOOSTER_AZURE_STORAGE_ACCOUNTS` as account name → endpoint/containers JSON; map
those accounts to `AzureBlobStorage` instances and explicitly configure allowed
directories and policies in the application.

## Executed checks

Fifteen upload tests pass in an isolated JDK 27 test run, including real local HTTP
transfers/restart/retry, 100 recorded directory/glob compatibility cases, upload-event
projection and persistent deduplication, authenticated Event Grid delivery, and
real Azure SDK user-delegation SAS/list request serialization against deterministic
HTTP fixtures. The SDK fixtures are not a live Azure deployment or SAS transfer
check. Root's integrated build and live cloud validation are recorded separately
in `VALIDATION.md`.

A separate-process `LocalApplicationIT` additionally starts the complete local
host twice and exercises real JWT authentication, legacy WebSocket subscriptions,
both rockets, joined models, interrupted/resumed migrations and authenticated
lifecycle cleanup. `FullLocalApplication` is its executable application fixture.
