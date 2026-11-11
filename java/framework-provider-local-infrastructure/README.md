# booster-framework-provider-local-infrastructure (`com.boostercloud.framework.provider.local.infrastructure`)

Java port of `packages/framework-provider-local-infrastructure`: the process that runs a Booster application
locally. It serves the GraphQL and health endpoints over HTTP (the TypeScript Express app), mounts the
infrastructure rockets, runs the scheduled commands and ships the test helper of the integration tests. The
storage, the websocket server and the envelope adapters live in `framework-provider-local`.

The provider loads it with `ProviderLoader` (`provider.infrastructure()`), which calls
`Infrastructure.infrastructure(List<RocketDescriptor>)`; `Booster.run(Main.class, "start", "--port", "3000")` then
calls `start(config, port)`, which blocks until the process receives SIGINT/SIGTERM (see `java/cli/README.md`,
"Contract with the framework").

```java
// what Booster.run does for "start"
config.provider().infrastructure().start(config, 3000);

// tests and embedding: a non-blocking server over an explicit runtime
LocalProviderRuntime runtime = LocalProviderRuntime.create(LocalProviderRuntime.Options.defaults()
    .withBaseDirectory(projectDir).withWebSocketServer(true, "0").withUserApp(() -> app));
LocalServer server = Infrastructure.infrastructure(config.rockets,
        LocalInfrastructure.Options.defaults().withRuntime(runtime).withShutdownHook(false))
    .startServer(config, 0);                     // port 0: a free port, see server.port()
server.graphqlURL();                             // http://localhost:<port>/graphql
server.close();                                  // graceful stop; also closes the runtime
```

## Package map

| Java | TypeScript | Purpose |
| --- | --- | --- |
| `Infrastructure` | `index.ts` (`Infrastructure`) | entry point; loads the rockets |
| `LocalInfrastructure` (`Options`) | the object `Infrastructure(...)` returns | `ProviderInfrastructure`: `start` (blocking), `startServer`, unsupported `deploy`/`nuke`/`synth` |
| `LocalServer` | the body of `start(config, port)` | a running server: router, application, HTTP server, scheduler, rockets, shutdown |
| `Http`, `HttpCodes` | `http.ts` | `requestFailed(error, res)`, `HttpCodes` |
| `controllers.GraphQLController`, `controllers.HealthController` | `controllers/graphql.ts`, `controllers/health-controller.ts` | the two endpoints |
| `Scheduler`, `CronExpression`, `SchedulerClock` | `scheduler.ts` (+ `node-schedule`/`cron-parser`) | scheduled commands |
| `LocalInfrastructureRocket`, `InfrastructureRocketMetadata` | `infrastructure-rocket.ts` (`InfrastructureRocket`, `InfrastructureRocketMetadata`) | the rocket SPI |
| `testhelper.LocalTestHelper`, `LocalCounters`, `LocalQueries`, `ApplicationOutputs` | `test-helper/*` | integration-test helper |
| `testhelper.LocalStoreReader` | (Java-only) | read-only replay of the `.booster` files |
| `web.*` | Express 4, `body-parser`, `cors`, `finalhandler`, `qs`, Node `http` | the HTTP layer (see below) |

The `declare module 'http' { rawBody }` augmentation of `index.ts` is `HttpRequest.rawBody()`.

## The server

`start(config, port)` / `startServer(config, port)` build exactly the TypeScript app, in the same order:

1. A `Router` with `/sensor/health` → `HealthController` (`GET /*`) and `/graphql` → `GraphQLController`
   (`POST /`).
2. The application: `json({ limit: '6mb', verify: keep rawBody })`, `urlencoded({ extended: true, limit: '6mb' })`,
   `cors()`, a middleware adding `Access-Control-Allow-Origin: *` and
   `Access-Control-Allow-Headers: Origin, X-Requested-With, Content-Type, Accept`, the router, and
   `defaultErrorHandler` (logs the error and answers `requestFailed` unless something was already sent).
3. The port is bound (failing with `Could not listen on port N: ...` when taken) and each rocket's
   `mountStack(config, router, new InfrastructureRocketMetadata(port))` runs, in `config.rockets` order, on the same
   router, after the framework routes.
