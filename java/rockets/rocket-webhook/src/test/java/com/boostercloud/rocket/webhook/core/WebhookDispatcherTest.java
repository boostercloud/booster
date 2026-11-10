package com.boostercloud.rocket.webhook.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.DecodedToken;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.TokenVerifier;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.rocket.webhook.support.MultipartBody;
import com.boostercloud.rocket.webhook.types.AllowedHttpMethod;
import com.boostercloud.rocket.webhook.types.MultiPartConfig;
import com.boostercloud.rocket.webhook.types.WebhookAPIErrorResult;
import com.boostercloud.rocket.webhook.types.WebhookAPIResult;
import com.boostercloud.rocket.webhook.types.WebhookAPISuccessResult;
import com.boostercloud.rocket.webhook.types.WebhookEvent;
import com.boostercloud.rocket.webhook.types.WebhookHandlerReturnType;
import com.boostercloud.rocket.webhook.types.WebhookHttpRequest;
import com.boostercloud.rocket.webhook.types.WebhookParamsEvent;
import com.boostercloud.rocket.webhook.types.WebhookRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@code webhook-dispatcher.ts} ({@code dispatch}): route lookup, method policy, token and authorization, event
 * conversion, handler call, event storage and response building, in the TypeScript order. The rocket repository has
 * no tests; these pin the behaviour read from the source (spec section 5.3).
 */
class WebhookDispatcherTest {

  /** What the handlers saw. */
  static final List<WebhookEvent> EVENTS = Collections.synchronizedList(new ArrayList<>());
  static final List<Register> REGISTERS = Collections.synchronizedList(new ArrayList<>());

  public record Pinged(UUID id, String note) {
    public UUID entityID() {
      return id;
    }
  }

  public record NotReduced(UUID id) {
    public UUID entityID() {
      return id;
    }
  }

  public record Ping(UUID id, int count) {}

  public static final class EchoHandler {
    public static WebhookHandlerReturnType handle(WebhookEvent event, Register register) {
      EVENTS.add(event);
      REGISTERS.add(register);
      return WebhookHandlerReturnType.json(Map.of("route", event.route()));
    }
  }

  public static final class OtherHandler {
    public static WebhookHandlerReturnType handle(WebhookEvent event, Register register) {
      EVENTS.add(event);
      return WebhookHandlerReturnType.text("other");
    }
  }

  public static final class VoidHandler {
    public static void handle(WebhookEvent event, Register register) {
      EVENTS.add(event);
      REGISTERS.add(register);
    }
  }

  public static final class EventsHandler {
    public static WebhookHandlerReturnType handle(WebhookEvent event, Register register) {
      REGISTERS.add(register);
      register.events(new Pinged(UUID.of("p-1"), "first"), new Pinged(UUID.of("p-2"), "second"));
      return WebhookHandlerReturnType.text("stored");
    }
  }

  public static final class RegistersThenFailsHandler {
    public static WebhookHandlerReturnType handle(WebhookEvent event, Register register) {
      register.events(new Pinged(UUID.of("p-1"), "lost"));
      throw new InvalidParameterError("Error message");
    }
  }

  public static final class UnreducedEventHandler {
    public static void handle(WebhookEvent event, Register register) {
      register.events(new NotReduced(UUID.of("n-1")));
    }
  }

  public static final class CheckedFailureHandler {
    public static void handle(WebhookEvent event, Register register) throws IOException {
      throw new IOException("disk on fire");
    }
  }

  public static final class AsyncHandler {
    public static CompletableFuture<WebhookHandlerReturnType> handle(WebhookEvent event, Register register) {
      return CompletableFuture.supplyAsync(() -> WebhookHandlerReturnType.text("later"));
    }
  }

  public static final class AsyncFailureHandler {
    public static CompletableFuture<WebhookHandlerReturnType> handle(WebhookEvent event, Register register) {
      register.events(new Pinged(UUID.of("p-9"), "lost"));
      return CompletableFuture.supplyAsync(() -> {
        throw new InvalidParameterError("rejected later");
      });
    }
  }

  public static final class EventOnlyHandler {
    public static WebhookHandlerReturnType handle(WebhookEvent event) {
      return WebhookHandlerReturnType.text(event.route());
    }
  }

