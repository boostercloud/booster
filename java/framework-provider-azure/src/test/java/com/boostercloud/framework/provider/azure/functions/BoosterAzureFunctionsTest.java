package com.boostercloud.framework.provider.azure.functions;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.provider.azure.library.AzureHttpResponse;
import com.boostercloud.framework.provider.azure.library.ScheduledAdapter;
import com.boostercloud.framework.provider.azure.library.SubscriptionModel;
import com.boostercloud.framework.provider.azure.library.WebPubSubConnectResponse;
import com.boostercloud.framework.provider.azure.library.WebPubSubMessageResponse;
import com.boostercloud.framework.provider.azure.types.AzureCosmosDBFunctionInput;
import com.boostercloud.framework.provider.azure.types.AzureEventHubFunctionInput;
import com.boostercloud.framework.provider.azure.types.AzureHttpFunctionInput;
import com.boostercloud.framework.provider.azure.types.AzureInvocationContext;
import com.boostercloud.framework.provider.azure.types.AzureWebPubSubFunctionInput;
import com.boostercloud.framework.provider.azure.types.TimerInfo;
import com.boostercloud.framework.provider.azure.types.WebPubSubConnectionContext;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.UserApp;
import com.boostercloud.framework.types.UserAppRegistry;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatusType;
import com.microsoft.azure.functions.annotation.FunctionName;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Tests of the Azure Functions entry points: input wrappers, forwarding, return values and boot. */
class BoosterAzureFunctionsTest {

  /** Captures what an entry point builds with the worker's response builder. */
  static final class FakeResponse implements HttpResponseMessage, HttpResponseMessage.Builder {
    HttpStatusType status;
    final Map<String, String> headers = new LinkedHashMap<>();
    Object body;

    FakeResponse(HttpStatusType status) {
      this.status = status;
    }

    @Override
    public HttpResponseMessage.Builder status(HttpStatusType status) {
      this.status = status;
      return this;
    }

    @Override
    public HttpResponseMessage.Builder header(String key, String value) {
      headers.put(key, value);
      return this;
    }

    @Override
    public HttpResponseMessage.Builder body(Object body) {
      this.body = body;
      return this;
    }

    @Override
    public HttpResponseMessage build() {
      return this;
    }

    @Override
    public HttpStatusType getStatus() {
      return status;
    }

    @Override
    public String getHeader(String key) {
      return headers.get(key);
    }

    @Override
    public Object getBody() {
      return body;
    }
  }

  private UserApp userApp;
  private ExecutionContext context;
  private final BoosterAzureFunctions functions = new BoosterAzureFunctions();

  @BeforeEach
  void setUp() {
    userApp = mock(UserApp.class);
    UserAppRegistry.register(userApp);
    context = context("graphql");
  }

  @AfterEach
  void tearDown() {
    UserAppRegistry.clear();
    EnvironmentVariables.clearOverrides();
    BoosterAzureApplication.useBooter(null);
    Booster.STARTED.clear();
  }

  private static ExecutionContext context(String functionName) {
    ExecutionContext context = mock(ExecutionContext.class);
    when(context.getInvocationId()).thenReturn("inv-1");
    when(context.getFunctionName()).thenReturn(functionName);
    Logger silent = Logger.getAnonymousLogger();
    silent.setUseParentHandlers(false);
    when(context.getLogger()).thenReturn(silent);
    return context;
  }

  @SuppressWarnings("unchecked")
  private static HttpRequestMessage<Optional<String>> httpRequest(HttpMethod method, String url, String body) {
    HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
    when(request.getHttpMethod()).thenReturn(method);
    when(request.getUri()).thenReturn(URI.create(url));
    when(request.getHeaders()).thenReturn(Map.of("Authorization", "Bearer t", "Content-Type", "application/json"));
    when(request.getQueryParameters()).thenReturn(Map.of("a", "1"));
    when(request.getBody()).thenReturn(Optional.ofNullable(body));
    when(request.createResponseBuilder(any(HttpStatusType.class)))
        .thenAnswer(invocation -> new FakeResponse(invocation.getArgument(0)));
    return request;
  }