4. Java-only: `runtime.lockDataDirectory()` takes an operating-system lock on `.booster/booster.lock` (holding the
   process id) for the life of the process. A second process on the same project (another `boost start` with
   another `--port`, an IDE run next to it) does not start: it logs `The server does not start: Another Booster
   process (pid N) is using the data files of <project>/.booster. ...` and exits with code 1, instead of serving its
   own copy of the data and losing some of the other process's writes. The lock dies with the process, also when it
   is killed.
   Java-only start-up check: `runtime.loadExistingDataFiles()` loads the `.booster` files that exist. A torn last
   line (a write cut by a crash) and up to 10 % of corrupt lines are skipped with a warning
   (`[Booster local store] Discarding an incomplete last line of ...`, `Skipped N corrupt line(s) of ...`); a file
   with more than 10 % of corrupt lines fails the start with NeDB's `CorruptDataFileException`
   (`N% of the data file is corrupt, more than given corruptAlertThreshold (10%). Cautiously refusing to start NeDB to
   prevent dataloss.`), logged as `Could not load the data files of <project>/.booster; the server does not start: ...`,
   so `Booster.run` reports `The 'start' operation failed` and exits with code 1, leaving the file untouched.
   Then `runtime.removeStaleConnections()` deletes the websocket connections and subscriptions stored for
   connections this process does not have open: those of a process that ended without processing their
   `DISCONNECT` (killed, out of memory, power loss). Connections live in the process, so those rows can never be used
   again, and each of their subscriptions would otherwise run on every change of its read model and log
   `There is no open websocket connection with id ...` (TypeScript keeps them for ever). Clients reconnect and
   subscribe again. It logs `Removed N websocket connection(s) and M subscription(s) left by a previous run ...`.
5. Java-only start-up recovery: `runtime.recoverUndispatchedEvents(config)` re-dispatches the events a crash left
   stored but undispatched (`framework-provider-local` README, "Durability and recovery"); a failure is logged and the
   server starts anyway (the entries stay for the next start).
6. The provider's websocket server is started if needed, the HTTP server starts serving, the scheduler is configured
   (TypeScript does it in the `listen` callback) and
   `Local server listening on http://localhost:3000 (GraphQL: ..., health: ..., websocket: ws://localhost:65529/websocket)`
   is logged (`[Booster]|LocalInfrastructure#start:` prefix, info level).

| Request | Answer |
| --- | --- |
| `POST /graphql` (also `/graphql/`, any case, with a query string) | `200` + `response.result` as JSON for every result of `boosterServeGraphQL`; the result headers are not sent |
| `GET`/`HEAD /sensor/health/<component path>` (also `/sensor/health`) | success: `200` + the health results (also when unhealthy); failure: `code` + `{"title": ...}` |
| an exception in a controller | `requestFailed`: status of the error class (`InvalidParameterError` 400, `NotAuthorizedError` 401, `NotFoundError` 404, `InvalidVersionError` 422, ... else 500) + `{"title": toClassTitle(error), "reason": message}` |
| `OPTIONS` anything | `204` pre-flight answer of `cors()` (methods `GET,HEAD,PUT,PATCH,POST,DELETE`, requested headers echoed) |
| malformed JSON / JSON or form over 6 MB / unsupported charset or encoding | `500` + `{"title":"Syntax Error" \| "Payload Too Large Error" \| "Unsupported Media Type Error", "reason": ...}` (without CORS headers: the parsers run before `cors()`, as in TypeScript) |
| anything else | Express's `404` page `Cannot <METHOD> <path>` |

The application receives a `LocalHttpRequest` (method, URL relative to the mount point, lower-cased headers, parsed
body, raw JSON bytes) for GraphQL and health requests. GraphQL subscriptions use the provider's websocket server on its
own port (`LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT`, default `65529`), path `/websocket`, sub-protocol `graphql-ws`.

Requests run on virtual threads (Jetty 12 `QueuedThreadPool` with a virtual-thread executor), so handlers block
freely. Jetty is configured like Node: no `Server` header, 16 KiB of request headers, lenient URI checks.

### Graceful shutdown

