package com.boostercloud.framework.core.sensor.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.services.tokenverifiers.JwksUriTokenVerifier;
import com.boostercloud.framework.core.services.tokenverifiers.JwtTestKeys;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.Authorize;
import com.boostercloud.framework.types.concepts.HealthAuthorizer;
import com.boostercloud.framework.types.concepts.HealthRoleAccess;
import com.boostercloud.framework.types.envelope.HealthEnvelope;
import com.boostercloud.framework.types.errors.BoosterError;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.framework.types.provider.ProviderAPIHandling;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.provider.ProviderSensorLibrary;
import com.boostercloud.framework.types.sensor.BoosterHealthIndicatorsIds;
import com.boostercloud.framework.types.sensor.HealthIndicatorConfiguration;
import com.boostercloud.framework.types.sensor.HealthIndicatorConfigurationBase;
import com.boostercloud.framework.types.sensor.HealthIndicatorInterface;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.framework.types.sensor.HealthIndicatorResult;
import com.boostercloud.framework.types.sensor.HealthStatus;
import com.boostercloud.framework.core.sensor.health.healthindicators.DefaultBoosterHealthIndicators;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code test/sensor/health/booster-health-service.test.ts}, plus the cases the TypeScript tests leave out. */
class BoosterHealthServiceTest {

  static final String ISSUER = "auth0";

  /** A role (its simple name is what the token's role claim must contain). */
  public static class UserRole {}