  @Nested
  class HttpFunctions {

    @Test
    void graphqlWrapsTheRequestAndReturnsTheJsonResponse() {
      HttpRequestMessage<Optional<String>> request =
          httpRequest(HttpMethod.POST, "https://app.azurewebsites.net/api/graphql", "{\"query\":\"q\"}");
      when(userApp.boosterServeGraphQL(any())).thenReturn(
          new AzureHttpResponse(Map.of("Content-Type", "application/json"), 200, obj("data", obj("x", 1))));

      FakeResponse response = (FakeResponse) functions.graphql(request, context);

      ArgumentCaptor<Object> input = ArgumentCaptor.forClass(Object.class);
      verify(userApp).boosterServeGraphQL(input.capture());
      AzureHttpFunctionInput wrapper = (AzureHttpFunctionInput) input.getValue();
      assertThat(wrapper.request().method()).isEqualTo("POST");
      assertThat(wrapper.request().url()).isEqualTo("https://app.azurewebsites.net/api/graphql");
      assertThat(wrapper.request().header("authorization")).isEqualTo("Bearer t");
      assertThat(wrapper.request().body()).isEqualTo("{\"query\":\"q\"}");
      assertThat(wrapper.request().query()).isEqualTo(Map.of("a", "1"));
      assertThat(wrapper.context().invocationId()).isEqualTo("inv-1");
      assertThat(wrapper.context().nativeContext()).isSameAs(context);
      assertThat(response.getStatus().value()).isEqualTo(200);
      assertThat(response.getHeader("Content-Type")).isEqualTo("application/json");
      assertThat(response.getBody()).isEqualTo("{\"data\":{\"x\":1}}");
    }

    @Test
    void sensorHealthForwardsToBoosterHealth() {
      HttpRequestMessage<Optional<String>> request =
          httpRequest(HttpMethod.GET, "https://app/api/sensor/health/booster", null);
      when(userApp.boosterHealth(any())).thenReturn(new AzureHttpResponse(Map.of(), 503, List.of()));

      FakeResponse response = (FakeResponse) functions.sensorHealth(request, context);

      verify(userApp).boosterHealth(any(AzureHttpFunctionInput.class));
      assertThat(response.getStatus().value()).isEqualTo(503);
      assertThat(response.getBody()).isEqualTo("[]");
    }

    @Test
    void aNullResultIsAnEmpty200AndOtherResultsAreJson() {
      HttpRequestMessage<Optional<String>> request = httpRequest(HttpMethod.POST, "https://app/api/graphql", "");

      FakeResponse empty = (FakeResponse) FunctionMappings.httpResponse(request, null);
      FakeResponse other = (FakeResponse) FunctionMappings.httpResponse(request, obj("a", 1));

      assertThat(empty.getStatus().value()).isEqualTo(200);
      assertThat(empty.getBody()).isNull();
      assertThat(other.getBody()).isEqualTo("{\"a\":1}");
      assertThat(other.getHeader("Content-Type")).isEqualTo("application/json");
    }
  }

  @Nested
  class ChangeFeedAndStreamFunctions {

    @Test
    void eventHandlerAndProducerWrapTheDocuments() {
      String documents = "[{\"id\":\"1\",\"kind\":\"event\"},{\"id\":\"2\"}]";

      functions.eventHandler(documents, context);
      functions.eventProducer(documents, context);

      ArgumentCaptor<Object> dispatched = ArgumentCaptor.forClass(Object.class);
      verify(userApp).boosterEventDispatcher(dispatched.capture());
      assertThat(((AzureCosmosDBFunctionInput) dispatched.getValue()).documents())
          .containsExactly(obj("id", "1", "kind", "event"), obj("id", "2"));
      ArgumentCaptor<Object> produced = ArgumentCaptor.forClass(Object.class);
      verify(userApp).boosterProduceEventStream(produced.capture());
      assertThat(((AzureCosmosDBFunctionInput) produced.getValue()).documents()).hasSize(2);
    }

