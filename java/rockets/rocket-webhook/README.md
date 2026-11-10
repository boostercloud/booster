# booster-rocket-webhook (`com.boostercloud.rocket.webhook`)

Java port of the webhook rocket (`boostercloud/rocket-webhook` 2.1.0, the npm packages
`@boostercloud/rocket-webhook-types`, `-core`, `-local-infrastructure` and `-azure-infrastructure`). The rocket adds
HTTP endpoints to a Booster application: when an endpoint is called, a handler class of the application receives the
request, may register events, and its result is the HTTP answer. Supported providers: **Local** and **Azure** (there is
no AWS package, as in TypeScript).

## Installation

```xml
<dependency>
  <groupId>com.boostercloud</groupId>
  <artifactId>booster-rocket-webhook</artifactId>
  <version>4.1.0</version>
</dependency>
```

The local and Azure parts are in the same artifact; their framework dependencies are `optional`, so an application
only gets the provider (and infrastructure) it already depends on.

## Configuration

All the webhooks of an application are declared in **one** `BoosterWebhook` per configuration (creating it registers
the rocket function `rocket-webhook`; a second one fails). `rocketForLocal()` / `rocketForAzure()` give the
`RocketDescriptor` of each provider.

```java
package com.example.shop;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterConfigurator;
import com.boostercloud.rocket.webhook.core.BoosterWebhook;
import com.boostercloud.rocket.webhook.types.AllowedHttpMethod;
import com.boostercloud.rocket.webhook.types.MultiPartConfig;
import com.boostercloud.rocket.webhook.types.WebhookParamsEvent;
import java.util.List;

public class Config implements BoosterConfigurator {

  static List<WebhookParamsEvent> webhooks() {
    return List.of(
        WebhookParamsEvent.builder()
            .route("test")                                         // POST /test (local), /api/test (Azure)
            .handlerClass(TestHandler.class)
            .allowedMethods(AllowedHttpMethod.POST)                 // the default when omitted
            .build(),
        WebhookParamsEvent.builder()
            .route("clients/other")                                 // nested routes are fine
            .handlerClass(FacebookHandler.class)
            .allowedMethods(AllowedHttpMethod.GET, AllowedHttpMethod.POST)
            .authorizeRoles(Admin.class)                            // or authorizeAll(), or authorize((user, request) -> ...)
            .multiPartConfig(MultiPartConfig.of(MultiPartConfig.Limits.fileSize(1_048_576).withFiles(2L)))
            .build());
  }

  @Override
  public void configure() {
    Booster.configure("local", config -> {
      config.appName = "my-store";
      config.providerPackage = "com.boostercloud.framework.provider.local";
      config.rockets = List.of(new BoosterWebhook(config, webhooks()).rocketForLocal());
    });
    Booster.configure("production", config -> {
      config.appName = "my-store";
      config.providerPackage = "com.boostercloud.framework.provider.azure";
      config.rockets = List.of(new BoosterWebhook(config, webhooks()).rocketForAzure());
    });
  }
}
```

```java
package com.example.shop;

import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.core.decorators.Role;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import com.boostercloud.rocket.webhook.types.WebhookEvent;
import com.boostercloud.rocket.webhook.types.WebhookHandlerReturnType;
import java.util.Map;

public class TestHandler {
  public static WebhookHandlerReturnType handle(WebhookEvent event, Register register) {
    if (!(event.body() instanceof Map<?, ?> body) || !(body.get("name") instanceof String name)) {
      throw new InvalidParameterError("Error message");          // 400 {"title":"Invalid Parameter Error",...}
    }
    register.events(new NameReceived(UUID.generate(), name));    // stored after handle returns
    return WebhookHandlerReturnType.json(Map.of("name", name));
  }
}

public class FacebookHandler {
  public static WebhookHandlerReturnType handle(WebhookEvent event, Register register) {
    // event.method() is always "POST" (as in TypeScript): tell the verification GET apart by its query
    if ("subscribe".equals(event.query().get("hub.mode"))) {
      return WebhookHandlerReturnType.text(event.query().get("hub.challenge"));
    }
    return WebhookHandlerReturnType.text("ok").withHeaders(Map.of("Test", "test header"));
  }
}

@Event
public record NameReceived(UUID id, String name) {
  public UUID entityID() { return id; }
}

@Entity
public record Name(UUID id, String value) {
  @Reduces(NameReceived.class)
  public static Name received(NameReceived event, Name current) { return new Name(event.id(), event.name()); }
}

@Role
public class Admin {}
```

