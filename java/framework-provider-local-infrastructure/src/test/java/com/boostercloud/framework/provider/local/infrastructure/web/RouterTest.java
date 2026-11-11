package com.boostercloud.framework.provider.local.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Express 4 routing semantics of {@link Router}. */
class RouterTest {

  private final List<String> calls = new ArrayList<>();
  private final AtomicReference<Throwable> outError = new AtomicReference<>();
  private boolean outCalled;

  private void dispatch(Router router, String method, String url) {
    dispatch(router, HttpRequest.builder(method, url).build());
  }

  private void dispatch(Router router, HttpRequest request) {
    router.handle(request, new MemoryHttpResponse(), error -> {
      outCalled = true;
      outError.set(error);
    });
  }

  private RequestHandler record(String name) {
    return (request, response, next) -> {
      calls.add(name + " " + request.baseUrl() + "|" + request.url() + " " + request.params());
      response.end();
    };
  }

  @Test
  void routesMatchTheWholePathCaseInsensitivelyWithAnOptionalTrailingSlash() {
    Router router = new Router().get("/hello", record("hello"));

    dispatch(router, "GET", "/hello");
    dispatch(router, "GET", "/HELLO/");
    dispatch(router, "GET", "/hello?x=1");

    assertThat(calls).containsExactly("hello |/hello {}", "hello |/HELLO/ {}", "hello |/hello?x=1 {}");
    calls.clear();
    dispatch(router, "GET", "/hello/world");
    dispatch(router, "GET", "/hellothere");
    assertThat(calls).isEmpty();
    assertThat(outCalled).isTrue();
    assertThat(outError.get()).isNull();
  }

  @Test
  void methodsMustMatchAndGetRoutesAnswerHead() {
    Router router = new Router().post("/x", record("post")).get("/x", record("get"));

    dispatch(router, "PUT", "/x");
    dispatch(router, "HEAD", "/x");
    dispatch(router, "POST", "/x");

    assertThat(calls).containsExactly("get |/x {}", "post |/x {}");
  }

  @Test
  void allMatchesEveryMethod() {
    Router router = new Router().all("/x", record("all"));
    dispatch(router, "DELETE", "/x");
    dispatch(router, "PATCH", "/x");
    assertThat(calls).hasSize(2);
  }

  @Test
  void namedParametersAreDecodedOneSegmentEach() {
    Router router = new Router().put("/files/:directory/:fileName", record("file"));

    dispatch(router, "PUT", "/files/client1/my%20file.txt");
    dispatch(router, "PUT", "/files/a%2Fb/c");
    dispatch(router, "PUT", "/files/a/b/c");

    assertThat(calls).containsExactly("file |/files/client1/my%20file.txt {directory=client1, fileName=my file.txt}",
        "file |/files/a%2Fb/c {directory=a/b, fileName=c}");
  }

  @Test
  void optionalParametersAndCustomPatterns() {
    Router router = new Router().get("/items/:id(\\d+)/:format?", record("item"));

    dispatch(router, "GET", "/items/42");
    dispatch(router, "GET", "/items/42/json");
    dispatch(router, "GET", "/items/abc");

    assertThat(calls).containsExactly("item |/items/42 {id=42}", "item |/items/42/json {id=42, format=json}");
  }

  @Test
  void wildcardsCaptureAnyCharactersByIndex() {
    Router router = new Router().get("/*", record("star")).get("/docs/*/:name", record("docs"));

    dispatch(router, "GET", "/");
    dispatch(router, "GET", "/booster/database");

    assertThat(calls).containsExactly("star |/ {0=}", "star |/booster/database {0=booster/database}");
    calls.clear();
    Router docs = new Router().get("/docs/*/:name", record("docs"));
    dispatch(docs, "GET", "/docs/a/b/c.txt");
    assertThat(calls).containsExactly("docs |/docs/a/b/c.txt {0=a/b, name=c.txt}");
  }

  @Test
  void literalTextIsNotARegularExpression() {
    Router router = new Router().get("/a.b+c", record("literal"));
    dispatch(router, "GET", "/a.b+c");
    dispatch(router, "GET", "/aXbbc");
    assertThat(calls).containsExactly("literal |/a.b+c {}");
  }

  @Test
  void malformedParametersBecomeAUriError() {
    Router router = new Router().get("/x/:id", record("x"));

    dispatch(router, "GET", "/x/%E0%A4%A");

    assertThat(calls).isEmpty();
    assertThat(outError.get()).isInstanceOf(URIError.class).hasMessage("Failed to decode param '%E0%A4%A'");
  }