    @Test
    void eventConsumerWrapsTheMessages() {
      functions.eventConsumer(List.of("{\"a\":1}", "{\"b\":2}"), context);

      ArgumentCaptor<Object> input = ArgumentCaptor.forClass(Object.class);
      verify(userApp).boosterConsumeEventStream(input.capture());
      assertThat(((AzureEventHubFunctionInput) input.getValue()).messages()).containsExactly("{\"a\":1}", "{\"b\":2}");
    }

    @Test
    void subscriptionNotifiersTakeTheReadModelFromTheFunctionName() {
      functions.subscriptionsNotifier("[{\"id\":\"42\"}]", context("CartReadModel-subscriptions-notifier"));

      ArgumentCaptor<Object> input = ArgumentCaptor.forClass(Object.class);
      verify(userApp).boosterNotifySubscribers(input.capture());
      SubscriptionModel.SubscriptionNotifierInput notifier = (SubscriptionModel.SubscriptionNotifierInput) input.getValue();
      assertThat(notifier.typeName()).isEqualTo("CartReadModel");
      assertThat(notifier.documents()).containsExactly(obj("id", "42"));
      assertThat(SubscriptionModel.isSubscriptionNotifierInput(notifier)).isTrue();
    }

    @Test
    void scheduledCommandsTakeTheCommandFromTheFunctionName() {
      functions.scheduledCommand("{\"Schedule\":{\"AdjustForDST\":true},\"IsPastDue\":false}",
          context("scheduleFunction-CheckCarts"));

      ArgumentCaptor<Object> input = ArgumentCaptor.forClass(Object.class);
      verify(userApp).boosterTriggerScheduledCommands(input.capture());
      ScheduledAdapter.ScheduledCommandInput scheduled = (ScheduledAdapter.ScheduledCommandInput) input.getValue();
      assertThat(scheduled.typeName()).isEqualTo("CheckCarts");
      assertThat(scheduled.timer()).isEqualTo(new TimerInfo(false, new TimerInfo.Schedule(true), null));
      assertThat(ScheduledAdapter.isScheduledCommandInput(scheduled)).isTrue();
    }
  }

  @Nested
  class WebPubSubFunctions {

    private static final String CONNECT_REQUEST = "{\"connectionContext\":{\"connectionId\":\"c1\",\"hub\":\"booster\","
        + "\"eventType\":\"system\",\"eventName\":\"connect\"},\"subprotocols\":[\"graphql-ws\"]}";

    @Test
    void connectAnswersTheSubprotocol() {
      when(userApp.boosterServeGraphQL(any())).thenReturn(new WebPubSubConnectResponse("graphql-ws", null, null, null));

      String response = functions.connect(CONNECT_REQUEST, null, context);

      assertThat(response).isEqualTo("{\"subprotocol\":\"graphql-ws\"}");
      ArgumentCaptor<Object> input = ArgumentCaptor.forClass(Object.class);
      verify(userApp).boosterServeGraphQL(input.capture());
      AzureWebPubSubFunctionInput wrapper = (AzureWebPubSubFunctionInput) input.getValue();
      assertThat(wrapper.request().connectionContext())
          .isEqualTo(new WebPubSubConnectionContext("c1", null, "booster", "system", "connect"));
    }

    @Test
    void connectFailuresAreInternalErrors() {
      when(userApp.boosterServeGraphQL(any())).thenThrow(new IllegalStateException("boom"));

      assertThat(functions.connect(CONNECT_REQUEST, null, context))
          .isEqualTo("{\"code\":\"InternalError\",\"errorMessage\":\"boom\"}");
    }