```shell
curl -X POST 'http://localhost:3000/test?param1=testvalue' -H 'Content-Type: application/json' -d '{"name":"x"}'
# {"name":"x"}
```

### Parameters (`WebhookParamsEvent`)

| Member | TypeScript | Meaning |
| --- | --- | --- |
| `route` | `route` | The endpoint, without leading slash (`test`, `clients/other`). Matched **exactly and case-sensitively** against the request path (without `/api/` or `/`). Not normalised. |
| `origin` | `origin` (deprecated) | Used only when `route` is `null` (an empty `route` does not fall back). |
| `handlerClass` | `handlerClass` | A class with a **static** `handle(WebhookEvent, Register)` (it may declare only the event); never instantiated. Implementing the marker `WebhookHandlerClassInterface` is optional. |
| `allowedMethods` | `allowedMethods` | `GET`/`POST`; `null` means `[POST]`, an empty list rejects everything. |
| `authorize` | `authorize` | `null`: **no check** (public, unlike commands); `Authorize.all()`; `Authorize.roles(Admin.class, ...)` (role class simple names against the token roles); `Authorize.custom(WebhookAuthorizer)`. Builder sugar: `authorizeAll()`, `authorizeRoles(...)`, `authorize(lambda)`. |
| `multiPartConfig` | `multiPartConfig` | Busboy options: `defParamCharset`, `preservePath`, `limits.fileSize/files/parts`. |

### Handler results

