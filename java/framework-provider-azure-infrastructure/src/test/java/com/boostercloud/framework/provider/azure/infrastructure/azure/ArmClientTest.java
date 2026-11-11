package com.boostercloud.framework.provider.azure.infrastructure.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.azure.infrastructure.support.FakeHttpTransport;
import com.boostercloud.framework.provider.azure.infrastructure.support.RecordingSleeper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Contract tests of the Resource Manager client: authentication, errors and the long-running operation protocol. */
class ArmClientTest {

  private static final String BASE = "https://management.azure.com/subscriptions/sub";

  private final FakeHttpTransport http = new FakeHttpTransport();
  private final RecordingSleeper sleeper = new RecordingSleeper();
  private final ArmClient arm = new ArmClient(scope -> "token-for-" + scope, "sub", http, sleeper);

  @Test
  void sends_authenticated_json_requests() {
    http.on("POST " + BASE + "/x", HttpTransport.Response.of(200, "{\"a\":1}"));
    assertThat(arm.request("POST", "/x", "2020-01-01", Map.of("k", "v"))).isEqualTo(Map.of("a", 1));
    HttpTransport.Request request = http.requests().get(0);
    assertThat(request.uri()).hasToString(BASE + "/x?api-version=2020-01-01");
    assertThat(request.headers()).containsEntry("Authorization", "Bearer token-for-https://management.azure.com/.default")
        .containsEntry("Accept", "application/json")
        .containsEntry("Content-Type", "application/json");
    assertThat(request.bodyText()).isEqualTo("{\"k\":\"v\"}");
  }

  @Test
  void maps_arm_errors() {
    http.on("DELETE " + BASE + "/resourcegroups/missing", HttpTransport.Response.of(404,
        "{\"error\":{\"code\":\"ResourceGroupNotFound\",\"message\":\"Resource group 'missing' could not be found.\"}}"));
    assertThatThrownBy(() -> arm.beginAndWait("DELETE", "/resourcegroups/missing", "2021-04-01", null))
        .isInstanceOfSatisfying(ArmError.class, error -> {
          assertThat(error.statusCode()).isEqualTo(404);
          assertThat(error.code()).isEqualTo("ResourceGroupNotFound");
          assertThat(error.getMessage()).isEqualTo("Resource group 'missing' could not be found.");
        });
    http.on("GET " + BASE + "/bad", HttpTransport.Response.of(500, "not json"));
    assertThatThrownBy(() -> arm.request("GET", "/bad", "1", null)).hasMessage("Unexpected status code: 500");
  }

  @Test
  void polls_the_azure_async_operation_and_reads_the_resource_of_a_put() {
    http.on("PUT " + BASE + "/hub", HttpTransport.Response.of(201, "{\"properties\":{\"provisioningState\":\"Updating\"}}",
        "Azure-AsyncOperation", "https://management.azure.com/operations/1", "Retry-After", "3"));
    http.on("GET https://management.azure.com/operations/1",
        HttpTransport.Response.of(200, "{\"status\":\"InProgress\"}", "Retry-After", "1"),
        HttpTransport.Response.of(200, "{\"status\":\"Succeeded\"}"));
    http.on("GET " + BASE + "/hub", HttpTransport.Response.of(200, "{\"name\":\"booster\"}"));
    assertThat(arm.beginAndWait("PUT", "/hub", "v", Map.of())).isEqualTo(Map.of("name", "booster"));
    assertThat(http.calls()).containsExactly("PUT " + BASE + "/hub?api-version=v",
        "GET https://management.azure.com/operations/1", "GET https://management.azure.com/operations/1",
        "GET " + BASE + "/hub?api-version=v");
    assertThat(sleeper.sleeps()).containsExactly(3000L, 1000L);
  }

  @Test
  void fails_when_the_async_operation_fails() {
    http.on("PUT " + BASE + "/hub", HttpTransport.Response.of(201, "",
        "Azure-AsyncOperation", "https://management.azure.com/operations/2"));
    http.on("GET https://management.azure.com/operations/2", HttpTransport.Response.of(200,
        "{\"status\":\"Failed\",\"error\":{\"code\":\"Conflict\",\"message\":\"Hub is busy\"}}"));
    assertThatThrownBy(() -> arm.beginAndWait("PUT", "/hub", "v", Map.of()))
        .isInstanceOfSatisfying(ArmError.class, error -> {
          assertThat(error.code()).isEqualTo("Conflict");
          assertThat(error.getMessage()).isEqualTo("Hub is busy");
        });
    assertThat(sleeper.sleeps()).containsExactly(ArmClient.DEFAULT_POLL_INTERVAL_MS);
  }

  @Test
  void polls_the_location_of_an_accepted_delete() {
    http.on("DELETE " + BASE + "/resourcegroups/rg", HttpTransport.Response.of(202, "",
        "Location", "https://management.azure.com/operationresults/9"));
    http.on("GET https://management.azure.com/operationresults/9",
        HttpTransport.Response.of(202, ""), HttpTransport.Response.of(200, ""));
    assertThat(arm.beginAndWait("DELETE", "/resourcegroups/rg", "2021-04-01", null)).isEmpty();
    assertThat(http.calls()).hasSize(3);
    assertThat(sleeper.sleeps()).containsExactly(2000L, 2000L);
  }

  @Test
  void returns_the_body_of_a_synchronous_post() {
    http.on("POST " + BASE + "/list", HttpTransport.Response.of(200, "{\"name\":\"app\"}"));
    assertThat(arm.beginAndWait("POST", "/list", "v", null)).isEqualTo(Map.of("name", "app"));
    assertThat(sleeper.sleeps()).isEmpty();
  }

  @Test
  void reads_the_location_after_an_async_post() {
    http.on("POST " + BASE + "/list", HttpTransport.Response.of(202, "",
        "Azure-AsyncOperation", "https://management.azure.com/operations/3", "Location",
        "https://management.azure.com/results/3"));
    http.on("GET https://management.azure.com/operations/3", HttpTransport.Response.of(200, "{\"status\":\"Succeeded\"}"));
    http.on("GET https://management.azure.com/results/3", HttpTransport.Response.of(200, "{\"name\":\"app\"}"));
    assertThat(arm.beginAndWait("POST", "/list", "v", null)).isEqualTo(Map.of("name", "app"));
  }

  @Test
  void polls_a_put_until_its_provisioning_state_is_terminal() {
    http.on("PUT " + BASE + "/thing", HttpTransport.Response.of(200,
        "{\"properties\":{\"provisioningState\":\"Creating\"}}"));
    http.on("GET " + BASE + "/thing",
        HttpTransport.Response.of(200, "{\"properties\":{\"provisioningState\":\"Creating\"}}"),
        HttpTransport.Response.of(200, "{\"properties\":{\"provisioningState\":\"Succeeded\"},\"id\":\"x\"}"));
    assertThat(arm.beginAndWait("PUT", "/thing", "v", Map.of())).containsEntry("id", "x");
    assertThat(sleeper.sleeps()).hasSize(2);
  }

  @Test
  void encodes_path_segments() {
    assertThat(ArmClient.segment("a b/c")).isEqualTo("a%20b%2Fc");
    assertThat(arm.url("/x", "1")).hasToString(BASE + "/x?api-version=1");
    assertThat(List.of(ArmClient.ENDPOINT, ArmClient.SCOPE))
        .containsExactly("https://management.azure.com", "https://management.azure.com/.default");
  }
}