  private BoosterConfig config;
  private ProviderSensorLibrary sensor;
  private ProviderAPIHandling api;
  private Object healthBody;
  private Boolean isHealthy;
  private JwtTestKeys keys;

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    config = new BoosterConfig("test");
    config.logger = mock(Logger.class);
    ProviderLibrary provider = mock(ProviderLibrary.class);
    api = mock(ProviderAPIHandling.class);
    when(provider.api()).thenReturn(api);
    when(api.requestFailed(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(api.healthRequestResult(any(), anyBoolean())).thenAnswer(invocation -> {
      healthBody = invocation.getArgument(0);
      isHealthy = invocation.getArgument(1);
      return healthBody;
    });
    config.setProvider(provider);
    sensor = mock(ProviderSensorLibrary.class);
    when(provider.sensor()).thenReturn(sensor);
    defaultSensor(null, null);
    config.sensorConfiguration.health.booster.values().forEach(indicator -> indicator.enabled = true);
    config.sensorConfiguration.health.globalAuthorizer = HealthRoleAccess.all();
  }

  @AfterEach
  void tearDown() {
    if (keys != null) {
      keys.close();
    }
  }

  private void defaultSensor(String token, String componentPath) {
    when(sensor.databaseEventsHealthDetails(config)).thenReturn(null);
    when(sensor.databaseReadModelsHealthDetails(config)).thenReturn(null);
    when(sensor.isGraphQLFunctionUp(config)).thenReturn(true);
    when(sensor.isDatabaseEventUp(config)).thenReturn(true);
    when(sensor.areDatabaseReadModelsUp(config)).thenReturn(true);
    when(sensor.databaseUrls(config)).thenReturn(List.of());
    when(sensor.graphQLFunctionUrl(config)).thenReturn("");
    when(sensor.rawRequestToHealthEnvelope(any()))
        .thenReturn(HealthEnvelope.builder().token(token).componentPath(componentPath).build());
    when(sensor.areRocketFunctionsUp(config)).thenReturn(Map.of("rocket1", true));
  }

  private HealthIndicatorConfigurationBase flags(BoosterHealthIndicatorsIds id) {
    return config.sensorConfiguration.health.get(id);
  }

  private void setFlags(boolean enabled, boolean details, boolean showChildren, BoosterHealthIndicatorsIds... ids) {
    for (BoosterHealthIndicatorsIds id : ids) {
      flags(id).enabled = enabled;
      flags(id).details = details;
      flags(id).showChildren = showChildren;
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> boosterHealthBody() {
    Object result = new BoosterHealthService(config).boosterHealth(null);
    assertThat(result).isInstanceOf(List.class);
    return (List<Map<String, Object>>) result;
  }

  /** The TypeScript helper: the first element of the response. */
  private Map<String, Object> boosterHealth() {
    List<Map<String, Object>> body = boosterHealthBody();
    return body.isEmpty() ? null : body.get(0);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> component(Map<String, Object> result, String id) {
    List<Map<String, Object>> components = (List<Map<String, Object>>) result.get("components");
    if (components == null) {
      return null;
    }
    return components.stream().filter(element -> id.equals(element.get("id"))).findFirst().orElse(null);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> details(Map<String, Object> result) {
    return (Map<String, Object>) result.get("details");
  }

  private static void expectDefaultResult(Map<String, Object> result, String status, String id, String name,
      int componentsLength) {
    assertThat(result.get("id")).isEqualTo(id);
    assertThat(result.get("status")).isEqualTo(status);
    assertThat(result.get("name")).isEqualTo(name);
    if (componentsLength == 0) {
      assertThat(result).doesNotContainKey("components");
    } else {
      assertThat((List<?>) result.get("components")).hasSize(componentsLength);
    }
  }

  private static void expectBooster(Map<String, Object> boosterResult, String version, String status) {
    expectDefaultResult(boosterResult, status, "booster", "Booster", 2);
    assertThat(details(boosterResult).get("boosterVersion")).isEqualTo(version);
  }

  @SuppressWarnings("unchecked")
  private static void expectBoosterFunction(Map<String, Object> boosterFunction, String url, String status) {
    expectDefaultResult(boosterFunction, status, "booster/function", "Booster Function", 0);
    List<Map<String, Object>> cpus = (List<Map<String, Object>>) details(boosterFunction).get("cpus");
    assertThat(cpus).isNotEmpty();
    assertThat((List<?>) cpus.get(0).get("timesPercentages")).isNotEmpty();
    Map<String, Object> memory = (Map<String, Object>) details(boosterFunction).get("memory");
    assertThat(((Number) memory.get("totalBytes")).longValue()).isPositive();
    assertThat(((Number) memory.get("freeBytes")).longValue()).isPositive();
    assertThat(details(boosterFunction).get("graphQL_url")).isEqualTo(url);
  }

  private static void expectBoosterDatabase(Map<String, Object> boosterDatabase, String status) {
    expectDefaultResult(boosterDatabase, status, "booster/database", "Booster Database", 2);
    assertThat(boosterDatabase.get("details")).isNotNull();
  }

  private static void expectDatabaseEvents(Map<String, Object> databaseEvents, String status, Object details) {
    expectDefaultResult(databaseEvents, status, "booster/database/events", "Booster Database Events", 0);
    assertThat(databaseEvents.get("details")).isEqualTo(details);
  }

  private static void expectDatabaseReadModels(Map<String, Object> databaseReadModels, String status, Object details) {
    expectDefaultResult(databaseReadModels, status, "booster/database/readmodels", "Booster Database ReadModels", 0);
    assertThat(databaseReadModels.get("details")).isEqualTo(details);
  }

  private void expectWholeTree(String expectedStatus, Object databaseDetails) {
    Map<String, Object> boosterResult = boosterHealth();
    Map<String, Object> boosterFunction = component(boosterResult, "booster/function");
    Map<String, Object> boosterDatabase = component(boosterResult, "booster/database");
    expectBooster(boosterResult, "", expectedStatus);
    expectBoosterFunction(boosterFunction, "", expectedStatus);
    expectBoosterDatabase(boosterDatabase, expectedStatus);
    expectDatabaseEvents(component(boosterDatabase, "booster/database/events"), expectedStatus, databaseDetails);
    expectDatabaseReadModels(component(boosterDatabase, "booster/database/readmodels"), expectedStatus, databaseDetails);
  }

  @Test
  void allIndicatorsAreUp() {
    expectWholeTree("UP", null);
  }

  @Test
  void allIndicatorsAreDown() {
    when(sensor.isGraphQLFunctionUp(config)).thenReturn(false);
    when(sensor.isDatabaseEventUp(config)).thenReturn(false);
    when(sensor.areDatabaseReadModelsUp(config)).thenReturn(false);

    expectWholeTree("DOWN", null);
  }

  @Test
  void detailsAreProcessed() {
    when(sensor.databaseEventsHealthDetails(config)).thenReturn(Map.of("test", true));
    when(sensor.databaseReadModelsHealthDetails(config)).thenReturn(Map.of("test", true));

    expectWholeTree("UP", Map.of("test", true));
  }

  private String token(String role) {
    keys = new JwtTestKeys();
    String jwksUri = keys.startJwksServer();
    config.tokenVerifiers = List.of(new JwksUriTokenVerifier(ISSUER, jwksUri));
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("sub", UUID.randomUUID().toString());
    claims.put("iss", ISSUER);
    claims.put("custom:role", role);
    claims.put("extraParam", "claims");
    claims.put("anotherParam", 111);
    claims.put("email", "user@example.com");
    claims.put("phoneNumber", "+34600000000");
    return keys.token(claims);
  }

  @Test
  void validatesWithTheExpectedRole() {
    defaultSensor(token("UserRole"), null);
    config.sensorConfiguration.health.globalAuthorizer = new HealthRoleAccess(Authorize.roles(UserRole.class));

    expectBooster(boosterHealth(), "", "UP");
  }

  @Test
  void validationFailsWithTheWrongRole() {
    defaultSensor(token("UserRole1"), null);
    config.sensorConfiguration.health.globalAuthorizer = new HealthRoleAccess(Authorize.roles(UserRole.class));

    Object result = new BoosterHealthService(config).boosterHealth(null);

    assertThat(result).isInstanceOf(NotAuthorizedError.class);
    assertThat(((BoosterError) result).code()).isEqualTo("NotAuthorizedError");
    verify(api, never()).healthRequestResult(any(), anyBoolean());
  }

  @Test
  void onlyRootEnabledAndWithoutChildrenAndDetails() {
    setFlags(true, false, false, BoosterHealthIndicatorsIds.ROOT);
    setFlags(false, false, false, BoosterHealthIndicatorsIds.DATABASE, BoosterHealthIndicatorsIds.DATABASE_EVENTS,
        BoosterHealthIndicatorsIds.DATABASE_READ_MODELS, BoosterHealthIndicatorsIds.FUNCTION);

    Map<String, Object> boosterResult = boosterHealth();

    expectDefaultResult(boosterResult, "UP", "booster", "Booster", 0);
    assertThat(boosterResult).doesNotContainKey("details");
    assertThat(component(boosterResult, "booster/database")).isNull();
    assertThat(component(boosterResult, "booster/function")).isNull();
  }

  @Test
  void ifTheParentIsDisabledTheChildrenAreDisabled() {
    defaultSensor(null, "booster/database/readmodels");
    setFlags(false, false, false, BoosterHealthIndicatorsIds.ROOT, BoosterHealthIndicatorsIds.DATABASE,
        BoosterHealthIndicatorsIds.DATABASE_EVENTS, BoosterHealthIndicatorsIds.FUNCTION);
    setFlags(true, false, false, BoosterHealthIndicatorsIds.DATABASE_READ_MODELS);

    assertThat(boosterHealth()).isNull();
    assertThat(isHealthy).isTrue();
  }

  @Test
  void onlyReadModelsEnabledAndWithoutChildrenAndDetails() {
    defaultSensor(null, "booster/database/readmodels");
    setFlags(true, false, false, BoosterHealthIndicatorsIds.ROOT, BoosterHealthIndicatorsIds.DATABASE,
        BoosterHealthIndicatorsIds.DATABASE_READ_MODELS);
    setFlags(false, false, false, BoosterHealthIndicatorsIds.DATABASE_EVENTS, BoosterHealthIndicatorsIds.FUNCTION);

    expectDatabaseReadModels(boosterHealth(), "UP", null);
  }

  @Nested
  class HealthResponseStatus {
    @Test
    void isHealthyWhenAllComponentsAreUp() {
      boosterHealth();
      assertThat(isHealthy).isTrue();
    }

    @Test
    void isNotHealthyWhenAnyComponentIsDown() {
      when(sensor.isGraphQLFunctionUp(config)).thenReturn(false);
      boosterHealth();
      assertThat(isHealthy).isFalse();
    }

    @Test
    void isHealthyWhenRocketsAreUnknown() {
      when(sensor.areRocketFunctionsUp(config)).thenReturn(Map.of());
      boosterHealth();
      assertThat(isHealthy).isTrue();
    }

    @Test
    void isNotHealthyWhenRocketsAreDown() {
      when(sensor.areRocketFunctionsUp(config)).thenReturn(Map.of("rocket1", false));
      boosterHealth();
      assertThat(isHealthy).isFalse();
    }

    @Test
    void isHealthyWhenRocketsAreUp() {
      when(sensor.areRocketFunctionsUp(config)).thenReturn(Map.of("rocket1", true));
      boosterHealth();
      assertThat(isHealthy).isTrue();
    }

    @Test
    void aDownComponentHiddenByShowChildrenDoesNotCount() {
      registerUserIndicator("booster/down", "Down", DownIndicator.class);
      config.userHealthIndicators.get("booster").healthIndicatorConfiguration().showChildren = false;

      List<Map<String, Object>> body = boosterHealthBody();

      assertThat(body).extracting(node -> node.get("id")).containsExactly("booster", "rockets");
      assertThat(body.get(0)).doesNotContainKey("components");
      assertThat(isHealthy).isTrue();
    }

    @Test
    void aVisibleDownUserComponentMakesTheResponseUnhealthy() {
      registerUserIndicator("booster/down", "Down", DownIndicator.class);

      boosterHealthBody();

      assertThat(isHealthy).isFalse();
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Behaviour without a TypeScript test (spec 09 §3.8)
  // ---------------------------------------------------------------------------------------------------------------

  @Test
  void theRootRequestListsEveryRootIndicatorInRegistryOrder() {
    when(sensor.areRocketFunctionsUp(config)).thenReturn(Map.of());

    List<Map<String, Object>> body = boosterHealthBody();

    assertThat(body).extracting(node -> node.get("id")).containsExactly("booster", "rockets");
    assertThat(body.get(1)).isEqualTo(Map.of("status", "UNKNOWN", "name", "Rockets", "id", "rockets", "details",
        Map.of("reason", "No Rockets found")));
    assertThat(isHealthy).isTrue();
  }

  @Test
  void everythingDisabledByDefaultGivesAnEmptyHealthyResponse() {
    config.sensorConfiguration.health.booster.values().forEach(indicator -> indicator.enabled = false);

    assertThat(boosterHealthBody()).isEmpty();
    assertThat(isHealthy).isTrue();
  }

  @Test
  void aRocketPathResolvesOneRocket() {
    defaultSensor(null, "rockets/rocket1");
    when(sensor.areRocketFunctionsUp(config)).thenReturn(Map.of("rocket1", true, "rocket2", false));

    List<Map<String, Object>> body = boosterHealthBody();

    assertThat(body).containsExactly(Map.of("status", "UP", "name", "rocket1", "id", "rockets/rocket1"));
    assertThat(isHealthy).isTrue();
    // The registry is not modified
    assertThat(DefaultBoosterHealthIndicators.defaultBoosterHealthIndicators(config).get("rockets")
        .healthIndicatorConfiguration().id).isEqualTo("rockets");
  }

  @Test
  void aRocketPathWithoutRocketsIsUnknownAndUnhealthyBecauseItsIdIsThePath() {
    defaultSensor(null, "rockets/rocket1");
    when(sensor.areRocketFunctionsUp(config)).thenReturn(Map.of());

    List<Map<String, Object>> body = boosterHealthBody();

    assertThat(body).hasSize(1);
    assertThat(body.get(0)).containsEntry("status", "UNKNOWN").containsEntry("name", "Rockets").containsEntry("id", "rockets/rocket1");
    assertThat(isHealthy).isFalse();
  }

  @Test
  void anUnknownRocketFailsTheRequest() {
    defaultSensor(null, "rockets/nope");

    Object result = new BoosterHealthService(config).boosterHealth(null);

    assertThat(result).isInstanceOf(IllegalStateException.class);
    assertThat(((Throwable) result).getMessage()).isEqualTo("Rocket \"nope\" not found");
  }

  @Test
  void aRocketPathWithoutRocketsIndicatorFails() {
    defaultSensor(null, "rockets/rocket1");
    config.userHealthIndicators = new LinkedHashMap<>(Map.of("custom",
        new HealthIndicatorMetadata(UpIndicator.class, new HealthIndicatorConfiguration("custom", "Custom", true, true, true))));

    Object result = new BoosterHealthService(config).boosterHealth(null);

    assertThat(((Throwable) result).getMessage()).isEqualTo("Rockets health provider not found");
  }

  @Test
  void anUnknownComponentPathFails() {
    defaultSensor(null, "booster/nothing");

    Object result = new BoosterHealthService(config).boosterHealth(null);

    assertThat(result).isInstanceOf(IllegalStateException.class);
    assertThat(((Throwable) result).getMessage()).isEqualTo("Unexpected HealthProvider id booster/nothing");
  }

  @Test
  void aSubtreeRequestReturnsThatComponentWithItsChildren() {
    defaultSensor(null, "booster/database");

    List<Map<String, Object>> body = boosterHealthBody();

    assertThat(body).hasSize(1);
    expectBoosterDatabase(body.get(0), "UP");
  }

  @Test
  void aDisabledChildLeavesAnEmptyComponentsList() {
    defaultSensor(null, "booster/database");
    flags(BoosterHealthIndicatorsIds.DATABASE_EVENTS).enabled = false;
    flags(BoosterHealthIndicatorsIds.DATABASE_READ_MODELS).enabled = false;

    Map<String, Object> database = boosterHealth();

    assertThat(database.get("components")).isEqualTo(List.of());
  }

  @Test
  void anInvalidTokenFailsEvenWhenEveryoneIsAuthorized() {
    token("UserRole");
    defaultSensor("not a token", null);

    Object result = new BoosterHealthService(config).boosterHealth(null);

    assertThat(result).isInstanceOf(Throwable.class);
    verify(api, never()).healthRequestResult(any(), anyBoolean());
  }

  /** A custom authorizer receives the user and the envelope. */
  public static class DenyAll implements HealthAuthorizer {
    @Override
    public void authorize(com.boostercloud.framework.types.envelope.UserEnvelope currentUser, HealthEnvelope healthEnvelope) {
      throw new NotAuthorizedError("Health is private: " + healthEnvelope.componentPath());
    }
  }

  @Test
  void aCustomAuthorizerCanDeny() {
    defaultSensor(null, "booster");
    config.sensorConfiguration.health.globalAuthorizer = new HealthRoleAccess(Authorize.custom(new DenyAll()));

    Object result = new BoosterHealthService(config).boosterHealth(null);

    assertThat(((Throwable) result).getMessage()).isEqualTo("Health is private: booster");
  }

  /** User indicators. */
  public static class UpIndicator implements HealthIndicatorInterface {
    static final AtomicInteger INSTANCES = new AtomicInteger();

    public UpIndicator() {
      INSTANCES.incrementAndGet();
    }

    @Override
    public HealthIndicatorResult health(BoosterConfig config, HealthIndicatorMetadata metadata) {
      return new HealthIndicatorResult(HealthStatus.UP, Map.of("id", metadata.healthIndicatorConfiguration().id));
    }
  }

  /** Returns OUT_OF_SERVICE with an extra property. */
  public static class OutOfServiceIndicator {
    public Map<String, Object> health(BoosterConfig config, HealthIndicatorMetadata metadata) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("status", "OUT_OF_SERVICE");
      result.put("extra", "kept");
      return result;
    }
  }

  /** Always down. */
  public static class DownIndicator implements HealthIndicatorInterface {
    @Override
    public HealthIndicatorResult health(BoosterConfig config, HealthIndicatorMetadata metadata) {
      return HealthIndicatorResult.of(HealthStatus.DOWN);
    }
  }

  /** Throws. */
  public static class FailingIndicator implements HealthIndicatorInterface {
    @Override
    public HealthIndicatorResult health(BoosterConfig config, HealthIndicatorMetadata metadata) {
      throw new IllegalStateException("indicator failure");
    }
  }

  private void registerUserIndicator(String id, String name, Class<?> type) {
    if (config.userHealthIndicators.isEmpty()) {
      config.userHealthIndicators = DefaultBoosterHealthIndicators.defaultBoosterHealthIndicators(config);
    }
    config.userHealthIndicators.put(id, new HealthIndicatorMetadata(type, new HealthIndicatorConfiguration(id, name, true, true, true)));
  }

  @Test
  void userIndicatorsExtendTheBuiltInTreeAndKeepExtraProperties() {
    registerUserIndicator("booster/database/extra", "Extra", UpIndicator.class);
    registerUserIndicator("booster/database/outofservice", "Out", OutOfServiceIndicator.class);
    defaultSensor(null, "booster/database");

    Map<String, Object> database = boosterHealth();

    assertThat((List<?>) database.get("components")).hasSize(4);
    assertThat(component(database, "booster/database/extra"))
        .isEqualTo(Map.of("status", "UP", "details", Map.of("id", "booster/database/extra"), "name", "Extra", "id",
            "booster/database/extra"));
    assertThat(component(database, "booster/database/outofservice"))
        .containsEntry("extra", "kept").containsEntry("status", "OUT_OF_SERVICE");
    assertThat(isHealthy).isFalse();
  }

  @Test
  void eachIndicatorIsANewInstancePerRequest() {
    registerUserIndicator("custom", "Custom", UpIndicator.class);
    defaultSensor(null, "custom");
    int before = UpIndicator.INSTANCES.get();

    boosterHealth();
    boosterHealth();

    assertThat(UpIndicator.INSTANCES.get() - before).isEqualTo(2);
  }

  @Test
  void anIndicatorThatThrowsFailsTheWholeRequest() {
    registerUserIndicator("failing", "Failing", FailingIndicator.class);

    Object result = new BoosterHealthService(config).boosterHealth(null);

    assertThat(((Throwable) result).getMessage()).isEqualTo("indicator failure");
  }

  @Test
  void theDetailsFlagRemovesTheDetailsOfAnyIndicator() {
    registerUserIndicator("custom", "Custom", UpIndicator.class);
    config.userHealthIndicators.get("custom").healthIndicatorConfiguration().details = false;
    defaultSensor(null, "custom");

    assertThat(boosterHealth()).isEqualTo(Map.of("status", "UP", "name", "Custom", "id", "custom"));
  }

  @Test
  void theResponseKeepsTheTypeScriptPropertyOrder() {
    defaultSensor(null, "booster/database/events");
    when(sensor.databaseEventsHealthDetails(config)).thenReturn(Map.of("count", 1));

    assertThat(BoosterJson.toJson(boosterHealthBody()))
        .isEqualTo("[{\"status\":\"UP\",\"details\":{\"count\":1},\"name\":\"Booster Database Events\","
            + "\"id\":\"booster/database/events\"}]");
  }

  @Test
  void azureLikeArrayDetailsArePassedThrough() {
    defaultSensor(null, "booster/database/readmodels");
    when(sensor.databaseReadModelsHealthDetails(config)).thenReturn(List.of(Map.of("url", "u", "count", 3)));

    assertThat(boosterHealth().get("details")).isEqualTo(List.of(Map.of("url", "u", "count", 3)));
  }
}