  public static final class RawMapHandler {
    public static Object handle(WebhookEvent event, Register register) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("body", Map.of("name", "my_name"));
      result.put("headers", Map.of("Test", "test header"));
      result.put("responseType", "application/json");
      return result;
    }
  }

  public static final class FalsyHandler {
    static Object value;

    public static Object handle(WebhookEvent event, Register register) {
      return value;
    }
  }

  public static final class Admin {}

  public static final class Editor {}

  private BoosterConfig config;
  private ProviderEventsLibrary events;
  private final List<String> infoLines = Collections.synchronizedList(new ArrayList<>());

  @BeforeEach
  void setUp() {
    EVENTS.clear();
    REGISTERS.clear();
    config = new BoosterConfig("test");
    config.logger = new Logger() {
      @Override
      public void debug(Object message, Object... optionalParams) {}

      @Override
      public void info(Object message, Object... optionalParams) {
        StringBuilder line = new StringBuilder(String.valueOf(message));
        for (Object param : optionalParams) {
          line.append(param);
        }
        infoLines.add(line.toString());
      }

      @Override
      public void warn(Object message, Object... optionalParams) {}

      @Override
      public void error(Object message, Object... optionalParams) {}
    };
    config.eventStoreRetry.initialDelay = 1;
    config.eventStoreRetry.maxDelay = 1;
    config.eventStoreRetry.maxRetries = 1;
    ProviderLibrary provider = mock(ProviderLibrary.class);
    events = mock(ProviderEventsLibrary.class);
    when(provider.events()).thenReturn(events);
    when(events.store(anyList(), any())).thenReturn(List.of());
    config.setProvider(provider);
    config.reducers.put("Pinged", new ReducerMetadata(Ping.class, "reduce"));
  }

  private static Map<String, Object> request(WebhookHttpRequest req) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("BOOSTER_ROCKET_FUNCTION_ID", "rocket-webhook");
    request.put("req", req);
    return request;
  }

  private static WebhookHttpRequest.Builder post(String url) {
    return WebhookHttpRequest.builder("POST", url).header("host", "localhost:3000");
  }

  private WebhookAPIResult dispatch(List<WebhookParamsEvent> params, WebhookHttpRequest req) {
    return WebhookDispatcher.dispatch(config, request(req), params);
  }

  private static WebhookAPIErrorResult failure(WebhookAPIResult result) {
    assertThat(result).as("result %s", result).isInstanceOf(WebhookAPIErrorResult.class);
    return (WebhookAPIErrorResult) result;
  }

  private static WebhookAPISuccessResult success(WebhookAPIResult result) {
    assertThat(result).as("result %s", result).isInstanceOf(WebhookAPISuccessResult.class);
    return (WebhookAPISuccessResult) result;
  }

  private static void assertFailure(WebhookAPIResult result, int status, String title, String reason) {
    WebhookAPIErrorResult failure = failure(result);
    assertThat(failure.status()).as("status of %s", failure).isEqualTo(status);
    assertThat(failure.body()).isEqualTo(new WebhookAPIErrorResult.Body(title, reason));
    assertThat(failure.headers()).containsExactly(Map.entry("Access-Control-Allow-Origin", "*"),
        Map.entry("Content-Type", "application/json"));
  }

  private void useVerifier(TokenVerifier verifier) {
    config.tokenVerifiers.add(verifier);
  }

  private static TokenVerifier verifierFor(String expectedToken, UserEnvelope user) {
    return new TokenVerifier() {
      @Override
      public DecodedToken verify(String token) {
        if (!token.equals(expectedToken)) {
          throw new IllegalArgumentException("invalid signature");
        }
        return new DecodedToken(Map.of(), Map.of("sub", user.username()));
      }

      @Override
      public UserEnvelope toUserEnvelope(DecodedToken decodedToken) {
        return user;
      }
    };
  }

  @Nested
  class RouteResolution {

    @Test
    void an_unknown_route_is_a_500_failure_and_logs_the_request() {
      WebhookAPIResult result = dispatch(List.of(WebhookParamsEvent.of("test", EchoHandler.class)), post("/nope").build());

      assertFailure(result, 500, "Illegal State Exception", "Could not find route nope in parameters.");
      assertThat(EVENTS).isEmpty();
      assertThat(infoLines).containsExactly("[Booster]|WebhookDispatcher#dispatch: Webhook: request received");
    }

    @Test
    void the_api_prefix_and_absolute_urls_resolve_to_the_route() {
      List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.of("clients/other", EchoHandler.class));

      assertThat(dispatch(params, post("/api/clients/other?x=1").build()).status()).isEqualTo(200);
      assertThat(dispatch(params, WebhookHttpRequest.builder("POST",
          "https://myappdevrgwebhookfunc.azurewebsites.net/api/clients/other?x=1").build()).status()).isEqualTo(200);
      assertThat(EVENTS).hasSize(2);
    }

    @Test
    void a_host_or_url_the_url_parser_rejects_is_a_500_invalid_url_failure() {
      List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.of("test", EchoHandler.class));

      // new URL(req.url, `http://${req.headers.host}/`) throws TypeError: Invalid URL before any route is looked up
      assertFailure(dispatch(params, WebhookHttpRequest.builder("POST", "/test").header("host", "a b").build()), 500,
          "Illegal Argument Exception", "Invalid URL");
      assertFailure(dispatch(params, WebhookHttpRequest.builder("POST", "/nope").header("host", "h:99999").build()),
          500, "Illegal Argument Exception", "Invalid URL");
      assertFailure(dispatch(params, post("https://h:x/api/test").build()), 500, "Illegal Argument Exception",
          "Invalid URL");
      assertThat(EVENTS).isEmpty();
      // Without a host header the base is http://undefined/, which parses
      assertThat(dispatch(params, WebhookHttpRequest.builder("POST", "/test").build()).status()).isEqualTo(200);
    }

    @Test
    void routes_match_exactly_and_case_sensitively() {
      List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.of("test", EchoHandler.class));

      assertFailure(dispatch(params, post("/Test").build()), 500, "Illegal State Exception",
          "Could not find route Test in parameters.");
      assertFailure(dispatch(params, post("/test/").build()), 500, "Illegal State Exception",
          "Could not find route test/ in parameters.");
    }

    @Test
    void the_first_of_two_webhooks_with_the_same_route_wins() {
      List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.of("test", EchoHandler.class),
          WebhookParamsEvent.of("test", OtherHandler.class));

      WebhookAPISuccessResult result = success(dispatch(params, post("/test").build()));

      assertThat(result.body()).isEqualTo("{\"route\":\"test\"}");
    }

    @Test
    void a_webhook_without_route_before_the_match_fails_every_later_route() {
      List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.of("first", EchoHandler.class),
          WebhookParamsEvent.builder().handlerClass(EchoHandler.class).build(),
          WebhookParamsEvent.of("last", EchoHandler.class));

      assertThat(dispatch(params, post("/first").build()).status()).isEqualTo(200);
      assertFailure(dispatch(params, post("/last").build()), 500, "Illegal State Exception",
          "Undefined route parameter [object Object]");
    }

    @Test
    @SuppressWarnings("deprecation")
    void the_deprecated_origin_is_the_route_of_webhooks_without_one() {
      List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.builder().origin("legacy")
          .handlerClass(EchoHandler.class).build());

      assertThat(dispatch(params, post("/legacy").build()).status()).isEqualTo(200);
      assertThat(EVENTS.getFirst().origin()).isEqualTo("legacy");
      assertThat(EVENTS.getFirst().route()).isEqualTo("legacy");
    }
  }

  @Nested
  class MethodPolicy {

    @Test
    void only_post_is_allowed_by_default_and_the_handler_is_not_called_for_get() {
      List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.of("test", EchoHandler.class));

      assertFailure(dispatch(params, WebhookHttpRequest.builder("GET", "/test").build()), 500,
          "Illegal State Exception", "Method GET not allowed");
      assertThat(EVENTS).isEmpty();
      assertThat(dispatch(params, post("/test").build()).status()).isEqualTo(200);
    }

    @Test
    void allowed_get_requests_reach_the_handler_with_method_post_as_in_typescript() {
      List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.builder().route("test")
          .handlerClass(EchoHandler.class).allowedMethods(AllowedHttpMethod.GET, AllowedHttpMethod.POST).build());

      assertThat(dispatch(params, WebhookHttpRequest.builder("GET", "/test?a=1").build()).status()).isEqualTo(200);

      assertThat(EVENTS.getFirst().method()).isEqualTo("POST");
    }

    @Test
    void an_empty_list_rejects_every_method_and_methods_are_case_sensitive() {
      List<WebhookParamsEvent> none = List.of(WebhookParamsEvent.builder().route("test").handlerClass(EchoHandler.class)
          .allowedMethods(List.of()).build());
      List<WebhookParamsEvent> postOnly = List.of(WebhookParamsEvent.of("test", EchoHandler.class));

      assertFailure(dispatch(none, post("/test").build()), 500, "Illegal State Exception", "Method POST not allowed");
      assertFailure(dispatch(postOnly, WebhookHttpRequest.builder("post", "/test").build()), 500,
          "Illegal State Exception", "Method post not allowed");
      assertFailure(dispatch(postOnly, WebhookHttpRequest.builder("HEAD", "/test").build()), 500,
          "Illegal State Exception", "Method HEAD not allowed");
    }
  }

  @Nested
  class TokensAndAuthorization {

    private final UserEnvelope admin = UserEnvelope.builder().id("u-1").username("admin@example.com")
        .roles(List.of("Admin")).build();

    @Test
    void without_token_nor_authorize_the_webhook_is_public_and_anonymous() {
      dispatch(List.of(WebhookParamsEvent.of("test", VoidHandler.class)), post("/test").build());

      assertThat(REGISTERS.getFirst().currentUser()).isNull();
    }

    @Test
    void a_token_is_verified_also_on_public_webhooks() {
      List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.of("test", VoidHandler.class));

      assertFailure(dispatch(params, post("/test").header("authorization", "Bearer x").build()), 401,
          "Not Authorized Error", "");
      useVerifier(verifierFor("Bearer good", admin));
      assertFailure(dispatch(params, post("/test").header("authorization", "Bearer bad").build()), 401,
          "Not Authorized Error", "Error: invalid signature");
      assertThat(REGISTERS).isEmpty();
    }

    @Test
    void the_user_of_a_valid_token_reaches_the_register() {
      useVerifier(verifierFor("Bearer good", admin));

      dispatch(List.of(WebhookParamsEvent.of("test", VoidHandler.class)),
          post("/test").header("Authorization", "Bearer good").build());

      assertThat(REGISTERS.getFirst().currentUser()).isEqualTo(admin);
    }

    @Test
    void an_empty_authorization_header_is_no_token() {
      dispatch(List.of(WebhookParamsEvent.of("test", VoidHandler.class)),
          post("/test").header("authorization", "").build());

      assertThat(REGISTERS.getFirst().currentUser()).isNull();
    }

    @Test
    void authorize_all_lets_anonymous_requests_in() {
      List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.builder().route("test")
          .handlerClass(VoidHandler.class).authorizeAll().build());

      assertThat(dispatch(params, post("/test").build()).status()).isEqualTo(200);
    }

    @Test
    void roles_need_a_user_with_one_of_them() {
      useVerifier(verifierFor("Bearer admin", admin));
      UserEnvelope editor = UserEnvelope.builder().id("u-2").username("editor@example.com").roles(List.of("Editor"))
          .build();
      useVerifier(verifierFor("Bearer editor", editor));
      List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.builder().route("test")
          .handlerClass(VoidHandler.class).authorizeRoles(Admin.class).build());

      assertFailure(dispatch(params, post("/test").build()), 401, "Not Authorized Error",
          "Access denied for this resource");
      assertFailure(dispatch(params, post("/test").header("authorization", "Bearer editor").build()), 401,
          "Not Authorized Error", "Access denied for this resource");
      assertThat(dispatch(params, post("/test").header("authorization", "Bearer admin").build()).status())
          .isEqualTo(200);
      List<WebhookParamsEvent> noRoles = List.of(WebhookParamsEvent.builder().route("test")
          .handlerClass(VoidHandler.class).authorizeRoles().build());
      assertFailure(dispatch(noRoles, post("/test").header("authorization", "Bearer admin").build()), 401,
          "Not Authorized Error", "Access denied for this resource");
    }

    @Test
    void a_custom_authorizer_gets_the_user_and_the_request_and_its_error_decides_the_status() {
      useVerifier(verifierFor("Bearer admin", admin));
      AtomicReference<UserEnvelope> seenUser = new AtomicReference<>();
      AtomicReference<WebhookRequest> seenRequest = new AtomicReference<>();
      List<WebhookParamsEvent> params = List.of(
          WebhookParamsEvent.builder().route("allowed").handlerClass(VoidHandler.class)
              .authorize((user, request) -> {
                seenUser.set(user);
                seenRequest.set(request);
              }).build(),
          WebhookParamsEvent.builder().route("denied").handlerClass(VoidHandler.class)
              .authorize((user, request) -> {
                throw new NotAuthorizedError("Unexpected user");
              }).build(),
          WebhookParamsEvent.builder().route("broken").handlerClass(VoidHandler.class)
              .authorize((user, request) -> {
                throw new IllegalStateException("boom");
              }).build());

      assertThat(dispatch(params, post("/allowed").header("authorization", "Bearer admin").build()).status())
          .isEqualTo(200);
      assertThat(seenUser.get()).isEqualTo(admin);
      assertThat(seenRequest.get().req().url()).isEqualTo("/allowed");
      assertThat(seenRequest.get().properties()).containsEntry("BOOSTER_ROCKET_FUNCTION_ID", "rocket-webhook");
      assertFailure(dispatch(params, post("/denied").build()), 401, "Not Authorized Error", "Unexpected user");
      assertFailure(dispatch(params, post("/broken").build()), 500, "Illegal State Exception", "boom");
    }

    @Test
    void route_and_method_errors_come_before_token_errors() {
      List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.of("test", VoidHandler.class));

      assertThat(dispatch(params, post("/nope").header("authorization", "Bearer x").build()).status()).isEqualTo(500);
      assertFailure(dispatch(params, WebhookHttpRequest.builder("GET", "/test").header("authorization", "Bearer x")
          .build()), 500, "Illegal State Exception", "Method GET not allowed");
    }

    @Test
    void authorization_runs_before_the_body_is_parsed() {
      List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.builder().route("upload")
          .handlerClass(VoidHandler.class).authorizeRoles(Admin.class).build());
      WebhookHttpRequest malformed = post("/upload").header("content-type", "multipart/form-data").build();

      assertFailure(dispatch(params, malformed), 401, "Not Authorized Error", "Access denied for this resource");
    }
  }

  @Nested
  class EventConversion {

    @Test
    void copies_the_request_into_the_event() {
      byte[] raw = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
      WebhookHttpRequest req = post("/test?param1=testvalue").header("content-type", "application/json")
          .query(Map.of("param1", "testvalue")).rawBody(raw).body(Map.of("a", 1)).build();

      dispatch(List.of(WebhookParamsEvent.of("test", EchoHandler.class)), req);

      WebhookEvent event = EVENTS.getFirst();
      assertThat(event.origin()).isNull();
      assertThat(event.route()).isEqualTo("test");
      assertThat(event.method()).isEqualTo("POST");
      assertThat(event.url()).isEqualTo("/test?param1=testvalue");
      assertThat(event.originalUrl()).isEqualTo("/test?param1=testvalue");
      assertThat(event.headers()).isEqualTo(Map.of("host", "localhost:3000", "content-type", "application/json"));
      assertThat(event.query()).isEqualTo(Map.of("param1", "testvalue"));
      assertThat(event.params()).isEmpty();
      assertThat(event.rawBody()).isEqualTo(raw);
      assertThat(event.body()).isEqualTo(Map.of("a", 1));
      assertThat(event.multipart()).isNull();
    }

    @Test
    void parses_multipart_bodies_from_the_stream_or_the_buffered_body() {
      byte[] file = new byte[] {0, 1, 2, (byte) 0xFF, 'x'};
      byte[] body = MultipartBody.create().field("parameterName", "value")
          .file("file1", "file1.bin", "application/octet-stream", file).build();
      List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.of("upload", EchoHandler.class));

      dispatch(params, post("/upload").header("content-type", MultipartBody.CONTENT_TYPE)
          .bodyStream(() -> new ByteArrayInputStream(body)).build());
      dispatch(params, post("/upload").header("Content-Type", "Multipart/Form-Data; boundary=" + MultipartBody.BOUNDARY)
          .body(body).build());

      for (WebhookEvent event : EVENTS) {
        assertThat(event.multipart().fields()).singleElement().satisfies(field -> {
          assertThat(field.name()).isEqualTo("parameterName");
          assertThat(field.value()).isEqualTo("value");
          assertThat(field.nameTruncated()).isFalse();
          assertThat(field.valueTruncated()).isFalse();
          assertThat(field.encoding()).isEqualTo("7bit");
          assertThat(field.mimeType()).isEqualTo("text/plain");
        });
        assertThat(event.multipart().files()).singleElement().satisfies(parsed -> {
          assertThat(parsed.name()).isEqualTo("file1");
          assertThat(parsed.filename()).isEqualTo("file1.bin");
          assertThat(parsed.bufferFile()).isEqualTo(file);
          assertThat(parsed.encoding()).isEqualTo("7bit");
          assertThat(parsed.mimeType()).isEqualTo("application/octet-stream");
        });
      }
      assertThat(EVENTS).hasSize(2);
    }

    @Test
    void files_over_the_size_limit_are_truncated_silently() {
      byte[] body = MultipartBody.create().file("file1", "big.txt", "text/plain",
          "0123456789".getBytes(StandardCharsets.UTF_8)).build();
      List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.builder().route("upload")
          .handlerClass(EchoHandler.class).multiPartConfig(MultiPartConfig.of(MultiPartConfig.Limits.fileSize(4)))
          .build());

      assertThat(dispatch(params, post("/upload").header("content-type", MultipartBody.CONTENT_TYPE).body(body).build())
          .status()).isEqualTo(200);

      assertThat(EVENTS.getFirst().multipart().files().getFirst().bufferFile())
          .isEqualTo("0123".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void an_urlencoded_type_quoting_multipart_form_data_is_not_parsed_busboys_urlencoded_parser_is_not_ported() {
      List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.of("upload", EchoHandler.class));
      String type = "application/x-www-form-urlencoded; x=\"multipart/form-data\"";

      // TypeScript runs Busboy's urlencoded parser here (README "Not implemented")
      assertFailure(dispatch(params, post("/upload").header("content-type", type).body("a=1".getBytes(
          StandardCharsets.UTF_8)).build()), 500, "Illegal State Exception", "Unsupported content type: " + type);
    }

    @Test
    void malformed_multipart_requests_are_failures() {
      List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.of("upload", EchoHandler.class));

      assertFailure(dispatch(params, post("/upload").header("content-type", "multipart/form-data").build()), 500,
          "Illegal State Exception", "Multipart: Boundary not found");
      byte[] truncated = MultipartBody.create().field("a", "b").buildTruncated();
      assertFailure(dispatch(params, post("/upload").header("content-type", MultipartBody.CONTENT_TYPE)
          .body(truncated).build()), 500, "Illegal State Exception", "Unexpected end of form");
      assertThat(EVENTS).isEmpty();
    }
  }

  @Nested
  class RegisteredEvents {

    @Test
    void are_stored_after_the_handler_returns_with_the_request_id_and_the_user() {
      UserEnvelope user = UserEnvelope.builder().id("u-1").username("someone@example.com").build();
      useVerifier(verifierFor("Bearer t", user));

      WebhookAPIResult result = dispatch(List.of(WebhookParamsEvent.of("test", EventsHandler.class)),
          post("/test").header("authorization", "Bearer t").build());

      assertThat(success(result).body()).isEqualTo("stored");
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<NonPersistedEventEnvelope>> stored = ArgumentCaptor.forClass(List.class);
      verify(events).store(stored.capture(), any());
      UUID requestID = REGISTERS.getFirst().requestID();
      assertThat(stored.getValue()).hasSize(2).allSatisfy(envelope -> {
        assertThat(envelope.requestID()).isEqualTo(requestID);
        assertThat(envelope.currentUser()).isEqualTo(user);
        assertThat(envelope.typeName()).isEqualTo("Pinged");
        assertThat(envelope.entityTypeName()).isEqualTo("Ping");
      });
      assertThat(stored.getValue()).extracting(NonPersistedEventEnvelope::entityID)
          .containsExactly(UUID.of("p-1"), UUID.of("p-2"));
      assertThat(REGISTERS.getFirst().responseHeaders()).isEmpty();
    }

    @Test
    void every_request_gets_a_new_request_id() {
      List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.of("test", VoidHandler.class));

      dispatch(params, post("/test").header("x-request-id", "fixed").build());
      dispatch(params, post("/test").header("x-request-id", "fixed").build());

      assertThat(REGISTERS.get(0).requestID()).isNotEqualTo(REGISTERS.get(1).requestID());
    }

    @Test
    void are_discarded_when_the_handler_fails() {
      assertFailure(dispatch(List.of(WebhookParamsEvent.of("test", RegistersThenFailsHandler.class)),
          post("/test").build()), 400, "Invalid Parameter Error", "Error message");

      verify(events, never()).store(anyList(), any());
    }

    @Test
    void the_store_is_not_touched_without_events() {
      dispatch(List.of(WebhookParamsEvent.of("test", VoidHandler.class)), post("/test").build());

      verify(events, never()).store(anyList(), any());
    }

    @Test
    void a_storage_failure_fails_the_request_although_the_handler_succeeded() {
      assertFailure(dispatch(List.of(WebhookParamsEvent.of("test", UnreducedEventHandler.class)),
          post("/test").build()), 404, "Not Found Error",
          "Couldn't find information about event NotReduced. Is the event handled by an entity?");
    }
  }

  @Nested
  class Results {

    private WebhookAPISuccessResult call(Class<?> handler) {
      return success(dispatch(List.of(WebhookParamsEvent.of("test", handler)), post("/test").build()));
    }

    @Test
    void a_json_result() {
      WebhookAPISuccessResult result = call(EchoHandler.class);

      assertThat(result.status()).isEqualTo(200);
      assertThat(result.body()).isEqualTo("{\"route\":\"test\"}");
      assertThat(result.headers()).containsExactly(Map.entry("Access-Control-Allow-Origin", "*"),
          Map.entry("Content-Type", "application/json"));
    }

    @Test
    void a_void_handler_answers_an_empty_text() {
      WebhookAPISuccessResult result = call(VoidHandler.class);

      assertThat(result.body()).isEqualTo("");
      assertThat(result.headers()).containsExactly(Map.entry("Access-Control-Allow-Origin", "*"),
          Map.entry("Content-Type", "text/plain"));
    }

    @Test
    void falsy_results_are_empty_responses() {
      for (Object falsy : new Object[] {null, "", 0, false, 0.0}) {
        FalsyHandler.value = falsy;
        WebhookAPISuccessResult result = call(FalsyHandler.class);
        assertThat(result.body()).as("result %s", falsy).isEqualTo("");
        assertThat(result.headers()).containsEntry("Content-Type", "text/plain");
      }
    }

    @Test
    void asynchronous_results_are_awaited_and_handlers_may_take_only_the_event() {
      assertThat(call(AsyncHandler.class).body()).isEqualTo("later");
      assertThat(call(EventOnlyHandler.class).body()).isEqualTo("test");
    }

    @Test
    void a_failed_asynchronous_result_is_mapped_by_its_cause_and_stores_nothing() {
      // await handlerClass.handle(...) rejects with the error itself: its class decides the status
      assertFailure(dispatch(List.of(WebhookParamsEvent.of("test", AsyncFailureHandler.class)), post("/test").build()),
          400, "Invalid Parameter Error", "rejected later");
      verify(events, never()).store(anyList(), any());
    }

    @Test
    void a_raw_map_result_is_read_like_the_typescript_object_literal() {
      WebhookAPISuccessResult result = call(RawMapHandler.class);

      assertThat(result.body()).isEqualTo("{\"name\":\"my_name\"}");
      assertThat(result.headers()).containsExactly(Map.entry("Access-Control-Allow-Origin", "*"),
          Map.entry("Test", "test header"), Map.entry("Content-Type", "application/json"));
    }

    @Test
    void checked_exceptions_of_handlers_are_failures_too() {
      assertFailure(dispatch(List.of(WebhookParamsEvent.of("test", CheckedFailureHandler.class)),
          post("/test").build()), 500, "IO Exception", "disk on fire");
    }

    @Test
    void a_handler_class_without_handle_or_without_class_is_a_failure() {
      WebhookAPIErrorResult missingHandle =
          failure(dispatch(List.of(WebhookParamsEvent.of("test", Admin.class)), post("/test").build()));
      assertThat(missingHandle.status()).isEqualTo(500);
      assertThat(missingHandle.body().title()).isEqualTo("Illegal State Exception");
      assertThat(missingHandle.body().reason()).contains("handle");
      assertFailure(dispatch(List.of(WebhookParamsEvent.builder().route("test").build()), post("/test").build()), 500,
          "Illegal Argument Exception", "Cannot read properties of undefined (reading 'handle')");
    }

    @Test
    void a_request_without_req_is_a_failure_not_an_exception() {
      WebhookAPIResult result = WebhookDispatcher.dispatch(config, Map.of("other", 1),
          List.of(WebhookParamsEvent.of("test", VoidHandler.class)));

      assertFailure(result, 500, "Illegal Argument Exception", "Cannot read properties of undefined (reading 'url')");
    }
  }
}
