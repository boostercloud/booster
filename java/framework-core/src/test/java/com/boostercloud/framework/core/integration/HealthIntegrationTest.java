package com.boostercloud.framework.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.core.integration.app.AppState;
import com.boostercloud.framework.core.integration.inmemory.InMemoryRequests.ApiResponse;
import com.boostercloud.framework.types.concepts.HealthRoleAccess;
import com.boostercloud.framework.types.concepts.Authorize;
import com.boostercloud.framework.core.integration.app.roles.Admin;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The health sensor: the built-in indicators (enabled by the configuration) and the application's
 * {@code @HealthSensor}s as one tree, component paths, hidden details, the aggregated status and the global health
 * authorizer.
 */
class HealthIntegrationTest extends IntegrationTestSupport {

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> nodes(ApiResponse response) {
    return (List<Map<String, Object>>) response.body();
  }

  private static Map<String, Object> node(List<Map<String, Object>> nodes, String id) {
    return nodes.stream().filter(node -> id.equals(node.get("id"))).findFirst()
        .orElseThrow(() -> new AssertionError("No node " + id + " in " + nodes));
  }

  @Test
  void reportsTheWholeTree() {
    data(graphQL("mutation { CreateProduct(input: {sku: \"S\", displayName: \"S\", price: 1, category: \"c\"}) }",
        TestTokens.admin()));

    ApiResponse response = health(null, "");

    assertThat(response.status()).isEqualTo(200);
    List<Map<String, Object>> roots = nodes(response);
    assertThat(roots).extracting(root -> root.get("id")).containsExactly("booster", "store");
    Map<String, Object> booster = node(roots, "booster");
    assertThat(booster).containsEntry("status", "UP").containsEntry("name", "Booster");
    assertThat(listAt(booster, "components")).extracting(component -> component.get("id"))
        .containsExactly("booster/function", "booster/database");
    Map<String, Object> database = node(listAt(booster, "components"), "booster/database");
    assertThat(listAt(database, "components")).extracting(component -> component.get("id"))
        .containsExactly("booster/database/events", "booster/database/readmodels");
    Map<String, Object> events = node(listAt(database, "components"), "booster/database/events");
    assertThat(events).containsEntry("status", "UP")
        .containsEntry("details", Map.of("file", "memory://events", "count", 1));

    Map<String, Object> store = node(roots, "store");
    assertThat(store).containsEntry("status", "UP").containsEntry("name", "Store")
        .containsEntry("details", Map.of("products", 1));
    assertThat(listAt(store, "components")).singleElement().satisfies(payments -> assertThat(payments)
        .containsEntry("id", "store/payments").containsEntry("name", "Payments").containsEntry("status", "UP")
        .doesNotContainKey("details"));
  }

  @Test
  void aComponentPathSelectsOneIndicator() {
    ApiResponse response = health(null, "store/payments");

    assertThat(response.status()).isEqualTo(200);
    assertThat(nodes(response)).singleElement().satisfies(node -> assertThat(node)
        .containsEntry("id", "store/payments").containsEntry("status", "UP"));
  }

  @Test
  void anIndicatorDownMakesTheResponseUnhealthy() {
    AppState.paymentsUp = false;

    ApiResponse response = health(null, "");

    assertThat(response.status()).isEqualTo(503);
    assertThat(listAt(node(nodes(response), "store"), "components")).singleElement()
        .satisfies(payments -> assertThat(payments).containsEntry("status", "DOWN"));
  }

  @Test
  void theDatabaseIndicatorsFollowTheProvider() {
    provider.databaseUp(false);

    ApiResponse response = health(null, "booster/database/readmodels");

    assertThat(response.status()).isEqualTo(503);
    assertThat(nodes(response)).singleElement().satisfies(node -> assertThat(node).containsEntry("status", "DOWN"));
  }

  @Test
  void anUnknownComponentIsAFailure() {
    ApiResponse response = health(null, "nope");

    assertThat(response.status()).isEqualTo(500);
    assertThat(response.body()).isEqualTo(Map.of("title", "Error", "reason", "Unexpected HealthProvider id nope"));
  }

  @Test
  void theGlobalAuthorizerProtectsTheEndpoint() {
    config.sensorConfiguration.health.globalAuthorizer = new HealthRoleAccess(Authorize.roles(Admin.class));

    assertThat(health(TestTokens.admin(), "").status()).isEqualTo(200);
    ApiResponse denied = health(TestTokens.customer(), "");
    assertThat(denied.status()).isEqualTo(401);
    assertThat(denied.body()).isEqualTo(Map.of("title", "Not Authorized Error",
        "reason", "Access denied for this resource"));
    assertThat(health(null, "").status()).isEqualTo(401);
  }

  @Test
  void anInvalidTokenIsRejectedEvenWhenEverybodyIsAuthorized() {
    ApiResponse response = health(TestTokens.forgedAdmin(), "");

    assertThat(response.status()).isEqualTo(401);
    assertThat(response.body()).isEqualTo(Map.of("title", "Not Authorized Error",
        "reason", "JsonWebTokenError: invalid signature"));
  }
}
