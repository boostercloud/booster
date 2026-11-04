# Azure provider and Java 27 hosting

The Azure implementation uses the Azure Java SDKs for Cosmos DB, Event Hubs,
Web PubSub, Blob Storage and identity. The host is a Java 27 container on Azure
Container Apps; it does not rely on Azure Functions supporting Java 27. Domain
code, GraphQL, subscription processing and rocket handlers execute in Java.

**Real Azure deployment, service integration tests and cloud cleanup have not
been executed. The user explicitly deferred them because Azure access is not
available.** Local contract tests exercise transaction, recovery, subscription,
authorization and ARM request behavior. They do not establish that Azure has
accepted this template, that RBAC has propagated, or that a deployed application
works. See [VALIDATION.md](VALIDATION.md) for executed checks.

## Application contract

Implement `io.boostercloud.booster.azure.hosting.AzureApplication` with a public
no-argument constructor. `create(AzureProvider)` must build the domain using
both `provider.eventStore()` and `provider.stateStore()`:

```java
Booster.Builder builder = Booster.builder(provider.eventStore())
    .stateStore(provider.stateStore());
// Register the application's entities, events, commands, projections and rockets.
return builder.build();
```

`authentication()` supplies the same authentication provider used by HTTP and
Web PubSub connection initialization. `configure(runtime, server)` mounts
webhooks or other application routes before startup. Mount the webhook rocket's
handler with `server.route("/webhook", rocket.handler())`.

`schedules()` returns named `ScheduledTask` calendars. `AzureHost` runs them
through `PersistentScheduler`, sharing Cosmos leases, progress and occurrence
receipts across replicas. External timer dispatch should use
`new AzureScheduleDispatcher(scheduler).dispatchScheduledTask(name, nominalTime)`
so it shares the same occurrence identity with the hosted scheduler.

For uploads, install `FileUploadRocket` into the domain builder and return that
same rocket from `fileUploads()`. Use `AzureBlobStorage` providers configured
for the actual account and containers. When `enableUploads` is true in deployment
configuration, the host requires `fileUploads()` to be present and mounts its
authenticated Event Grid endpoint at `/azure/files`. An enabled deployment with
no installed rocket fails startup instead of accepting unhandled notifications.
The bundled shopping-cart application does not enable uploads automatically.

`AzureProvider.open(AzureProvider.Config.fromEnvironment())` uses
`DefaultAzureCredential`; the constructor accepting `TokenCredential` supports
explicit identity selection. An application embedding the provider directly must
call `provider.stopWorkers()` before closing its `Booster` runtime, then close
the provider's SDK clients. `AzureHost` owns this lifecycle.

## Configuration and image

An example deployment configuration for an application that installs the upload
rocket is:

```json
{
  "application": "orders-java",
  "subscriptionId": "00000000-0000-0000-0000-000000000000",
  "resourceGroup": "orders-java-test",
  "location": "westeurope",
  "image": "your-registry.example/orders-java:version",
  "applicationClass": "com.example.orders.AzureOrdersApplication",
  "containers": ["uploads"],
  "additionalStorageAccounts": {
    "ordersattachments123": ["documents", "images"]
  },
  "replicas": 1,
  "port": 8080,
  "enableUploads": true
}
```

Replace the example identifiers. Set `enableUploads` to false for an application
without the upload rocket. It defaults to false when omitted. The image must be
pullable by Container Apps; the generated template does not configure private
registry credentials. Supply any required registry configuration before using a
private image.

`containers` belongs to the generated primary account. Optional
`additionalStorageAccounts` maps globally unique Azure account names to their
container lists. Each account gets its own Blob Storage resources, identity role
assignment and, when uploads are enabled, a BlobCreated Event Grid subscription.
Names must be 3–24 lowercase letters/digits; configure at most 16 additional
accounts and 1–32 containers per account. The application chooses matching
`AzureBlobStorage` instances and policies for those accounts.

From `java/`, build the framework with `./mvnw verify`, then build its provided
image using `docker build -t booster-java:version .`. The Dockerfile installs the
same checksum-verified JDK 27 used by the Java build and runs as an unprivileged
user. Extend the image to copy the application's JAR and additional dependencies
into `/app/`. The entry point loads `BOOSTER_APPLICATION_CLASS` from that
classpath. The application's dependencies must be compatible with those already
included in the Booster shaded JAR. Build-time proxy/CA configuration belongs to
the build environment; do not copy proxy credentials into the image.

The CLI supports these operations:

```sh
java -jar target/booster-java-0.1.0-SNAPSHOT-all.jar azure synth --config azure.json --output azure/template.json
java -jar target/booster-java-0.1.0-SNAPSHOT-all.jar azure deploy --config azure.json
java -jar target/booster-java-0.1.0-SNAPSHOT-all.jar azure status --config azure.json
java -jar target/booster-java-0.1.0-SNAPSHOT-all.jar azure nuke --config azure.json --confirm-resource-group orders-java-test
```

`synth` is local and does not contact Azure. The other commands require Azure
access and are documented for future deployment; they were not run for this
goal. Deployment creates or updates a dedicated resource group and waits for ARM
completion. Existing groups must have the matching
`booster-java-application` ownership tag. Teardown requires the exact group name,
checks ownership and waits until the group is absent. It deletes the whole owned
resource group; do not put unrelated resources in it.

## Resources, identity and endpoints

The ARM template includes Cosmos DB, Event Hubs, Web PubSub, Blob Storage,
Container Apps and a user-assigned managed identity. Its data-role assignments
are Cosmos DB Built-in Data Contributor, Azure Event Hubs Data Sender and
Receiver, Web PubSub Service Owner, and Storage Blob Data Contributor. The
deploying identity also needs management-plane permissions to create these
resources and role assignments. The template waits for resource/role creation;
role propagation can still take time.

