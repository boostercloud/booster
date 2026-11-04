# Local provider and application lifecycle

The local provider runs a `LocalApplication` using Java, an H2 event journal,
persisted snapshots/read models and a separate H2 CAS state store. Its application
contract is also used by the generated Maven project; running a custom domain
does not require editing the bundled shopping-cart demo.

```java
public final class Application implements LocalApplication {
  public Application() {}

  @Override public Booster create(LocalProvider provider) {
    return Booster.builder(provider.eventStore())
        .stateStore(provider.stateStore())
        // Register this application's entities, events, commands, queries and projections.
        .entity(Order.class)
        .event(OrderReceived.class, OrderReceived::id, Order.class, Order::received)
        .build();
  }

  @Override public void configure(Booster runtime, BoosterHttpServer server,
      LocalProvider provider) {
    var webhooks = WebhookRocket.builder(runtime)
        .authentication(authentication())
        .route("orders/received", (request, register) -> {
          register.emit(new OrderReceived(request.body().path("id").asText()));
          return WebhookResponse.text("accepted");
        }).build();
    server.route("/webhook", webhooks.handler());
  }
}
```

The application class must implement `LocalApplication` and expose a public
no-argument constructor. `authentication()` supplies the shared GraphQL/health
authenticator; explicitly pass it to rocket handlers as shown. The default
authenticator accepts anonymous requests and rejects supplied credentials.
Authorization remains a separate per-command/query/read-model/rocket policy.
`schedules()` returns persistent calendar tasks. `rocketHealthChecks(...)` adds
named health sensors for configured rockets.

`LocalProvider.own(worker)` registers an `AutoCloseable` worker for host-managed
shutdown. The host stops accepting HTTP requests, stops owned background workers,
then closes Booster and its stores. Workers close in reverse registration order.
Use this ownership for file notifications and other background services created
by `configure`. See [scheduling.md](scheduling.md) and the [webhook rocket](rockets/webhook.md)
for their contracts.

## Configuration and CLI

```json
{
  "applicationClass": "com.example.Application",
  "dataDirectory": ".booster-java",
  "host": "127.0.0.1",
  "port": 3000
}
```

Relative data paths resolve against the configuration file's directory. The host
defaults to `127.0.0.1`; explicitly choose `0.0.0.0` to expose the application on
other interfaces. Port zero requests an available port. Configuration rejects
unknown fields and trailing JSON. It contains application configuration, not the
runtime shutdown credential.

Run the CLI with both the application's compiled classes and Booster's runtime
dependencies on the classpath. For example, from a compiled Maven application,
using the framework's shaded JAR:

```sh
java -cp "target/classes:/path/to/booster-java-0.1.0-SNAPSHOT-all.jar" \
  io.boostercloud.booster.cli.BoosterCli local synth \
  --application com.example.Application --config local.json --data .booster-java --port 3000
```

Use that same classpath/main-class prefix for the remaining operations:

```text
local start   --config local.json
local start   --config local.json --detach true
local status  --config local.json
local stop    --config local.json
local rebuild --config local.json
local nuke    --config local.json --confirm-data /absolute/path/to/.booster-java
```

`synth` writes a new configuration file and refuses to overwrite an existing
file. `start` runs the application in the foreground; `--detach true` starts a
child JVM, verifies that child owns the runtime metadata and waits for health.
The detached process writes `runtime.log` under the data directory. `status`
returns its PID, port, application class and current provider health report over
the authenticated loopback control endpoint. A custom application's protected
public health route does not prevent lifecycle status checks. A failed detached
startup terminates its own child process and reports the failure.

`stop` uses an authenticated loopback lifecycle endpoint and waits for the
runtime to release its lock. It retains the event history, durable state,
projections, receipts and schedule cursors. `rebuild` requires exclusive access
to the stopped application's directory and rebuilds projections from the journal.
It does not replay arbitrary external side effects.

`nuke` intentionally deletes the local provider's data directory, including
history. The exact absolute directory must be supplied through `--confirm-data`.
Cleanup refuses unowned directories and an active provider. A dead process's
stale runtime metadata does not prevent cleanup when the lifecycle lock proves
that no process owns the directory. Normal stop/restart does not invoke cleanup.

## Persistence, ownership and control

The provider initializes only an empty directory (or one containing its startup
log) and writes `.booster-local-provider` as an ownership marker. It refuses to
adopt unrelated files. Data-directory ancestors, ownership metadata, database
files and startup logs must not be symbolic links. H2 supports this local
provider in a single JVM at a time; this is not shared network storage for
multiple hosts.

A stable `.booster-java-<path-hash>.lock` file lives in the data directory's
parent. Creation, runtime ownership and cleanup use the same lock inode. **The
lock file remains after cleanup** so a concurrent start cannot acquire a new
lock while cleanup still owns an old one. It contains no shutdown token or
application data. Do not delete it while local lifecycle operations are active.

The host atomically writes `.runtime.json` with its instance ID, PID, selected
port and a random shutdown token. On POSIX filesystems this file is owner-only
(`0600`). Do not commit or expose it. The CLI reads it locally; status output and
startup logs omit the token. `GET /__booster/status` and
`POST /__booster/shutdown` require both a loopback peer and the matching bearer
token; forwarding headers cannot claim loopback access. The status control route
checks the provider directly, independently of any replacement public health
handler. Metadata is removed only by its owning instance during orderly shutdown.
A new startup can discard stale metadata after obtaining the exclusive lock.

`GET /health` reports the local provider's component health and is public by
default. The host's default health configuration omits detailed service metadata;
applications can install a different health service during `configure`. Local
health checks inspect the local runtime/storage and configured rocket sensors;
they do not contact Azure or establish cloud deployment readiness.

The validation suites exercise store restart, exclusive ownership, linked-path
rejection, worker shutdown and the complete local host lifecycle. The webhook
integration test starts the host, sends a real webhook, queries its projection
over GraphQL, stops it, starts it again and verifies the original durable
delivery receipt. Executed test totals belong in [VALIDATION.md](VALIDATION.md).
