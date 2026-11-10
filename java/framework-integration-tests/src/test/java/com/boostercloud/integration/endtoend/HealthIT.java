package com.boostercloud.integration.endtoend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.boostercloud.framework.common.HttpService;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import com.boostercloud.integration.helper.AppHelper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-unaware/end-to-end/health.integration.ts}: the health endpoint returns a JSON
 * array of indicator trees. The built-in {@code booster} indicators (all enabled by the application's configuration)
 * are joined by the application's sensors: {@code myApplication} (UP) with its child {@code myApplication/child}
 * (OUT_OF_SERVICE), and two UNKNOWN children added to {@code booster/database}. TypeScript expects 503 for a
 * response whose tree contains an indicator that is not UP and 200 for the leaves that are UP.
 *
 * <p>The 503 holds where the provider honours the health result's {@code status-code} (Azure). The local provider,
 * TypeScript and Java alike, answers 200 to every successful health request, so the two 503 tests are skipped on
 * {@code LOCAL} and two additional tests pin the local status with the same body assertions.
 *
 * <p>The request is {@code request(path, 'GET', '', { acceptedStatusCodes: [503] })} of
 * {@code framework-common-helpers}, i.e. {@link HttpService#request}. TypeScript skips the suite on AWS; AWS is not
 * ported, so the gate only documents it.
 */
@EndToEnd
@DisplayName("Health end-to-end tests")
@SuppressWarnings("unchecked") // the responses are raw JSON trees (maps keyed by strings)
class HealthIT {

  private static final String AWS = "AWS";

  private static final String UNHEALTHY_ANSWERS_200_ON_LOCAL = "the local provider answers 200 to every successful "
      + "health request, even when an indicator is not UP (TypeScript's local HealthController ignores the "
      + "'status-code' header of healthRequestResult; framework-provider-local-infrastructure PARITY Q13): the tests "
      + "with status 200 below pin it";

  private final ApplicationUnderTest applicationUnderTest;
  private String url = "";

  HealthIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    if (AWS.equals(AppHelper.testedProvider())) {
      System.out.println("****************** Warning **********************");
      System.out.println("AWS provider does not support sensor health so these tests are skipped for AWS");
      System.out.println("*************************************************");
    }
    assumeFalse(AWS.equals(AppHelper.testedProvider()),
        "AWS provider does not support sensor health so these tests are skipped for AWS");

    url = applicationUnderTest.http().getHealthUrl();
  }

  @Test
  @Order(1)
  @DisplayName("root health returns all indicators")
  void rootHealthReturnsAllIndicators() {
    assumeFalse(AppHelper.LOCAL.equals(AppHelper.testedProvider()), UNHEALTHY_ANSWERS_200_ON_LOCAL);

    expectRootHealth(getHealth(url, null, 503));
  }

  @Test
  @Order(2)
  @DisplayName("function health returns the indicator")
  void functionHealthReturnsTheIndicator() {
    Map<String, Object> boosterFunction = (Map<String, Object>) getHealth(url, "function", 200).get(0);
    expectBoosterFunction(boosterFunction);
  }

  @Test
  @Order(3)
  @DisplayName("database health returns the indicator")
  void databaseHealthReturnsTheIndicator() {
    assumeFalse(AppHelper.LOCAL.equals(AppHelper.testedProvider()), UNHEALTHY_ANSWERS_200_ON_LOCAL);

    expectDatabaseHealth((Map<String, Object>) getHealth(url, "database", 503).get(0));
  }

  @Test
  @Order(4)
  @DisplayName("events database health returns the indicator")
  void eventsDatabaseHealthReturnsTheIndicator() {
    Map<String, Object> databaseEvents = (Map<String, Object>) getHealth(url, "database/events", 200).get(0);
    expectDatabaseEvents(databaseEvents);
  }

  @Test
  @Order(5)
  @DisplayName("readmodels database health returns the indicator")
  void readmodelsDatabaseHealthReturnsTheIndicator() {
    Map<String, Object> databaseReadModels = (Map<String, Object>) getHealth(url, "database/readmodels", 200).get(0);
    expectDatabaseReadModels(databaseReadModels);
  }

  @Test
  @Order(6)
  @DisplayName("root health returns all indicators with status 200 on the local provider")
  void rootHealthReturnsAllIndicatorsWithStatus200OnTheLocalProvider() {
    assumeTrue(AppHelper.LOCAL.equals(AppHelper.testedProvider()), "local provider behaviour");

    expectRootHealth(getHealth(url, null, 200));
  }

  @Test
  @Order(7)
  @DisplayName("database health returns the indicator with status 200 on the local provider")
  void databaseHealthReturnsTheIndicatorWithStatus200OnTheLocalProvider() {
    assumeTrue(AppHelper.LOCAL.equals(AppHelper.testedProvider()), "local provider behaviour");

    expectDatabaseHealth((Map<String, Object>) getHealth(url, "database", 200).get(0));
  }

  private static void expectRootHealth(List<?> jsonResult) {
    Map<String, Object> boosterResult = find(jsonResult, "booster");
    expectBooster(boosterResult);
    Map<String, Object> boosterFunction = find(components(boosterResult), "booster/function");
    expectBoosterFunction(boosterFunction);
    expectDatabaseHealth(find(components(boosterResult), "booster/database"));

    Map<String, Object> appResult = find(jsonResult, "myApplication");
    expectApplication(appResult);
    Map<String, Object> appChildResult = find(components(appResult), "myApplication/child");
    expectApplicationChild(appChildResult);
  }

  /** {@code expectBoosterDatabase} and its four components, as both TypeScript tests check them. */
  private static void expectDatabaseHealth(Map<String, Object> boosterDatabase) {
    expectBoosterDatabase(boosterDatabase);
    Map<String, Object> databaseEvents = find(components(boosterDatabase), "booster/database/events");
    expectDatabaseEvents(databaseEvents);
    Map<String, Object> databaseReadModels = find(components(boosterDatabase), "booster/database/readmodels");
    expectDatabaseReadModels(databaseReadModels);
    Map<String, Object> myApplicationDatabase =
        find(components(boosterDatabase), "booster/database/myApplication");
    expectApplicationAddDatabase(myApplicationDatabase);
    Map<String, Object> myApplication2Database =
        find(components(boosterDatabase), "booster/database/myApplication2");
    expectApplication2AddDatabase(myApplication2Database);
  }

  private static Map<String, Object> find(List<?> elements, String id) {
    return elements.stream()
        .map(element -> (Map<String, Object>) element)
        .filter(element -> id.equals(element.get("id")))
        .findFirst()
        .orElse(null);
  }

  private static List<?> components(Map<String, Object> indicator) {
    return (List<?>) indicator.get("components");
  }

  private static void expectBooster(Map<String, Object> boosterResult) {
    assertThat(boosterResult.get("id")).isEqualTo("booster");
    assertThat(boosterResult.get("status")).isEqualTo("UP");
    assertThat(boosterResult.get("name")).isEqualTo("Booster");
    assertThat((String) ((Map<String, Object>) boosterResult.get("details")).get("boosterVersion")).isNotEmpty();
    assertThat(components(boosterResult)).hasSize(2);
  }

  private static void expectBoosterFunction(Map<String, Object> boosterFunction) {
    assertThat(boosterFunction.get("id")).isEqualTo("booster/function");
    assertThat(boosterFunction.get("status")).isEqualTo("UP");
    assertThat(boosterFunction.get("name")).isEqualTo("Booster Function");
    Map<String, Object> details = (Map<String, Object>) boosterFunction.get("details");
    List<?> cpus = (List<?>) details.get("cpus");
    assertThat(cpus).isNotEmpty();
    assertThat((List<?>) ((Map<String, Object>) cpus.get(0)).get("timesPercentages")).isNotEmpty();
    Map<String, Object> memory = (Map<String, Object>) details.get("memory");
    assertThat(((Number) memory.get("totalBytes")).doubleValue()).isGreaterThan(0);
    assertThat(((Number) memory.get("freeBytes")).doubleValue()).isGreaterThan(0);
    assertThat(((String) details.get("graphQL_url")).endsWith("/graphql")).isTrue();
    assertThat(boosterFunction).doesNotContainKey("components");
  }

  private static void expectBoosterDatabase(Map<String, Object> boosterDatabase) {
    assertThat(boosterDatabase.get("id")).isEqualTo("booster/database");
    assertThat(boosterDatabase.get("status")).isEqualTo("UP");
    assertThat(boosterDatabase.get("name")).isEqualTo("Booster Database");
    assertThat(boosterDatabase).containsKey("details");
    assertThat(components(boosterDatabase)).hasSize(4);
  }

  private static void expectDatabaseEvents(Map<String, Object> databaseEvent) {
    assertThat(databaseEvent.get("id")).isEqualTo("booster/database/events");
    assertThat(databaseEvent.get("status")).isEqualTo("UP");
    assertThat(databaseEvent.get("name")).isEqualTo("Booster Database Events");
    assertThat(databaseEvent).containsKey("details");
    assertThat(databaseEvent).doesNotContainKey("components");
  }

  private static void expectDatabaseReadModels(Map<String, Object> databaseReadModels) {
    assertThat(databaseReadModels.get("id")).isEqualTo("booster/database/readmodels");
    assertThat(databaseReadModels.get("status")).isEqualTo("UP");
    assertThat(databaseReadModels.get("name")).isEqualTo("Booster Database ReadModels");
    assertThat(databaseReadModels).containsKey("details");
    assertThat(databaseReadModels).doesNotContainKey("components");
  }

  private static void expectApplicationAddDatabase(Map<String, Object> applicationDatabase) {
    assertThat(applicationDatabase.get("id")).isEqualTo("booster/database/myApplication");
    assertThat(applicationDatabase.get("status")).isEqualTo("UNKNOWN");
    assertThat(applicationDatabase.get("name"))
        .isEqualTo("Indicator added to the Booster Database indicator through My Application");
    assertThat(applicationDatabase).doesNotContainKey("details");
    assertThat(applicationDatabase).doesNotContainKey("components");
  }

  private static void expectApplication2AddDatabase(Map<String, Object> databaseApplication2) {
    assertThat(databaseApplication2.get("id")).isEqualTo("booster/database/myApplication2");
    assertThat(databaseApplication2.get("status")).isEqualTo("UNKNOWN");
    assertThat(databaseApplication2.get("name"))
        .isEqualTo("A second indicator added to the Booster Database indicator through My Application");
    assertThat(databaseApplication2).doesNotContainKey("details");
    assertThat(databaseApplication2).doesNotContainKey("components");
  }

  private static void expectApplication(Map<String, Object> boosterResult) {
    assertThat(boosterResult.get("id")).isEqualTo("myApplication");
    assertThat(boosterResult.get("status")).isEqualTo("UP");
    assertThat(boosterResult.get("name")).isEqualTo("my-application");
    assertThat(boosterResult).doesNotContainKey("details");
    assertThat(components(boosterResult)).hasSize(1);
  }

  private static void expectApplicationChild(Map<String, Object> boosterResult) {
    assertThat(boosterResult.get("id")).isEqualTo("myApplication/child");
    assertThat(boosterResult.get("status")).isEqualTo("OUT_OF_SERVICE");
    assertThat(boosterResult.get("name")).isEqualTo("My Application child");
    assertThat(boosterResult).doesNotContainKey("details");
    assertThat(boosterResult).doesNotContainKey("components");
  }

  private static List<?> getHealth(String url, String componentUrl, int expectedHttpStatus) {
    String path = componentUrl != null ? url + "booster/" + componentUrl : url;
    System.out.println(path);
    // Accept 503 as a valid response code for health checks
    HttpService.PostResult result =
        HttpService.request(path, HttpService.Method.GET, "", HttpService.PostConfiguration.accepting(503));
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(expectedHttpStatus);
    return (List<?>) BoosterJson.parse(result.body());
  }
}
