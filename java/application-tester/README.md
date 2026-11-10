# booster-application-tester (`com.boostercloud.application.tester`)

Java port of `packages/application-tester`: the client-side helpers the integration suites use to test a
running Booster application — GraphQL clients over HTTP and WebSocket, JWTs signed with the test key, the
health URL, and the provider's counters and raw queries. It also ships the `sleep`/`waitForIt` polling
helpers of `framework-integration-tests/integration/helper/sleep.ts`, so every suite gets them from here.

## Contents

| Java type | TypeScript counterpart | Purpose |
| --- | --- | --- |
| `ApplicationTester` | `ApplicationTester` (`application-tester.ts`) | `graphql()`, `http()`, `token()`, `count()`, `query()` for one application. |
| `ProviderTestHelper` | `ProviderTestHelper` (`provider-test-helper.ts`) | what each provider's test helper implements: `outputs()`, `counters()`, `queries()`; `ProviderTestHelper.of(...)`. |
| `ApplicationOutputs` | `ApplicationOutputs` | record `(graphqlURL, websocketURL, healthURL)`. |
| `Counters` | `Counters` | blocking `long subscriptions()`, `connections()`, `readModels(name)`, `events()` (`-1` = not supported). |
| `Queries` | `Queries` | blocking `List<Map<String,Object>> events(primaryKey)`, `readModels(primaryKey, readModelName)` (raw documents). |
| `HttpHelper` | `HttpHelper` (`http-helper.ts`) | `getHealthUrl()`. |
| `TokenHelper`, `TokenOptions` | `TokenHelper`, `TokenOptions` (`token-helper.ts`) | RS256 JWTs: `forUser(email[, role[, options]])`; bundled key pair; `decode(token)`. |
| `GraphQLHelper` | `GraphQLHelper` (`graphql-helper.ts`) | `client([token])`, `clientWithSubscriptions([token])`; token = `String` or `Supplier<String>`. |
| `DisconnectableGraphQLClient` | `DisconnectableApolloClient` | `reconnect()`, `reconnectAsync()`, `disconnect()` (also `AutoCloseable`), `websocket()`. |
| `Sleep` | `sleep.ts` (integration helper) | `sleep(ms)`, `waitForIt(tryFunction, checkResult[, trialDelayMs[, timeoutMs]])`. |
| `graphql.GraphQLClient` | `ApolloClient` (as configured by the helper) | `query`, `mutate`, `subscribe`; `ErrorPolicy`; `prepare` shows what is sent. |
| `graphql.GraphQLResponse`, `GraphQLError` | `ApolloQueryResult`/`FetchResult`, entries of `graphQLErrors` | raw `data`/`errors`/`extensions`; `get("A.b[0].c")`; errors keep the server's raw object. |
| `graphql.GraphQLClientException` | `ApolloError` | `graphQLErrors()`, `networkError()`, Apollo's message rules. |
| `graphql.ServerError`, `ServerParseError`, `FetchError` | Apollo `ServerError`/`ServerParseError`, node-fetch `FetchError` | network errors with Apollo's/node-fetch's messages. |
| `graphql.GraphQLDocument`, `Operation` | `gql`, `checkDocument`, `getMainDefinition`, `getOperationName`, `getDefaultValues`, `addTypenameToDocument`; link `Operation` | document handling (graphql-java parser/printer). |
| `graphql.SubscriptionObservable`, `SubscriptionObserver`, `ObservableSubscription` | Apollo `Observable` (Concast), zen-observable `Observer`/`Subscription` | multicast subscription results; `nextResult()` = the suites' `promisifyNextSubscriptionResult`. |
| `graphql.SubscriptionClient`, `SubscriptionClientOptions`, `OperationObserver` | `SubscriptionClient`, `ClientOptions` of `subscriptions-transport-ws` 0.11.0 | the legacy `graphql-ws` protocol over `java.net.http.WebSocket`. |
| `graphql.SubscriptionConnectionException` | rejection of `clientWithSubscriptions` | `connection_error` payload, or a connection that was never acknowledged. |
| `graphql.AuthToken`, `GraphQLClientOptions` | `AuthToken`; Apollo/HttpLink settings | token string or supplier; `addTypename`, `includeUnusedVariables`, `requestTimeout`, `connectionTimeout`, `socketLogger`. |

Not ported, with reason: `effect/index.ts` (`fakeService`, `FakeServiceUtils`, `FakeOverrides`) builds sinon
fakes of Effect-TS services for the TypeScript CLI's unit tests. Java has no Effect-TS layer; the Java CLI's
services are tested with Mockito, which already provides what `fakeService` does.

## Usage