  @Test
  void mountedMiddlewareSeesTheUrlRelativeToItsMountPoint() {
    Router health = new Router().get("/*", record("health"));
    Router router = new Router().use("/sensor/health", health);

    dispatch(router, "GET", "/sensor/health");
    dispatch(router, "GET", "/sensor/health/");
    dispatch(router, "GET", "/sensor/health/booster/database?x=1");
    dispatch(router, "GET", "/SENSOR/Health/booster");

    assertThat(calls).containsExactly(
        "health /sensor/health|/ {0=}",
        "health /sensor/health/|/ {0=}".replace("/sensor/health/|", "/sensor/health|"),
        "health /sensor/health|/booster/database?x=1 {0=booster/database}",
        "health /SENSOR/Health|/booster {0=booster}");
  }

  @Test
  void mountPathsMatchWholeSegments() {
    Router router = new Router().use("/graphql", record("graphql"));
    dispatch(router, "POST", "/graphqlx");
    assertThat(calls).isEmpty();
    dispatch(router, "POST", "/graphql/x");
    assertThat(calls).containsExactly("graphql /graphql|/x {}");
  }

  @Test
  void theUrlIsRestoredAfterAMountedHandlerCallsNext() {
    Router router = new Router()
        .use("/api", (request, response, next) -> {
          calls.add("first " + request.baseUrl() + "|" + request.url());
          next.next();
        })
        .use((request, response, next) -> {
          calls.add("second " + request.baseUrl() + "|" + request.url());
          response.end();
        });

    dispatch(router, "GET", "/api/users");

    assertThat(calls).containsExactly("first /api|/users", "second |/api/users");
  }

  @Test
  void nestedRoutersAccumulateTheBaseUrl() {
    Router inner = new Router().get("/:id", record("inner"));
    Router middle = new Router().use("/users", inner);
    Router router = new Router().use("/api", middle);

    dispatch(router, "GET", "/api/users/7");

    assertThat(calls).containsExactly("inner /api/users|/7 {id=7}");
  }

  @Test
  void errorsSkipNormalHandlersAndReachErrorHandlers() {
    RuntimeException failure = new IllegalStateException("boom");
    Router router = new Router()
        .use((request, response, next) -> {
          throw failure;
        })
        .use(record("skipped"))
        .get("/x", record("route skipped"))
        .use((error, request, response, next) -> {
          calls.add("error handler " + error.getMessage());
          response.end();
        });

    dispatch(router, "GET", "/x");

    assertThat(calls).containsExactly("error handler boom");
    assertThat(outCalled).isFalse();
  }

  @Test
  void errorHandlersAreSkippedWithoutError() {
    Router router = new Router()
        .use((error, request, response, next) -> calls.add("error handler"))
        .get("/x", record("route"));

    dispatch(router, "GET", "/x");

    assertThat(calls).containsExactly("route |/x {}");
  }

  @Test
  void unhandledErrorsLeaveTheRouter() {
    RuntimeException failure = new IllegalArgumentException("bad");
    Router router = new Router().get("/x", (request, response, next) -> next.next(failure));

    dispatch(router, "GET", "/x");

    assertThat(outError.get()).isSameAs(failure);
  }

  @Test
  void routeHandlersRunInOrderThroughNext() {
    Router router = new Router().post("/x", (request, response, next) -> {
      calls.add("one");
      next.next();
    }, (request, response, next) -> {
      calls.add("two");
      next.next();
    }).post("/x", record("three"));

    dispatch(router, "POST", "/x");

    assertThat(calls).containsExactly("one", "two", "three |/x {}");
  }

  @Test
  void paramsAreRestoredWhenLeavingTheRouter() {
    Router inner = new Router().get("/:id", (request, response, next) -> next.next());
    Router router = new Router().use("/users", inner).use((request, response, next) -> {
      calls.add("after " + request.params() + " " + request.baseUrl());
      response.end();
    });

    dispatch(router, "GET", "/users/1");

    assertThat(calls).containsExactly("after {} ");
  }

  @Test
  void aRouteNeedsAHandler() {
    assertThatThrownBy(() -> new Router().get("/x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Route.get() requires a callback function but got a [object Undefined]");
  }

  @Test
  void queryIsParsedFromTheOriginalUrl() {
    AtomicReference<Map<String, Object>> query = new AtomicReference<>();
    Router router = new Router().use("/api", new Router().get("/x", (request, response, next) -> {
      query.set(request.query());
      response.end();
    }));

    dispatch(router, "GET", "/api/x?a=1&b[c]=2");

    assertThat(query.get()).isEqualTo(Map.of("a", "1", "b", Map.of("c", "2")));
  }
}