`LocalServer.close()` (run by a JVM shutdown hook on SIGINT/SIGTERM when started with `start`) logs
`Stopping the local server on ...`, stops the scheduler and waits for running scheduled commands, stops the HTTP server
gracefully (no new connections, `503` for new requests on open connections, in-flight requests may finish within
`Options.shutdownTimeout`, 5 s by default), calls every rocket's `unmountStack()`, closes the provider runtime (websocket
server and data files; every write was already flushed) and logs `Local server stopped`. `start` then returns.

## Scheduled commands

`Scheduler.configureScheduler(config, userApp[, clock])` creates one job per entry of
`config.scheduledCommandHandlers` whose `scheduledOn` has at least one field: the expression is
`minute hour day month weekDay` with `*` for missing (or blank) fields and `year` ignored (`0/5` gives `0/5 * * * *`),
evaluated with `cron-parser`'s rules (`CronExpression`: ranges, steps, lists, month/day names, `0`/`7` Sunday, `?`,
`L`, `5L`, `5#2`, day-of-month OR day-of-week when both are restricted) in the JVM's default time zone. At second 0 of
every matching minute the job calls `userApp.boosterTriggerScheduledCommands(new LocalScheduleCommandEnvelope(name))`
on a new virtual thread, without waiting for earlier runs. Runs missed while the process was suspended run late, one
after the other, as with `node-schedule`. `SchedulerClock` makes the time source replaceable (tests use a manual clock).

## Rockets

A local infrastructure rocket is a package `com.acme.rocket.local` with a public class `Rocket implements
RocketBuilder` whose `build(parameters)` returns a `LocalInfrastructureRocket`. The application registers it with
`config.rockets.add(new RocketDescriptor("com.acme.rocket.local", parameters))`; `Infrastructure.infrastructure(...)`
loads it with `RocketLoader` (TypeScript's three error messages: `Could not load the rocket infrastructure package ...`,
`Could not initialize rocket infrastructure package ...`, `The package ... doesn't seem to be a rocket.`, the last one
also when the built object is not a `LocalInfrastructureRocket`).

```java
public final class LocalWebhook implements LocalInfrastructureRocket {
  @Override
  public void mountStack(BoosterConfig config, Router router, InfrastructureRocketMetadata metadata) {
    router.post("/my-hook", BodyParsers.raw(), (request, response, next) -> {
      Object result = UserAppRegistry.get().boosterRocketDispatcher(
          Map.of(Rockets.ROCKET_FUNCTION_ID_ENV_VAR, "rocket-webhook", "req", request));
      response.status(200).json(result);
    });
  }

  @Override
  public void unmountStack() {
    // stop watchers, threads...
  }
}
```

The `web` package is the Express API rockets need:

* `Router` — `use([path,] handler...)`, `use([path,] ErrorHandler)`, `get/post/put/patch/delete/head/options/all(path,
  handler...)`; routers are handlers, so they nest. Paths follow `path-to-regexp` 0.1: `:name`, `:name?`,
  `:name(\\d+)`, `*` (captured as `"0"`, `"1"`...); case-insensitive, optional trailing slash; mount paths match whole
  segments and are stripped from `request.url()` (kept in `baseUrl()`/`originalUrl()`). `GET` routes answer `HEAD`.
  Thrown exceptions and `next.next(error)` go to the next `ErrorHandler`.
* `HttpRequest` — `method()`, `url()`, `originalUrl()`, `baseUrl()`, `path()`, `params()`, `query()` (`qs`),
  `headers()` (lower-cased, Node joining rules), `body()`, `rawBody()`, `inputStream()` (the unread body, for
  streaming uploads), `readAllBytes()`, `is(type)`, `attribute(name[, value])`, `toLocalHttpRequest()`.
