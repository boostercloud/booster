package com.boostercloud.framework.provider.azure.types;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests of the structural type guards of {@code azure-func-types.ts} and of the input records. */
class AzureFunctionTypesTest {

  private static final AzureInvocationContext CONTEXT = AzureInvocationContext.of("i");

  @Test
  void httpInputsNeedRequestContextAndAStringMethod() {
    assertThat(AzureFunctionTypes.isHttpFunctionInput(
        new AzureHttpFunctionInput(AzureHttpRequest.of("POST", "u", Map.of(), null), CONTEXT))).isTrue();
    assertThat(AzureFunctionTypes.isHttpFunctionInput(obj("request", obj("method", "GET"), "context", null))).isTrue();
    assertThat(AzureFunctionTypes.isHttpFunctionInput(new AzureHttpFunctionInput(null, CONTEXT))).isFalse();
    assertThat(AzureFunctionTypes.isHttpFunctionInput(obj("request", obj("method", 1), "context", obj()))).isFalse();
    assertThat(AzureFunctionTypes.isHttpFunctionInput(obj("request", obj("method", "GET")))).isFalse();
    assertThat(AzureFunctionTypes.isHttpFunctionInput(null)).isFalse();
  }

  @Test
  void cosmosAndEventHubInputsNeedAnArray() {
    assertThat(AzureFunctionTypes.isCosmosDBFunctionInput(new AzureCosmosDBFunctionInput(List.of(), CONTEXT))).isTrue();
    assertThat(AzureFunctionTypes.isCosmosDBFunctionInput(obj("documents", "x", "context", obj()))).isFalse();
    assertThat(AzureFunctionTypes.isEventHubFunctionInput(new AzureEventHubFunctionInput(List.of(), CONTEXT))).isTrue();
    assertThat(AzureFunctionTypes.isEventHubFunctionInput(obj("messages", List.of()))).isFalse();
    assertThat(AzureFunctionTypes.isCosmosDBFunctionInput(List.of())).isFalse();
  }

  @Test
  void timerInputsNeedABooleanIsPastDue() {
    assertThat(AzureFunctionTypes.isTimerFunctionInput(
        new AzureTimerFunctionInput(new TimerInfo(false, null, null), CONTEXT))).isTrue();
    assertThat(AzureFunctionTypes.isTimerFunctionInput(
        new AzureTimerFunctionInput(new TimerInfo(null, null, null), CONTEXT))).isFalse();
    assertThat(AzureFunctionTypes.isTimerFunctionInput(obj("timer", obj("isPastDue", "no"), "context", obj()))).isFalse();
  }

  @Test
  void webPubSubInputsNeedAConnectionIdInEitherLocation() {
    WebPubSubConnectionContext connection = new WebPubSubConnectionContext("c1", null, "booster", "user", "message");
    assertThat(AzureFunctionTypes.isWebPubSubFunctionInput(
        new AzureWebPubSubFunctionInput(new AzureWebPubSubRequest(connection, null, null), CONTEXT))).isTrue();
    assertThat(AzureFunctionTypes.isWebPubSubFunctionInput(obj("request", obj(), "context",
        obj("triggerMetadata", obj("connectionContext", obj("connectionId", "c2")))))).isTrue();
    assertThat(AzureFunctionTypes.isWebPubSubFunctionInput(obj("request", obj("connectionContext", obj()),
        "context", obj()))).isFalse();
  }

  @Test
  void theRequestConnectionContextWinsWheneverItIsPresent() {
    Map<String, Object> input = obj(
        "request", obj("connectionContext", obj("hub", "booster")),
        "context", obj("triggerMetadata", obj("connectionContext", obj("connectionId", "c2"))));

    assertThat(AzureFunctionTypes.getWebPubSubConnectionContext(input))
        .isEqualTo(new WebPubSubConnectionContext(null, null, "booster", null, null));
    assertThat(AzureFunctionTypes.getWebPubSubConnectionContext(obj("request", obj(), "context", obj()))).isNull();
  }

  @Test
  void httpRequestsBehaveLikeFetchRequests() {
    AzureHttpRequest request = AzureHttpRequest.of("POST", "u", Map.of("Content-Type", "application/json"), "{\"a\":1}");

    assertThat(request.header("content-TYPE")).isEqualTo("application/json");
    assertThat(request.headers()).containsOnlyKeys("content-type");
    assertThat(request.json()).isEqualTo(obj("a", 1));
    assertThat(AzureHttpRequest.of("GET", "u", null, null).text()).isEmpty();
    assertThatThrownBy(() -> AzureHttpRequest.of("GET", "u", null, "").json())
        .hasMessageContaining("Unexpected end of JSON input");
  }

  @Test
  void timerJsonFromTheJavaWorkerIsParsed() {
    TimerInfo timer = TimerInfo.fromJson("{\"Schedule\":{\"AdjustForDST\":true},\"ScheduleStatus\":{\"Last\":"
        + "\"2026-11-10T09:00:00Z\",\"Next\":\"2026-11-10T09:05:00Z\",\"LastUpdated\":\"2026-11-10T09:00:00Z\"},"
        + "\"IsPastDue\":true}");

    assertThat(timer).isEqualTo(new TimerInfo(true, new TimerInfo.Schedule(true), new TimerInfo.ScheduleStatus(
        "2026-11-10T09:00:00Z", "2026-11-10T09:05:00Z", "2026-11-10T09:00:00Z")));
    assertThat(TimerInfo.fromJson(null)).isEqualTo(new TimerInfo(false, null, null));
    assertThat(TimerInfo.fromJson("{\"isPastDue\":false}").isPastDue()).isFalse();
  }
}