    @Test
    void disconnectFailuresAnswerNothing() {
      when(userApp.boosterServeGraphQL(any())).thenThrow(new IllegalStateException("boom"));

      assertThat(functions.disconnect(CONNECT_REQUEST, null, context)).isNull();
    }

    @Test
    void messagesUseTheTriggerMetadataConnectionContextAndAnswerJsonData() {
      when(userApp.boosterServeGraphQL(any())).thenReturn(new WebPubSubMessageResponse(obj("type", "data"), "json"));

      String response = functions.messages("{\"type\":\"start\",\"id\":\"1\"}",
          "{\"connectionId\":\"c2\",\"hub\":\"booster\",\"eventType\":\"user\",\"eventName\":\"message\"}", context);

      assertThat(response).isEqualTo("{\"data\":{\"type\":\"data\"},\"dataType\":\"json\"}");
      ArgumentCaptor<Object> input = ArgumentCaptor.forClass(Object.class);
      verify(userApp).boosterServeGraphQL(input.capture());
      AzureWebPubSubFunctionInput wrapper = (AzureWebPubSubFunctionInput) input.getValue();
      assertThat(wrapper.request().data()).isEqualTo("{\"type\":\"start\",\"id\":\"1\"}");
      assertThat(wrapper.context().triggerMetadata().get("connectionContext"))
          .isEqualTo(obj("connectionId", "c2", "hub", "booster", "eventType", "user", "eventName", "message"));
    }

    @Test
    void messageFailuresAreGraphQLErrors() {
      when(userApp.boosterServeGraphQL(any())).thenThrow(new IllegalStateException(""));

      assertThat(functions.messages("x", null, context))
          .isEqualTo("{\"data\":{\"errors\":[{\"message\":\"Internal server error\"}]},\"dataType\":\"json\"}");
    }

    @Test
    void triggerValuesAreParsedLeniently() {
      assertThat(FunctionMappings.webPubSubRequest("{\"data\":\"d\",\"dataType\":\"text\"}").data()).isEqualTo("d");
      assertThat(FunctionMappings.webPubSubRequest("plain").data()).isEqualTo("plain");
      assertThat(FunctionMappings.webPubSubRequest("{\"type\":\"start\"}").data()).isEqualTo("{\"type\":\"start\"}");
      assertThat(FunctionMappings.webPubSubRequest(null).data()).isNull();
      assertThat(FunctionMappings.webPubSubTriggerMetadata("not json")).isEmpty();
    }
  }

  @Nested
  class RocketFunctions {