* `HttpResponse` — `status`, `setHeader`, `header` (Express `res.set`), `type`, `vary`, `getHeader`, `removeHeader`,
  `json`, `send`, `end`, `outputStream()`, `download(path[, name])`, `headersSent()`, `finished()`. `setHeader`
  (and so `header`) validates like Node's: a name that is not an HTTP token fails with `Header name must be a valid
  HTTP token ["<name>"]`, a value with CR, LF, other controls or characters above `U+00FF` with `Invalid character in
  header content ["<name>"]` (`IllegalArgumentException`, Node's `TypeError`), before anything reaches Jetty.
* `BodyParsers.json/urlencoded/raw`, `Cors.middleware()`, `MimeTypes`, `QueryStrings`, `Uris`.
* `MemoryHttpResponse` records a response in memory for unit tests of handlers.

## Test helper

`LocalTestHelper.build(appName[, Options])` gives `outputs()` (`graphqlURL`, `websocketURL`, `healthURL` from
`BOOSTER_INTERNAL_LOCAL_PORT` / `LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT`, defaults 3000 / 65529), `counters()` and
`queries()`; it first waits (30 s by default, `Options.withReadinessTimeout`) until a `query { __typename }` POST
answers 200. Counters and queries read the `.booster` files of `Options.baseDirectory` (the working directory by
default) without opening them as a store (`LocalStoreReader`: NeDB replay, read-only, tolerant to a write in progress),
so they work while the application runs in another process:

| Method | Value |
| --- | --- |
| `counters().events()` | documents with `kind: "event"` in `events.json` (soft-deleted ones included, snapshots not) |
| `counters().readModels(name)` | documents with `typeName: name` in `read_models.json` |
| `counters().connections()`, `subscriptions()` | documents in `connections.json`, `subscriptions.json` |
| `queries().events(key[, latestFirst])` | `events.json` documents (with `_id`) for `<entityTypeName>-<entityID>-<kind>` (`Cart-42-event`, the Azure key), or for the entity id when the key has another shape; by `createdAt`, newest first by default |
| `queries().readModels(id, name[, latestFirst])` | the read models (`value`, `boosterMetadata` included) with that id |

The methods mirror the application tester's `Counters`/`Queries` (`long` counts, `List<Map<String, Object>>`
documents); this module does not depend on it, so an adapter wraps the helper into a `ProviderTestHelper`.

## Differences from TypeScript

Lifecycle:

* `start` blocks until the server stops and stops it gracefully on SIGINT/SIGTERM (TypeScript returns right after
  `listen` and the process dies on the signal). `startServer` returns a `LocalServer` for tests and embedding.
* The port is bound before the rockets are mounted, so `InfrastructureRocketMetadata.port` is the real port also for
  port 0 (TypeScript passes the requested port); a taken port fails `start` with an exception (Node crashes with an
  unhandled `EADDRINUSE`). When the bound port differs from `BOOSTER_INTERNAL_LOCAL_PORT`, the variable is overridden
  in-process (`InternalInfo.setBoosterLocalPort`) so the health sensor probes the right URL.
* The lock of the `.booster` directory (TypeScript lets two processes share the files), the removal of the
  connections and subscriptions a crash left, the start-up load of the existing data files (a corrupt file stops the
  start; TypeScript loads each NeDB file on first use, so it starts and the requests touching the file fail),
  start-up recovery of undispatched events, the
  websocket server being (re)started with the HTTP server, the listening
  and stopping log lines, `unmountStack()` on shutdown: Java-only.
* `deploy`, `nuke` and `synth` exist and throw `UnsupportedOperationException` with the CLI's message
  (`Attempted to perform the 'deploy' operation with a provider that does not support this feature, please check your
  environment configuration.`); `supports()` answers `true` only for `START`.
* The application is reached through the provider runtime (`UserAppRegistry` by default) instead of
  `require(dist/index.js)`; the controllers receive a `LocalHttpRequest` instead of the Express request.

HTTP layer (a re-implementation of the parts of Express the framework and the rockets use):

* Error titles come from Java class names: where TypeScript throws a plain `Error` (title `"Error"`), Java throws
  `IllegalStateException`/`IllegalArgumentException` (`"Illegal State Exception"`), per ARCHITECTURE §7. A missing
  message is sent as `""` (a JavaScript `Error`'s default). Body-parser errors keep their JavaScript names
  (`SyntaxError`, `PayloadTooLargeError`, `UnsupportedMediaTypeError`, `BadRequestError`, `URIError`), so their titles
  match; JSON syntax messages follow V8's format (`Unexpected token 'x', "x" is not valid JSON`,
  `Unexpected end of JSON input`) but the reported position comes from Jackson.
* Exceptions thrown by any handler reach the error handlers (Express 4 only catches synchronous throws; Node turns
  rejected promises into unhandled rejections). A handler that returns without answering gets its response finished
  (Express leaves the request hanging). An error reaching the end of the application after the response started aborts
  an unfinished response and is otherwise only logged (Express destroys the socket). The final error page shows the
  status message (Express's production page), never a stack trace.
* Not emitted: `X-Powered-By: Express`, the weak `ETag` of `res.send` and `304` answers, `Range` requests of
  `res.download`, routers' automatic `OPTIONS` answers (unreachable behind `cors()` anyway). During a shutdown, new
  requests on kept-alive connections get `503`.
* Route paths: only `:param`, `:param?`, `:param(regex)` and `*` are special; other characters are literal (in
  `path-to-regexp` 0.1 some, like `?` and `+`, are live regex syntax).
* `qs` is reproduced with its defaults (depth 5, 1000 parameters, array limit 20; depth 32 and an array limit of at least
  100 for urlencoded bodies); exotic merges of mixed array/object keys are best effort.
* Logging goes through the Booster logger (`config.logger`, error level always on) instead of `console.error`.

Scheduler:

* A schedule with no field set is never run, as the documentation says and the Azure provider does; the TypeScript
  local provider runs `{}` every minute. Blank fields count as absent.
* Invalid expressions and expressions that never match are logged as warnings (TypeScript schedules nothing,
  silently); failing runs are logged (TypeScript: unhandled rejections). The non-deprecated
  `boosterTriggerScheduledCommands` is called (TypeScript calls its deprecated alias, the same function).
* Jobs stop on shutdown and running commands are awaited (TypeScript never cancels them).

Test helper: real counters and queries instead of `-1` and `[]`, ports from the environment instead of hard-coded
defaults, and a real readiness check instead of the empty `ensureProviderIsReady`.

Kept on purpose (TypeScript behaviour): GraphQL and health successes are always `200` and the result headers
(including `config.defaultResponseHeaders` and the health `status-code`) are not sent (Q12, Q13); health failures send
only `{"title"}` because TypeScript reads `response.message` (Q14); the controllers answer an exception and also pass it
to `next`; body parsers run before `cors()`, so parser errors have no CORS headers; the JSON/urlencoded parsers apply to
rocket routes too (a rocket that streams an upload must not receive it as `application/json`).

## Not implemented

* Express features no framework or rocket code uses: `res.sendFile`, `res.redirect`, cookies, `res.render`,
  `router.param`, `router.route(path)` chains, `mergeParams`, app settings (`trust proxy`, `etag`...), `express.text`,
  `express.static`, multipart parsing (a rocket parses `HttpRequest.inputStream()` itself).
* `node-schedule` features the framework does not use: `RecurrenceRule` objects, one-off `Date` jobs, the `tz` option.
* V8-exact JSON error messages (positions and the less common V8 message templates).

## Tests

`mvn -B -pl framework-provider-local-infrastructure -am verify` from `java/`.

* `controllers.GraphQLControllerTest` ports `test/controllers/graphql.test.ts` (`test/index.test.ts` is empty);
  `HealthControllerTest`, `InfrastructureTest`, `CronExpressionTest`, `SchedulerTest` (manual clock) and the `web.*Test`
  classes cover what TypeScript leaves untested.
* HTTP-level tests start real servers on random ports over temporary `.booster` directories with a fake application:
  `LocalServerTest` (routes, parsers, CORS, errors, health, scheduler wiring, concurrency on virtual threads),
  `WebSocketTest` (graphql-ws connect/message/disconnect), `RocketMountingTest` (loading errors, routes, streaming
  upload/download, unmount), `GracefulShutdownTest` (in-flight requests finish, new connections refused, stores
  closed), `SignalShutdownTest` (SIGTERM to a real JVM running `start`), `RestartTest` (data survives a restart; events
  left undispatched by a crash are re-dispatched at start-up; a torn last line or a few corrupt lines are discarded
  before the server serves, too many corrupt lines fail the start; a second server on the same data files does not
  start; connections and subscriptions a crash left are removed) and `testhelper.*Test` (counters and queries over NeDB
  files, readiness).
