package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.common.HttpService;
import com.boostercloud.framework.provider.azure.cosmos.CosmosException;
import com.boostercloud.framework.provider.azure.cosmos.FeedResponse;
import com.boostercloud.framework.provider.azure.cosmos.SqlQuerySpec;
import com.boostercloud.framework.provider.azure.support.CosmosMocks;
import com.boostercloud.framework.provider.azure.types.AzureHttpFunctionInput;
import com.boostercloud.framework.provider.azure.types.AzureHttpRequest;
import com.boostercloud.framework.provider.azure.types.AzureInvocationContext;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.concepts.ReadModelMetadata;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.HealthEnvelope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Tests of {@code health-adapter.ts} (no TypeScript tests exist). */
class HealthAdapterTest {

  record CartReadModel(String id) {}

  record ProductReadModel(String id) {}

  private BoosterConfig config;
  private CosmosMocks cosmos;
  private final List<String> calls = Collections.synchronizedList(new ArrayList<>());

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    cosmos = new CosmosMocks();
    when(cosmos.container.url()).thenReturn("dbs/new-booster-app-app/colls/x");
  }

  @AfterEach
  void clearEnvironment() {
    EnvironmentVariables.clearOverrides();
  }

  private HealthAdapter.HttpRequester answering(int status) {
    return (url, method, data) -> {
      calls.add(method + " " + url + (data == null ? "" : " " + data));
      return new HttpService.PostResult(status, "");
    };
  }

  @Test
  void databaseUrlsAndContainerChecks() {
    assertThat(HealthAdapter.databaseUrl(cosmos.client, config)).containsExactly("dbs/new-booster-app-app");
    assertThat(HealthAdapter.isDatabaseEventUp(cosmos.client, config)).isTrue();
    assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec("SELECT TOP 1 1 FROM c", List.of()));
  }

  @Test
  void eventsDetailsCountTheDocuments() {
    when(cosmos.iterator.fetchAll()).thenReturn(FeedResponse.of(List.of(12)));

    assertThat(HealthAdapter.databaseEventsHealthDetails(cosmos.client, config))
        .isEqualTo(obj("url", "dbs/new-booster-app-app/colls/x", "count", 12));
    assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec("SELECT VALUE COUNT(1) FROM c", List.of()));
  }

  @Test
  void anEmptyCountResultLeavesTheCountOutLikeUndefined() {
    assertThat(HealthAdapter.databaseEventsHealthDetails(cosmos.client, config))
        .isEqualTo(obj("url", "dbs/new-booster-app-app/colls/x"));
  }

  @Test
  void readModelChecksCoverEveryReadModelContainer() {
    config.readModels.put("CartReadModel", new ReadModelMetadata(CartReadModel.class, List.of(), null, List.of()));
    config.readModels.put("ProductReadModel", new ReadModelMetadata(ProductReadModel.class, List.of(), null, List.of()));
    when(cosmos.iterator.fetchAll()).thenReturn(FeedResponse.of(List.of(1)));

    assertThat(HealthAdapter.areDatabaseReadModelsUp(cosmos.client, config)).isTrue();
    assertThat(HealthAdapter.databaseReadModelsHealthDetails(cosmos.client, config)).isEqualTo(List.of(
        obj("url", "dbs/new-booster-app-app/colls/x", "count", 1), obj("url", "dbs/new-booster-app-app/colls/x", "count", 1)));
    ArgumentCaptor<String> containers = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(cosmos.database, org.mockito.Mockito.atLeast(4)).container(containers.capture());
    assertThat(containers.getAllValues()).contains("new-booster-app-app-CartReadModel", "new-booster-app-app-ProductReadModel");
  }

  @Test
  void aFailingContainerQueryRejectsTheReadModelCheck() {
    config.readModels.put("CartReadModel", new ReadModelMetadata(CartReadModel.class, List.of(), null, List.of()));
    when(cosmos.iterator.fetchAll()).thenThrow(CosmosException.of("down", 503));

    assertThatThrownBy(() -> HealthAdapter.areDatabaseReadModelsUp(cosmos.client, config)).isNotNull();
    assertThat(HealthAdapter.areDatabaseReadModelsUp(new CosmosMocks().client, new BoosterConfig("test"))).isTrue();
  }

  @Test
  void theGraphQLFunctionIsCheckedWithATypenameQuery() {
    assertThat(HealthAdapter.graphqlFunctionUrl()).isEqualTo("undefined/graphql");
    EnvironmentVariables.set("BOOSTER_REST_API_URL", "http://rg.eastus.cloudapp.azure.com/production");

    assertThat(HealthAdapter.isGraphQLFunctionUp(answering(200))).isTrue();
    assertThat(calls).containsExactly(
        "POST http://rg.eastus.cloudapp.azure.com/production/graphql {\"query\":\"query { __typename }\"}");
    assertThat(HealthAdapter.isGraphQLFunctionUp(answering(201))).isFalse();
    assertThat(HealthAdapter.isGraphQLFunctionUp((url, method, data) -> {
      throw new IllegalStateException("HTTP status code 500");
    })).isFalse();
  }

  @Test
  void rocketFunctionAppsAreCheckedFromTheMapping() {
    EnvironmentVariables.set("ROCKET_PACKAGE_MAPPING", "rocket-a:appA;;broken;:noPackage;rocket-b:appB;rocket-a:appC");

    Map<String, Boolean> result = HealthAdapter.areRocketFunctionsUp(answering(200));

    assertThat(result).containsExactly(Map.entry("rocket-a", true), Map.entry("rocket-b", true));
    assertThat(calls).containsExactlyInAnyOrder("GET https://appC.azurewebsites.net", "GET https://appB.azurewebsites.net");
    EnvironmentVariables.unset("ROCKET_PACKAGE_MAPPING");
    assertThat(HealthAdapter.areRocketFunctionsUp(answering(200))).isEmpty();
  }

  @Test
  void healthRequestsBecomeHealthEnvelopes() {
    AzureHttpFunctionInput input = new AzureHttpFunctionInput(AzureHttpRequest.of("GET",
        "https://app.azurewebsites.net/api/sensor/health/booster/database/events?x=1",
        Map.of("Authorization", "Bearer t", "Accept", "application/json"), null), AzureInvocationContext.of("inv-9"));

    HealthEnvelope envelope = HealthAdapter.rawRequestToSensorHealth(input);

    assertThat(envelope.requestID()).isEqualTo(UUID.of("inv-9"));
    assertThat(envelope.componentPath()).isEqualTo("booster/database/events?x=1");
    assertThat(envelope.token()).isEqualTo("Bearer t");
    assertThat(envelope.context().request().headers())
        .isEqualTo(Map.of("authorization", "Bearer t", "accept", "application/json"));
    assertThat(envelope.context().request().body()).isNull();
    assertThat(envelope.context().rawContext()).isSameAs(input);
  }

  @Test
  void componentPathRules() {
    assertThat(HealthAdapter.rawRequestToSensorHealthComponentPath(new AzureHttpFunctionInput(
        AzureHttpRequest.of("GET", "https://a/api/sensor/health", Map.of(), null), AzureInvocationContext.of("1"))))
        .isEmpty();
    assertThat(HealthAdapter.rawRequestToSensorHealthComponentPath(new AzureHttpFunctionInput(
        AzureHttpRequest.of("GET", "https://a/api/other", Map.of(), null), AzureInvocationContext.of("1"))))
        .isEqualTo("https://a/api/other");
    assertThat(HealthAdapter.rawRequestToSensorHealthComponentPath("x")).isEmpty();
    assertThatThrownBy(() -> HealthAdapter.rawRequestToSensorHealth(Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Invalid input type for rawRequestToSensorHealth: expected AzureHttpFunctionInput");
    assertThat(HealthAdapter.rawRequestToSensorHealth(new AzureHttpFunctionInput(
        AzureHttpRequest.of("GET", "https://a/api/sensor/health/", Map.of("authorization", ""), null),
        AzureInvocationContext.of("1"))).token()).isEmpty();
  }
}