    @Test
    void httpRocketsGetTheWebhookRequestShapeAndMapTheResult() {
      HttpRequestMessage<Optional<String>> request =
          httpRequest(HttpMethod.POST, "https://app/api/hooks/test?a=1", "{\"x\":1}");
      when(userApp.boosterRocketDispatcher(any())).thenReturn(obj("status", 201,
          "headers", obj("Content-Type", "application/json"), "body", obj("ok", true)));

      FakeResponse response = (FakeResponse) functions.rocketHttp(request, context);

      ArgumentCaptor<Object> input = ArgumentCaptor.forClass(Object.class);
      verify(userApp).boosterRocketDispatcher(input.capture());
      assertThat(input.getValue()).isEqualTo(obj("req", obj(
          "method", "POST", "url", "https://app/api/hooks/test?a=1", "originalUrl", "https://app/api/hooks/test?a=1",
          "headers", obj("authorization", "Bearer t", "content-type", "application/json"),
          "query", Map.of("a", "1"), "params", Map.of(), "rawBody", "{\"x\":1}", "body", obj("x", 1))));
      assertThat(response.getStatus().value()).isEqualTo(201);
      assertThat(response.getBody()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void blobRocketsGetTheBlobAndItsPath() {
      functions.rocketBlob(new byte[] {1, 2}, "rocketfiles/client/a.txt", "https://st/rocketfiles/client/a.txt",
          context("fileupload_clientst"));

      ArgumentCaptor<Object> input = ArgumentCaptor.forClass(Object.class);
      verify(userApp).boosterRocketDispatcher(input.capture());
      @SuppressWarnings("unchecked")
      Map<String, Object> wrapper = (Map<String, Object>) input.getValue();
      assertThat((byte[]) wrapper.get("blob")).containsExactly(1, 2);
      assertThat(((AzureInvocationContext) wrapper.get("context")).triggerMetadata())
          .containsEntry("blobTrigger", "rocketfiles/client/a.txt");
    }
  }

  @Nested
  class Boot {

    @Test
    void bootsTheApplicationNamedByBoosterAppClassOnceThroughCoreBoosterStart() throws Exception {
      UserAppRegistry.clear();
      EnvironmentVariables.set("BOOSTER_APP_CLASS", BoosterAzureFunctionsTest.class.getName());
      UserApp booted = mock(UserApp.class);
      Booster.factory = ignored -> booted;
      CountDownLatch start = new CountDownLatch(1);
      List<Future<UserApp>> results = new ArrayList<>();
      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        for (int i = 0; i < 16; i++) {
          results.add(executor.submit(() -> {
            start.await();
            return BoosterAzureApplication.userApp();
          }));
        }
        start.countDown();
        for (Future<UserApp> result : results) {
          assertThat(result.get()).isSameAs(booted);
        }
      }

      assertThat(Booster.STARTED).containsExactly(BoosterAzureFunctionsTest.class);
    }

    @Test
    void aMissingOrUnknownApplicationClassIsReported() {
      UserAppRegistry.clear();

      assertThatThrownBy(BoosterAzureApplication::userApp)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("The BOOSTER_APP_CLASS app setting must contain the fully qualified name of the Booster "
              + "application class");
      EnvironmentVariables.set("BOOSTER_APP_CLASS", "com.example.Missing");
      assertThatThrownBy(BoosterAzureApplication::userApp)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("com.example.Missing");
    }

    @Test
    void theBootStepCanBeReplaced() {
      UserAppRegistry.clear();
      EnvironmentVariables.set("BOOSTER_APP_CLASS", BoosterAzureFunctionsTest.class.getName());
      List<Class<?>> booted = new ArrayList<>();
      BoosterAzureApplication.useBooter(appClass -> {
        booted.add(appClass);
        UserAppRegistry.register(userApp);
      });

      assertThat(BoosterAzureApplication.userApp()).isSameAs(userApp);
      assertThat(booted).containsExactly(BoosterAzureFunctionsTest.class);
      assertThat(Booster.STARTED).isEmpty();
    }
  }

  @Test
  void everyFunctionMethodIsPublicAnnotatedAndDocumentedAsAnEntryPoint() throws Exception {
    assertThat(BoosterAzureFunctions.CLASS_NAME).isEqualTo(BoosterAzureFunctions.class.getName());
    List<String> entryPoints = new ArrayList<>();
    for (java.lang.reflect.Field field : BoosterAzureFunctions.class.getFields()) {
      if (field.getName().endsWith("_ENTRY_POINT")) {
        entryPoints.add((String) field.get(null));
      }
    }
    List<String> annotated = new ArrayList<>();
    for (Method method : BoosterAzureFunctions.class.getDeclaredMethods()) {
      if (method.isAnnotationPresent(FunctionName.class)) {
        assertThat(Modifier.isPublic(method.getModifiers())).isTrue();
        annotated.add(BoosterAzureFunctions.CLASS_NAME + "." + method.getName());
      }
    }
    assertThat(entryPoints).containsExactlyInAnyOrderElementsOf(annotated);
    assertThat(BoosterAzureFunctions.ENTRY_POINTS).containsEntry("graphql", BoosterAzureFunctions.GRAPHQL_ENTRY_POINT)
        .containsEntry("sensor-health", BoosterAzureFunctions.SENSOR_HEALTH_ENTRY_POINT).hasSize(8);
  }
}