```java
ApplicationTester applicationUnderTest = new ApplicationTester(providerTestHelper);

// Queries and mutations (HTTP). Responses carry __typename like Apollo's InMemoryCache adds it.
GraphQLClient client = applicationUnderTest.graphql().client(
    applicationUnderTest.token().forUser("admin@example.com", "Admin"));
client.mutate("""
    mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
      ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
    }""", Map.of("cartId", cartId, "productId", productId, "quantity", 2));

GraphQLResponse cart = Sleep.waitForIt(
    () -> client.query(CART_QUERY, Map.of("cartId", cartId)),
    response -> response.get("CartReadModel") != null);
assertThat(cart.get("CartReadModel.cartItems[0]"))
    .isEqualTo(Map.of("__typename", "CartItem", "productId", productId, "quantity", 2));

// GraphQL errors (ApolloError)
GraphQLClientException error = catchThrowableOfType(GraphQLClientException.class,
    () -> expiredClient.query(EVENTS_QUERY));
assertThat(error.graphQLErrors()).extracting(GraphQLError::toMap).containsExactly(Map.of(
    "message", "TokenExpiredError: jwt expired\nTokenExpiredError: jwt expired",
    "extensions", Map.of("code", "BoosterTokenExpiredError")));

// Subscriptions (WebSocket); always disconnect
try (DisconnectableGraphQLClient ws = applicationUnderTest.graphql().clientWithSubscriptions(token)) {
  SubscriptionObservable observable = ws.subscribe(CART_SUBSCRIPTION, Map.of("cartId", cartId));
  observable.subscribe(result -> {});
  Sleep.waitForIt(applicationUnderTest.count()::subscriptions, count -> count == original + 1);
  ws.mutate(CHANGE_CART, Map.of("cartId", cartId, "productId", productId));
  GraphQLResponse next = observable.nextResult().get(1, TimeUnit.MINUTES);
  ws.reconnect();   // same subscriptions, re-sent on a new socket
}

// Tokens
String expired = tokens.forUser(email, "UserWithEmail", TokenOptions.builder().expiresIn(0).build());
String notYet = tokens.forUser(email, "UserWithEmail", TokenOptions.builder().notBefore(999999).build());
String custom = tokens.forUser(email, null, TokenOptions.builder().customClaim("magicWord", "opensesame").build());
```

The application under test must verify tokens with the bundled public key:
`new PublicKeyTokenVerifier("booster", TokenHelper.publicKeyPem(), "booster:role")` (the TypeScript test app
reads the same key from `assets/certs/public.key`).

## Behaviour (faithful to the TypeScript stack)

* **Tokens** reproduce `jsonwebtoken.sign` (RS256, header `{"alg":"RS256","typ":"JWT","kid":"booster"}`):
  payload `id`, `email`, custom claims, `booster:role` (string, only for a non-empty role), `iat`, `nbf`
  (`notBefore` when non-zero), `exp` (`expiresIn` whenever given, `0` included), `iss` = `booster`,
  `sub` = email, in that order; jsonwebtoken's validation errors and messages are kept. A test pins the output
  byte for byte against a token computed with Node.
* **HTTP**: `POST {"operationName","variables","query"}` with `accept: */*`, `content-type: application/json`
  and `Authorization: Bearer <token>` (none for a `null`/empty token; a supplier is evaluated per operation).
  Variables are merged over the document's defaults and unused ones are stripped (Apollo's `HttpLink`).
  Status `>= 300` → `ServerError("Response not successful: Received status code N")` (but a body with both
  `data` and `errors` surfaces the GraphQL errors first); non-JSON → `ServerParseError`; no
  `data`/`errors` → `ServerError("Server response was missing for query 'Name'.")`; I/O →
  `FetchError("request to <url> failed, reason: …")`. With the default error policy any `errors` throw a
  `GraphQLClientException` whose message is the error messages joined with `\n`.
