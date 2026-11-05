# Health sensors

`HealthService` implements the Booster component tree and `HealthHttpHandler`
exposes it at `GET /health` and `GET /health/<component>`. Responses contain an
array of `{name,id,status,details?,components?}` values. Healthy results return
HTTP 200; unhealthy results return 503. The statuses are `UP`, `PARTIALLY_UP`,
`DOWN`, `OUT_OF_SERVICE` and `UNKNOWN`. As in the baseline, an UNKNOWN `rockets`
root is healthy when no rocket checks are configured; other UNKNOWN components
are unhealthy.

The standard provider sensors use these IDs:

- `booster`
- `booster/function`
- `booster/database`
- `booster/database/events`
- `booster/database/readmodels`
- `rockets`, plus configured `rockets/<name>` probes

Provider checks call the actual event/state stores. Function and rocket probes
are supplied by the application or provider because they must describe the
mounted application's lifecycle. No cloud deployment is implied by a successful
local sensor response.

Provider defaults are disabled, matching the TypeScript sensor configuration.
`LocalHost` explicitly enables the standard tree without details. Applications
can replace the handler before server startup:

```java
var health = provider.own(ProviderHealth.defaults(
    booster,
    () -> applicationReady.get(),
    () -> "http://127.0.0.1:" + server.port() + "/graphql",
    List.of(provider.directory().toString()),
    Map.of("uploads", uploads::isReady))
  .configureAll(true, false, true)
  .authorize(HealthAuthorizer.policy(AccessPolicy.roles("operator")))
  .indicator(new HealthConfiguration("search", "Search index", true, false, true),
      (configuration, request) -> new HealthResult(search.isReady() ? HealthStatus.UP : HealthStatus.DOWN))
  .build());
server.health(new HealthHttpHandler(health, authentication));
```

The readiness suppliers above represent application-owned checks. A custom
authorizer can also inspect `HealthRequest.componentPath()`, the verified user
and request metadata. Authentication runs before authorization and sensors;
invalid credentials return 401 and denied access returns 403. Raw tokens are not
passed to indicators or included in responses.

Each sensor has `enabled`, `details` and `showChildren` options. Disabling a
parent disables its descendants, including direct requests. Disabling details
removes the field from the response. Hiding children omits them from the parent
response while preserving direct component access. Custom sensors can replace
the defaults by registering the same ID. Register parent IDs before building a
custom tree.

Checks have a configurable whole-request deadline, five seconds by default, and
bounded concurrent execution. A failed or timed-out sensor produces DOWN with a
generic reason; exception messages and stack traces are not returned. A sensor
that ignores interruption retains its execution slot until it actually exits.
Responses are not cached, and the HTTP adapter bounds their encoded size.

`HealthServiceTest` covers inherited configuration, custom authorization, real
provider reads, rocket aggregation, timeout/concurrency behavior and actual HTTP
status/authentication responses. Close the service with the provider to stop
its worker executor.