| Result | Body | Headers (after `Access-Control-Allow-Origin: *` and the result's `headers`) |
| --- | --- | --- |
| `void`, `null` or another falsy value (`""`, `0`, `false`) | `""` | `Content-Type: text/plain` |
| `WebhookHandlerReturnType.text(body)` | the body as is (`null` → `""`) | `Content-Type: text/plain` |
| `WebhookHandlerReturnType.json(body)` | `JSON.stringify(body)`; a falsy body (`null`, `false`, `0`, `""`) → `""` | `Content-Type: application/json` |
| `WebhookHandlerReturnType.file(bytes, name, mimeType)` | the bytes (or string) as they are | `Content-Type: <mimeType or "">`, `Content-Disposition: attachment; filename=<name>` (not quoted) |

`withHeaders(Map)` adds response headers: they override `Access-Control-Allow-Origin` but not the type-specific ones
(the merge is case-sensitive, as the TypeScript object spread). A `CompletionStage` result is awaited. A raw
`Map` with `body`, `headers`, `responseType` (`text/plain`, `application/json`, `file`) and `file` is accepted too;
an unknown `responseType` logs `Unexpected response type. Using File` and fails without `file`. The status of a
success is always `200`; other statuses come only from thrown errors.

### `WebhookEvent`

| Member | Local | Azure |
| --- | --- | --- |
| `origin`, `route` | the configured `origin` and `route ?? origin` | same |
| `method` | always `"POST"` (TypeScript hard-codes it, also for `GET`) | same |
| `url`, `originalUrl` | path and query (`/test?param1=testvalue`) | the absolute URL |
| `headers` | lower-cased names (`header(name)` reads any case) | lower-cased, sorted |
| `query` | `qs`-parsed (nested maps and lists) | flat `URLSearchParams`, last value wins |
| `params` | `{}` | `{}` |
| `rawBody` | `byte[]` for `application/json` bodies, else `null` | the body as text |
| `body` | parsed JSON, parsed form (`application/x-www-form-urlencoded`), `byte[]` (`application/octet-stream`, up to 100 kB), else `{}` | `byte[]` for multipart, else `JSON.parse`d or the text (`""` without body) |
| `multipart` | `{ fields, files }` for `multipart/form-data`, else `null` | same |

`rawBodyBytes()`/`rawBodyText()` read `rawBody` whatever its provider type. Multipart files are fully buffered
(`WebhookParsedFile.bufferFile()`); fields come in body order, files in the order they end.

### Status codes

| Situation | Status | Body |
| --- | --- | --- |
| `Host` header or URL authority the URL parser rejects (`new URL(req.url, 'http://' + host + '/')`) | 500 | `{"title":"Illegal Argument Exception","reason":"Invalid URL"}` |
| Unknown route (also `/Test` or `/test/` for `test`) | 500 | `{"title":"Illegal State Exception","reason":"Could not find route Test in parameters."}` |
| A webhook without `route`/`origin` before the matching one | 500 | `... "reason":"Undefined route parameter [object Object]"` |
| Method not in `allowedMethods` | 500 | `... "reason":"Method GET not allowed"` |
| `authorization` header no verifier accepts (also on public webhooks) | 401 | `Not Authorized Error` / `Booster Token Expired Error` / `Booster Token Not Before Error` |
| Roles not satisfied | 401 | `{"title":"Not Authorized Error","reason":"Access denied for this resource"}` |
| Handler/authorizer throws `InvalidParameterError` / `NotAuthorizedError` / `NotFoundError` / `InvalidVersionError` | 400 / 401 / 404 / 422 | `{"title": <class title>, "reason": <message>}` |
| Malformed multipart body | 500 | Busboy's message (`Multipart: Boundary not found`, `Unexpected end of form`, `Malformed part header`) |
| A registered event no entity reduces | 404 | `Couldn't find information about event X. Is the event handled by an entity?` |
| Locally, a result header Node's `setHeader` rejects (name not a token; CR, LF, controls or characters above `U+00FF` in a value, e.g. a file name `名.txt`) | 500 | `Header name must be a valid HTTP token ["<name>"]` / `Invalid character in header content ["<name>"]` |
| Anything else thrown | 500 | `{"title": <class title>, "reason": <message>}` |

Checks run in this order: route, method, token, authorization, body parsing, handler, event storage. Events registered
by a handler are stored only after it returns (a failing handler stores nothing it did not `flush()`).

Locally, a failure is sent as `res.json(body)` without the result headers (the server's CORS middleware still adds
`Access-Control-Allow-Origin: *`); on Azure the failure headers (`Access-Control-Allow-Origin: *`,
`Content-Type: application/json`) are sent. Strings are sent as UTF-8 (`; charset=utf-8` added locally, as Express's
`res.send`), bytes as they are.

## Architecture

| TypeScript | Java | Role |
| --- | --- | --- |
| `rocket-webhook-types/src/*` | `types.*` | `WebhookParamsEvent`, `WebhookParams` (`FUNCTION_ID`, `getRoute`), `Helper.getRouteFromRequest`, `WebhookEvent`, `WebhookRequest` + `WebhookHttpRequest`, handler return types, `WebhookAPIResult` records, multipart records, enums |
| `rocket-webhook-core/src/index.ts` | `core.BoosterWebhook` | registers the rocket function, builds the descriptors |
| `webhook-dispatcher.ts` | `core.WebhookDispatcher` | the rocket function (`dispatch`) |
| `webhook-token-verifier.ts`, `webhook-authorization-checker.ts`, `webhook-authorizer-builder.ts` | same names | token and `authorize` handling on top of core's `BoosterTokenVerifier`/`BoosterAuthorizer` |
| `parse-multi-part.ts` + `busboy@1.6.0` (+ `streamsearch`) | `core.ParseMultiPart`, `core.busboy.*` | multipart parsing (Busboy's multipart parser ported line by line, pinned by Busboy's own test vectors) |
| `response/**` | `core.response.**` | `WebhookResponse`, `WebhookResponseBuilder`, the four strategies |
| `rocket-webhook-local-infrastructure` (`index.ts`, `infra.ts`, `controllers/webhook-controller.ts`) | `local.Rocket`, `local.LocalWebhook`, `local.Infra`, `local.controllers.WebhookController` | the routes on the local server |
| `rocket-webhook-azure-infrastructure` (`index.ts`, `helper.ts`, `functions/*`, `synth/*`) | `azure.Rocket`, `azure.AzureWebhook`, `azure.Helper`, `azure.functions.*`, `azure.synth.*` | function app, functions and gateway rules |

**Runtime.** `new BoosterWebhook(config, params)` calls `config.registerRocketFunction("rocket-webhook", ...)`. A
request reaches it through `boosterRocketDispatcher(rawRequest)` (core's `BoosterRocketDispatcher`), which reads the
rocket id with the provider's `rockets().rawToEnvelopes`: locally from the request property
`BOOSTER_ROCKET_FUNCTION_ID` (all rockets share the process), on Azure from the app setting of the same name (the
webhook function app has `BOOSTER_ROCKET_FUNCTION_ID=rocket-webhook`). The raw request is a map
`{ BOOSTER_ROCKET_FUNCTION_ID?, req }`; `WebhookRequest.from` reads both transports' shapes.

**Descriptor packages.** Providers load an infrastructure rocket as `<descriptor packageName>.Rocket` (a
`RocketBuilder`, see `RocketLoader`), the Java form of the npm default export:

| Descriptor | `packageName` | `Rocket.build(params)` returns |
| --- | --- | --- |
| `rocketForLocal()` (`@boostercloud/rocket-webhook-local-infrastructure`) | `com.boostercloud.rocket.webhook.local` | `LocalWebhook` (`LocalInfrastructureRocket`) |
| `rocketForAzure()` (`@boostercloud/rocket-webhook-azure-infrastructure`) | `com.boostercloud.rocket.webhook.azure` | `AzureWebhook` (`InfrastructureRocket`) |

The descriptor `parameters` is the same `List<WebhookParamsEvent>` instance given to `BoosterWebhook` (live objects:
handler classes and authorizer lambdas, never serialised).

**Local.** `LocalWebhook.mountStack(config, router, metadata)` mounts one `WebhookController` per webhook at `/` of the
local server's router (after `/sensor/health` and `/graphql`, behind the server's JSON/urlencoded parsers and CORS), so
each webhook answers `POST` and `GET` at `http://localhost:<port>/<route>` (no `/webhook` prefix; Express path rules:
case-insensitive, optional trailing slash, `HEAD` routed to `GET`; other verbs get Express's 404). The controller adds
`express.raw()` (`BodyParsers.raw()`), calls `UserAppRegistry.get().boosterRocketDispatcher({ BOOSTER_ROCKET_FUNCTION_ID:
"rocket-webhook", req })` and writes the result; when the dispatcher itself throws it answers `requestFailed` and passes
the error to `next`.

**Azure.** `AzureWebhook`:

* `mountStack` (`Synth`): an `azurerm_windows_function_app` `<resourceGroupName>webhookfunc` (construct id
  `toTerraformName(appPrefix, "webhookfunc")`) on the main service plan and storage account, with the app settings
  `WEBSITE_RUN_FROM_PACKAGE=1`, `WEBSITE_CONTENTSHARE`, `config.env`, `BOOSTER_ENV`, `COSMOSDB_CONNECTION_STRING`,
  `WEBSITE_CONTENTAZUREFILECONNECTIONSTRING`, `BOOSTER_ROCKET_FUNCTION_ID=rocket-webhook` and `BOOSTER_APP_CLASS`; and,
  on the application gateway, the raw overrides `backend_address_pool` (`rocketWebHookBackendAddressPool` →
  `<functionApp>.azurewebsites.net`), `url_path_map.0.path_rule` (`/<route>` and `/<route>/*` per webhook) and
  `rewrite_rule_set.0.rewrite_rule` (`/<route>` → `/api/<route>`, `rule_sequence` 1, 2...). Rule names are
  `toTerraformName(appPrefix, "ru"|"rw" + 23 characters of a random UUID)`, new on every synthesis.
* `mountFunctionsV4` (`Functions`, `WebhookFunction`): one package `rocket_<functionApp>.zip` with one function per
  webhook: name = route with each UTF-16 unit outside `[a-zA-Z0-9_]` → `_` (a repeated name fails the synthesis),
  `function.json` = `httpTrigger` `request` (`POST`, `GET`,
  `anonymous`, route = the webhook route, `dataType: binary`) + `$return`, entry point
  `BoosterAzureFunctions.ROCKET_HTTP_ENTRY_POINT`, default `host.json` (route prefix `api`).
* `getFunctionAppName`: `<resourceGroupName>webhookfunc` (it goes into the main app's `ROCKET_FUNCTION_APP_NAMES`; the
  webhook params have no `rocketProviderPackage`, so `ROCKET_PACKAGE_MAPPING` stays empty and the rocket does not appear
  in the rockets health indicator, as in TypeScript).

At run time `BoosterAzureFunctions.rocketHttp` (framework-provider-azure) builds the request the TypeScript generated
handler builds (`FunctionMappings.rocketHttpRequest`) and maps `{ status, headers, body }` back (object bodies as JSON,
strings and bytes as they are).

## Differences from TypeScript

Packaging and API shape:

* One Maven module for the four npm packages; the infrastructure "packages" are the Java packages
  `com.boostercloud.rocket.webhook.local` / `.azure` loaded as `<package>.Rocket` instead of npm default exports.
* `WebhookParams` (the TypeScript array type) is `List<WebhookParamsEvent>`; the `WebhookParams` class holds
  `FUNCTION_ID`, `getRoute` and `from(parameters)` (which rejects descriptor parameters that are not webhooks, where
  TypeScript would fail later). `WebhookParamsEvent` is a record with a builder.
* Handler results are sealed records with factories (`text`, `json`, `file`, `withHeaders`); raw maps shaped like the
  TypeScript literals are accepted as well. `WebhookAPIResult` is a sealed interface of two records;
  `WebhookAPISuccessResult` rejects statuses other than 200; `DEFAULT_HEADERS` is immutable (a mutable static in
  TypeScript).
* `authorize` is `Authorize<WebhookAuthorizer>`; a `WebhookAuthorizer` is synchronous and denies by throwing. Java
  cannot throw strings, so the README's `Promise.reject('Unexpected user')` (TypeScript answers `{"title":"String"}`)
  becomes `throw new NotAuthorizedError(...)`.
* The request is the neutral `WebhookHttpRequest` (instead of the Express request locally and a plain object on Azure):
  headers are single lower-cased strings, `bodyStream` replaces `req.pipe`; `WebhookRequest` keeps the other members of
  the raw request in `properties`. `WebhookEvent` is a record with `header`, `rawBodyBytes` and `rawBodyText` helpers; it
  is not an `EventInterface` (TypeScript only casts it).

Behaviour:

* Error titles follow ARCHITECTURE §7: where TypeScript throws a plain `Error` (title `Error`) or hits a `TypeError`,
  Java throws `IllegalStateException`/`IllegalArgumentException` with the same message, so the titles are
  `Illegal State Exception`/`Illegal Argument Exception`. Statuses and reasons are unchanged.
* `Webhook: request received` and `Unexpected response type. Using File` go to the Booster logger at info level
  (`[Booster]|WebhookDispatcher#dispatch: ...`) instead of `console.log`.
* Malformed multipart bodies: TypeScript has no `'error'` listener on Busboy (an uncaught exception, the request never
  answers); Java answers 500 with Busboy's message. Exceeded limits still truncate files and drop parts silently.
* Local transport: the `response.headers['Content-type']` branches of the controller (a `PassThrough` stream for the
  literal type `file`, `res.json` for `application/json`) are dead code in TypeScript because of the lower-case `t`;
  Java sends every success with `res.send`, which is what TypeScript does in practice. The only visible difference is
  a handler setting a header spelled exactly `Content-type: application/json`: TypeScript then double-encodes the JSON
  string, Java does not. A number used as a text body is sent as its JSON text (Express would treat it as the
  deprecated `res.send(status)`).
* Azure runtime: a Java worker `function.json` per webhook replaces the generated `functions.js`; the trigger binds the
  body as bytes (`dataType: binary`) so `BoosterAzureFunctions.rocketHttp` can reproduce
  `Buffer.from(await request.arrayBuffer())`. The function app runs the Java stack (`application_stack.java_version`
  instead of `node_version: '~22'`), gets `BOOSTER_APP_CLASS`, and its package has no `package.json`.
* Route resolution follows the WHATWG URL parser, including the base built from the `Host` header (an invalid one
  is a 500 `Invalid URL`, title `Illegal Argument Exception` instead of `Type Error`). Domain to ASCII is exact for
  ASCII host names; for non-ASCII and `xn--` labels it is approximated with RFC 3492 Punycode and `java.net.IDN`
  (IDNA 2003): UTS 46's joiner and bidi checks and its non-transitional `ß`/`ς` handling are not applied, and a
  non-ASCII label whose ASCII form exceeds 63 characters fails. Locally, the Jetty server answers many such requests
  itself before any route runs:
  `400 Bad HostPort` (spaces, `<`, a bad port, a broken IPv6 address), `400 Blank Host`, `400 No Host` (HTTP/1.1
  without `Host`, which Node's server also refuses) and `400 Authority!=Host` for absolute-form targets with another
  authority (Express routes those, and `event.url` is then the absolute URL). Hosts Jetty accepts, such as `a%20b`,
  get the TypeScript `Invalid URL` answer.
* Azure: two routes with the same function name (`a/b` and `a_b`) fail `mountFunctionsV4` with the Node worker's
  `A function with name "a_b" has already been registered.`; TypeScript deploys them and the function app then fails
  to start, so no webhook works.
* A `null` header value of a handler result is dropped. TypeScript sends `null` for `null`; for `undefined`,
  `res.setHeader` fails locally (`Invalid value "undefined" for header "x"`, 500) and Azure sends `undefined`.
* `dispatch` turns every `Exception` into a failure result; JVM `Error`s (out of memory...) propagate.
* `Synth` has a constructor taking the UUID source of the rule names (tests use it for golden files); the published
  rocket uses random UUIDs as TypeScript.

TypeScript behaviour kept on purpose: `event.method` is always `POST`; an unknown route and a method that is not allowed
are 500s; a token is verified even on public webhooks; a webhook without `authorize` is public; `/api/` is stripped from
local paths too (a route `api/x` can never match); a webhook named `graphql` answers `GET` only (the server's GraphQL
route takes `POST /graphql` first); Azure function names replace each UTF-16 unit outside `[a-zA-Z0-9_]` (an emoji
gives two underscores); `toTerraformName` truncation (`estproductionwebhookfunc` for app prefix `testproduction`); the
`applicationSynthStack.rocketStack ?? []` push that is lost when the bag has no list; the gateway overrides
**replace** the base gateway's backend pools, path rules and rewrite rules (also the GraphQL and sensor ones) rather
than adding to them, so verify the gateway before relying on it for anything else; unquoted `Content-Disposition` file
names; the empty `ROCKET_PACKAGE_MAPPING` entry (no rockets health); `JSON.stringify` of falsy JSON bodies being `""`.

## Not implemented

* Busboy's `application/x-www-form-urlencoded` parser. The rocket runs Busboy when the content type *contains*
  `multipart/form-data`, so it is reachable only with an urlencoded type that quotes that text in a parameter
  (`application/x-www-form-urlencoded; x="multipart/form-data"`). TypeScript then gives `multipart` the parsed form on
  Azure and an empty form locally (the server's urlencoded parser already read the body); Java answers 500
  `Unsupported content type: ...`. It was left out because no verified copy of busboy 1.6.0's `urlencoded.js` (and
  of its test vectors) was available to port and pin it against.
* The local package's `http.ts` (`HttpCodes`, `requestFailed`) is not duplicated: the controller uses the framework's
  `Http.requestFailed` (the TypeScript file is a copy of it).

## Tests

`mvn -B -pl rockets/rocket-webhook -am verify` from `java/`. The TypeScript repository has no tests (every package
declares a mocha script but there is no `test/` directory); everything here pins the behaviour read from the source.

The audit of every TypeScript source and spec item against these tests is the ledger [`PARITY.md`](PARITY.md).

* `types.*Test`: route resolution (WHATWG pathnames, 125 `Host` headers and 21 absolute URLs, all computed with Node
  24), descriptor parameters, request shapes.
* `core.*Test`: `BoosterWebhookTest` (registration, duplicate error, descriptors), `WebhookDispatcherTest` (order of
  the checks, method policy, tokens and authorizers, event conversion, registered events, results, failures),
  `ParseMultiPartTest`, `WebhookAuthorizerBuilderTest`, `WebhookTokenVerifierTest` (application-tester tokens),
  `response.*Test`, and `core.busboy.*Test` (Busboy's and streamsearch's own test vectors, whole and byte by byte).
* `local.*Test`: the rocket loaded from its descriptor, mounting, and the controller contract with in-memory responses.
* `azure.AzureWebhookSynthTest`: the real Azure synthesis with the rocket (golden `cdk.tf.json` and package listing in
  `src/test/resources/golden`, regenerated with `-Dbooster.golden.update=true`), guards, random rule names;
  `azure.functions.FunctionsTest`; `azure.AzureRuntimeContractTest`: `BoosterAzureFunctions.rocketHttp` on the
  end-to-end application with the Azure provider against an in-JVM fake Cosmos DB (stored envelopes, answers, query,
  byte-exact uploads, and the Cosmos DB master-key token of each batch re-derived from Microsoft's documented
  string-to-sign).
* `e2e.LocalEndToEndTest`: the `e2e.app` application (webhooks, entities, read models, a command, a role) started with
  `Booster.start`, the local provider over a temporary directory and the real local server on free ports; HTTP calls to
  the webhooks become events and read models visible through GraphQL; routing (`HEAD`, `graphql` shadowing, odd `Host`
  headers), request shapes, the `express.raw()` limit, responses and header validation, uploads, tokens, authorizers,
  the rockets health indicator, concurrent calls, and restarts over the same project directory (events and read
  models kept, concurrent calls on one entity losing no event).