* **`__typename`** is added to every selection set except operation roots, sets that already select a
  `__`-prefixed field and fields with `@export`, appended last (Apollo's `addTypenameToDocument`), for queries,
  mutations and subscriptions. `GraphQLClientOptions.addTypename(false)` sends the document as written.
* **WebSocket** (`subscriptions-transport-ws` 0.11.0): sub-protocol `graphql-ws`;
  `{"type":"connection_init","payload":{"Authorization":"Bearer …"}}` (or `{}`) on every (re)connection;
  `{"id":"N","type":"start","payload":{"variables","extensions":{},"operationName","query"}}`; `data`, `error`
  (observer error with the first error's message), `complete`; `{"id":"N","type":"stop"}` when the last observer
  leaves; `disconnect()` = `stop` for every operation, `{"type":"connection_terminate","payload":null}`, close.
  Reconnection (server close, drop, `reconnect()`): backoff 100 ms ×2 with 50% jitter (≤ 10 s), max connect time
  1 s ×1.2 (≤ 30 s), re-send of `connection_init` and every active `start` with the same id; `ka` keep-alive
  checks every 30 s. Messages for unknown operation ids are ignored.
* **Subscription observables** follow Apollo 3.7: the operation starts at `client.subscribe(...)`, all observers
  share it, a late observer gets the latest result (or error) at once, a result with `errors` fails the observer
  that receives it, and the operation stops for good when its last observer leaves.
* **`waitForIt`**: `true` stops, `false` or any other value (typically a diagnostic string) keeps waiting; the
  timeout (checked after a failed attempt, strictly greater than `timeoutMs`) throws
  `IllegalStateException("[waitForIt] Timeout reached")` or `"[waitForIt] Timeout reached. <diagnostic>"`
  (falsy diagnostics add nothing); an exception of the try function propagates at once; defaults 1 s / 10 min;
  `[waitForIt] start`, `.` and `[waitForIt] match!` are logged at debug level.
* **Socket logging**: `[GraphQL socket] on open`, `on "ping"`, `on "pong"`, `on message: `, `on close: `,
  `on error: ` go to `GraphQLClientOptions.socketLogger()` (`ConsoleLogger`, i.e. `console.debug`, by default).

## Differences from TypeScript

* **No Apollo Client.** `GraphQLClient` reproduces what the helper's Apollo configuration does observably
  (document checks, `__typename`, default variables, unused-variable stripping, `no-cache`, error policies,
  `ApolloError` messages, split link). There is no normalized cache, no `watchQuery`, `refetchQueries`,
  optimistic responses, `@client`/`@connection` directives or query deduplication; `stop()` is a no-op.
  Responses are raw `Map`/`List` trees (JSON numbers come back as `Integer`/`Long`/`Double`), not frozen
  JavaScript objects.
* **Promises become blocking calls.** `query`/`mutate`/`clientWithSubscriptions`/`reconnect` block (use
  `reconnectAsync()` for concurrent reconnections); counters and queries are blocking methods.
* **Connection waits are bounded.** `clientWithSubscriptions` and `reconnect()` give up after
  `GraphQLClientOptions.connectionTimeout()` (default 10 minutes) with `SubscriptionConnectionException`; the
  TypeScript promises wait forever. When `clientWithSubscriptions` fails (timeout or `connection_error`) the
  websocket client is closed, where the TypeScript client keeps reconnecting in the background.
* **`disconnect()` always ends the client.** A forced close while the client is between reconnection attempts
  cancels the pending reconnection (the JavaScript client ignores `close()` when it has no socket and comes back
  later). As in TypeScript, a client is not unusable afterwards: a new `subscribe` opens a new socket.
* **Errors are exceptions.** The `connection_error` rejection value is `SubscriptionConnectionException.payload()`;
  the `error` message delivered to a subscription observer is a `GraphQLClientException` whose message is the
  first error's message (TypeScript passes the raw first error object); malformed websocket messages are
  reported to `onError` listeners and stderr instead of throwing an uncaught exception; JavaScript `Error`s are
  `IllegalStateException`/`IllegalArgumentException` with the same messages.
* **Wire details.** The websocket is closed with status 1000 (the `ws` package sends no status); the query text
  is printed by graphql-java (`{cartId : $cartId}` instead of graphql-js' `{cartId: $cartId}`); a
  `ServerParseError` carries Jackson's parser message instead of V8's; a token supplier returning `null` sends
  `Bearer null` (JavaScript: `Bearer undefined`). Requests use HTTP/1.1 like `cross-fetch`.
* **Threading.** Each websocket client runs on its own event loop (a virtual thread that exists only while there
  is work), so observers and listeners are called one at a time on that thread; they must not block on the same
  client (`reconnect()` from a callback throws). Idle clients hold no threads.
* **Tokens.** Keys load with the JDK only (PEM PKCS#1 and PKCS#8); `iat` comes from `Dates.nowEpochSeconds()`
  (the foundation clock, replaceable in tests). Custom claims cannot hold JavaScript `undefined`; `null` values
  are serialised as `null`. Java adds `TokenHelper(String pem)`, `fromPrivateKeyFile`, `publicKeyPem()`,
  `publicKey()` and `decode()`.
* **Accessors instead of properties**: `applicationUnderTest.graphql()` for `applicationUnderTest.graphql`,
  `outputs().graphqlURL()` for `outputs.graphqlURL`. `HttpHelper` is public (TypeScript does not export it from the
  barrel but exposes it through `ApplicationTester.http`).
* **`waitForIt`** is a loop instead of recursion, uses a `Supplier` and a `Function<T, ?>` (return `Boolean` or a
  diagnostic), and its debug lines go to a configurable `Logger` (`Sleep.useLogger(Logger.NOOP)` silences them).
* **Not ported**: `effect/index.ts` (see above); `subscriptions-transport-ws` middlewares (`use`), unused by
  Booster.

## Tests

`src/test` runs every client against `FakeGraphQLServer`, an in-JVM Jetty 12 server that answers `POST /graphql`
with scripted responses and speaks the server side of `graphql-ws` on `/websocket` (it records every message and
can close, drop, stop and restart). The TypeScript package has no tests; these cover the behaviour above,
including reconnection after server restarts, keep-alive, inactivity timeouts and the golden JWT.