Cosmos uses containers `journal` and `state` partitioned by `/pk`, and a `leases`
container partitioned by `/id`. The state container enables per-document TTL.
The Event Hubs entity and Web PubSub hub are both named `booster`. With uploads
enabled, an Event Grid system topic forwards `Microsoft.Storage.BlobCreated`
events to `/azure/files` using the Event Grid schema and bounded retries.

The following values are injected into the container:

| Variable | Purpose |
| --- | --- |
| `AZURE_CLIENT_ID` | User-assigned managed identity selection. |
| `BOOSTER_APPLICATION_CLASS`, `PORT` | Java application entry point and listen port. |
| `BOOSTER_AZURE_COSMOS_ENDPOINT`, `BOOSTER_AZURE_DATABASE` | Cosmos account and database. |
| `BOOSTER_AZURE_JOURNAL_CONTAINER`, `BOOSTER_AZURE_STATE_CONTAINER`, `BOOSTER_AZURE_LEASE_CONTAINER` | Provider containers. |
| `BOOSTER_AZURE_APPLICATION` | Application partition identity. |
| `BOOSTER_AZURE_EVENTHUB_NAMESPACE`, `BOOSTER_AZURE_EVENTHUB_NAME` | Event Hubs namespace host and entity. |
| `BOOSTER_AZURE_WEBPUBSUB_ENDPOINT` | Web PubSub service endpoint. |
| `BOOSTER_AZURE_STORAGE_ENDPOINT`, `BOOSTER_AZURE_STORAGE_CONTAINERS` | Storage account endpoint and configured containers. |
| `BOOSTER_AZURE_STORAGE_ACCOUNTS` | JSON mapping of every account name to its endpoint and container list. |
| `BOOSTER_AZURE_UPLOADS_ENABLED` | Requires the application upload rocket when true. |
| `BOOSTER_AZURE_UPSTREAM_TOKEN` | Secret used only to authenticate service callbacks. |
| `BOOSTER_AZURE_UPSTREAM_VERSION` | Nonsecret token version causing a new container revision on rotation. |

HTTP GraphQL is `/graphql`; local WebSocket compatibility remains available on
the same host. `POST /azure/negotiate` authenticates the caller and obtains a
short-lived Web PubSub connection URL. Connect with the legacy `graphql-ws`
subprotocol and send `connection_init` with the application's credentials before
starting operations. A negotiate token authorizes a transport connection; domain
access still goes through Booster authentication and access policies.

The `/azure/webpubsub` callback is guarded by a secret URL parameter supplied
only to Web PubSub. It verifies validation origins and handles connect, message
and disconnect CloudEvents. `/azure/files` requires a separate constant-time
check of the same secret delivered in an Authorization header; Event Grid marks
that header as secret. Service callback credentials are not application user
tokens and are never copied into persisted subscription identity.

SDK HTTP clients honor the inherited HTTP CONNECT proxy and JVM CA trust.
Cosmos uses Gateway mode, while Event Hubs uses AMQP over WebSockets, avoiding
an implicit dependency on direct Cosmos TCP or AMQP port 5671. Proxy settings do
not grant access to destinations blocked by environment network policy.

## Durability and limits

The Cosmos event journal uses **one logical partition per application** so that
a transaction can atomically check stream versions and write events, stream
heads, its result receipt and a global position. This deliberately preserves the
Java core's cross-stream transaction contract. The deployment therefore inherits
Cosmos's **100 operations per transactional batch, 2 MiB batch limit and 20 GiB
logical-partition storage limit**. The implementation rejects commits above 100
operations or a conservative 1.9 MB serialized payload budget. It does not split
one command into non-atomic transactions or silently distribute one journal
across partitions. Capacity planning must include command receipts and their
event copies.

Snapshots, projections, migration progress and scheduling state use durable
ETag compare-and-set documents. Tombstones retain revisions to avoid
delete/recreate races. Event payload redaction preserves journal positions and
stream versions, empties the original payload and updates its owning command
receipt atomically; the receipt's independently returned command result remains
unchanged.

The leased Cosmos change feed publishes immutable journal events to Event Hubs.
Consumers retain partition leases and sequence checkpoints in Cosmos, advance
only after successful replay/handler recovery, and never append an Event Hubs
payload directly to the authoritative journal. Core receipts deduplicate derived
handler work; arbitrary external side effects still need application-level
idempotency.

Connections and subscription operations are persisted with absolute expiry and
TTL cleanup. They can be restored on another replica without persisting raw
bearer tokens. CloudEvent delivery receipts prevent repeated initialization on
service retries. A separate leased Cosmos projection feed applies current
resource authorization, filters, sequence keys and GraphQL selection sets before
sending notifications. Expired connections are excluded immediately and a
background worker closes their Web PubSub transports.

A restored connection retains its previously verified claims until expiry or
reconnection. It does not retain a raw token to refetch JWKS or reauthenticate
that token on each notification. Current application authorization still runs on
the persisted identity. To apply a changed identity or role claim immediately,
close the connection and require a fresh authenticated connection.

Event Hubs delivery and Web PubSub notifications are **at least once**. A service
send can succeed before its checkpoint is recorded, so a retry may deliver a
duplicate. Cosmos's latest-version change feed is not a durable log of every
intermediate projection value. The event journal is the durable history.

The provider's `health()` performs service requests instead of synthesizing
success. Event pipeline failures and subscription cleanup failures also remain
inspectable through their worker APIs. Operational service quotas, eventual RBAC
propagation, outage recovery and successful deployment still require the deferred
Azure integration exercise.
