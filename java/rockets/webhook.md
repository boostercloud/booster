# Java webhook rocket

`io.boostercloud.booster.rockets.webhook` implements the webhook dispatcher in
Java. The same `HttpHandler` runs in the local HTTP server and the Azure Java
container. It does not start a Node process or forward work to the TypeScript
rocket. Azure ingress must forward `/webhook/*` to that container; a real Azure
deployment is a separate validation requirement.

```java
var webhooks = WebhookRocket.builder(booster)
    .authentication(AuthenticationProvider.jwt(verifier))
    .route(new WebhookRoute("orders/received", null, Set.of("POST"),
        AccessPolicy.roles("integration"), null, MultipartConfig.defaults(),
        (request, register) -> {
          register.emit(new OrderReceived(request.body().path("id").asText()));
          register.responseHeader("X-Receipt", register.context().requestId());
          return WebhookResponse.json(Map.of("received", true));
        }))
    .build();

var server = new BoosterHttpServer(booster, 8080);
server.route("/webhook", webhooks.handler());
server.start();
```

The route above receives `/webhook/orders/received`. For configurations already
containing `webhook/`, `WebhookRoute("webhook/orders/received", handler)` selects
the same path without duplicating that prefix. Nested literal paths are
supported. `route` takes precedence over the deprecated `origin`; the original
configuration values remain available in the request. An adapter that receives
the Azure Functions `/api/` prefix can use `dispatch(...)` directly or mount the
handler at `/api/webhook` as well. A provider adapter can supply route parameters
to the five-argument `dispatch` overload.

Methods default to POST and may explicitly include GET. As in the baseline
rocket, an omitted authorizer allows public access. Explicit policies support
public, authenticated and role-based requests. A `WebhookAuthorizer` additionally
receives the verified user and complete request for request-aware checks; throw
`BoosterException(FORBIDDEN, ...)` to reject a request. Both policy and request
authorization are checked again before replaying a stored response. A supplied
invalid credential is rejected even on public routes.

Requests expose normalized multivalue headers and query parameters, provider
parameters, raw UTF-8 text, original bytes, parsed JSON/text and multipart files
and fields. Binary file content is not passed through a text conversion. The
multipart parser supports quoted boundaries, per-part MIME types/charsets,
extended UTF-8 filenames and configurable parameter charset/path preservation.
It never writes client-supplied filenames to the filesystem.

Default request/response limits are 1 MiB and the HTTP adapter permits 32 active
requests. Multipart defaults cap each file at 1 MiB, fields at 64 KiB, files at
16, parts at 64 and each header block at 16 KiB. These limits are configurable;
exceeded limits reject the request rather than passing truncated files to a
handler. The configured body limit is also the parser's maximum memory input.
HTTP headers, query parameter counts and URI lengths have separate bounds.

Successful handlers return text, JSON, binary/file or empty responses with
custom headers. `Register.responseHeader` values are included in the stored
response. Header names/values reject injection and framing headers are owned by
the HTTP server. The original rocket's permissive CORS header (`*`) is retained.

`Idempotency-Key` (or `X-Request-ID`) identifies a delivery; a random ID is used
when neither is supplied. The key, verified identity and request fingerprint
protect the action receipt. Its stored ID is derived from the configured route
and delivery key in a reserved system namespace that public commands cannot
preclaim. Buffered events and response commit atomically,
and a retry returns the persisted response across a runtime/database restart.
Changing a payload while reusing a key conflicts. Transport-volatile headers,
the host/port and bearer-token bytes are excluded from the request fingerprint;
the authenticated subject, roles and claims are included by Booster. Application
handlers must use their own idempotency keys for arbitrary external side
effects. An explicit `Register.flush()` has the core's partial-commit semantics.

## Compatibility

The source baseline is `boostercloud/rocket-webhook` commit
`07aa8a806ff6ae5acbdb1ba06ebdc068f6650cbb`. Contract vectors are derived from its
dispatcher and response-builder source, with the source paths recorded in the
test fixture. These vectors are not a claim that the TypeScript test suite was
executed in this Java build.

Default compatibility preserves several unusual baseline behaviors:

- `request.method()` is `POST` even for an accepted GET; `httpMethod()` exposes
  the actual HTTP method separately.
- Missing routes and disallowed methods return HTTP 500 with `{title, reason}`.
- Denied authorization returns HTTP 401.
- JSON responses with `null`, `false`, zero or an empty string have an empty body.
- Malformed/non-JSON bodies remain text, matching the Azure request adapter.

Applications can explicitly enable `strictHttpSemantics(true)` to use the actual
event method, HTTP 404/405 for route/method failures, HTTP 403 for authorization,
HTTP 409 for conflicting deliveries and rejection of malformed JSON declared as
`application/json`. This is opt-in and does not silently change compatibility.
Unexpected internal failures return the baseline error envelope with a generic
reason; Java stack traces and exception internals are not returned to clients.

## Validation

`WebhookRocketTest` exercises delivery, authentication/authorization, rollback,
concurrent idempotency, JSON/text/binary/empty responses, bounds, multipart,
compatibility defaults and strict mode. Its real HTTP test closes the server
and H2 journal, opens a new runtime/server, repeats a delivery and verifies the
original bytes, headers and single projected event. `MultipartParserTest`
exercises malformed input, binary boundary-like content, limits, charsets and
filenames. The root validation record reports executed outcomes. Actual Azure
ingress, authentication and delivery must also be verified in the goal's
isolated deployment.
