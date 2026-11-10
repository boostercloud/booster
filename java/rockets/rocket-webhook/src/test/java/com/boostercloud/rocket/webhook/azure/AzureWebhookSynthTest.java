package com.boostercloud.rocket.webhook.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.common.PromisesError;
import com.boostercloud.framework.common.RocketLoader;
import com.boostercloud.framework.provider.azure.infrastructure.ApplicationBuild;
import com.boostercloud.framework.provider.azure.infrastructure.ApplicationBuilder;
import com.boostercloud.framework.provider.azure.infrastructure.InfrastructureContext;
import com.boostercloud.framework.provider.azure.infrastructure.helper.DeploySources;
import com.boostercloud.framework.provider.azure.infrastructure.helper.Params;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.InfrastructureRocket;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketUtils;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketZipResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.rocket.webhook.azure.synth.Synth;
import com.boostercloud.rocket.webhook.azure.synth.TerraformFunctionApp;
import com.boostercloud.rocket.webhook.core.BoosterWebhook;
import com.boostercloud.rocket.webhook.types.AllowedHttpMethod;
import com.boostercloud.rocket.webhook.types.WebhookParamsEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Azure infrastructure rocket inside a real synthesis ({@code ApplicationBuilder.buildApplication()} of the Azure
 * infrastructure; nothing is deployed and no Azure API is called): golden-file contract tests of the
 * {@code cdk.tf.json} (the webhook function app and the application gateway overrides) and of the rocket package (one
 * {@code function.json} per webhook). The rule names come from deterministic UUIDs here; the published rocket uses
 * random ones, as TypeScript does. Regenerate the golden files with {@code -Dbooster.golden.update=true} and review the
 * diff.
 */
class AzureWebhookSynthTest {

  private static final Path GOLDEN = Path.of("src/test/resources/golden");

  public static final class TestHandler {}

  public static final class FacebookHandler {}

  private static final List<WebhookParamsEvent> PARAMS = List.of(
      WebhookParamsEvent.builder().route("test").handlerClass(TestHandler.class)
          .allowedMethods(AllowedHttpMethod.POST).build(),
      WebhookParamsEvent.builder().route("clients/other").handlerClass(FacebookHandler.class)
          .allowedMethods(AllowedHttpMethod.GET, AllowedHttpMethod.POST).build());

  @TempDir
  Path root;

  @BeforeEach
  void setUp() {
    EnvironmentVariables.clearOverrides();
    Map<String, String> settings = new LinkedHashMap<>();
    settings.put("REGION", "East US");
    settings.put("BOOSTER_ENV", "production");
    settings.put("BOOSTER_APP_NAME", "");
    settings.put("AZURE_MAX_CONTAINER_THROUGHPUT", "1000");
    settings.put("AZURE_MAX_DATABASE_THROUGHPUT", "1000");
    settings.put("BOOSTER_AZURE_SERVICE_PLAN_BASIC", "false");
    settings.put("BOOSTER_USE_WAF", "false");
    settings.put("BOOSTER_AZURE_JAVA_VERSION", "21");
    settings.put("BOOSTER_APP_CLASS", "");
    settings.forEach(EnvironmentVariables::set);
  }

  @AfterEach
  void tearDown() {
    EnvironmentVariables.clearOverrides();
  }

  /** UUIDs whose first characters differ: the rule names keep only {@code ru}/{@code rw} plus 7 of them here. */
  private static final List<String> UUIDS = List.of("f47ac10b-58cc-4372-a567-0e02b2c3d479",
      "9b2d5f3e-1c4a-4e8b-b6d2-7a1c3e5f9b0d", "3e7c1a9f-2b4d-4f6a-8c0e-5d9b7f1a3c2e");

  private static Supplier<String> deterministicUuids() {
    AtomicInteger next = new AtomicInteger();
    return () -> UUIDS.get(next.getAndIncrement() % UUIDS.size());
  }

  private BoosterConfig config(List<WebhookParamsEvent> params) {
    BoosterConfig config = new BoosterConfig("production");
    config.appName = "test";
    config.logger = Logger.NOOP;
    config.enableSubscriptions = false;
    config.env.put("CUSTOM_SETTING", "custom value");
    config.env.put("BOOSTER_ROCKET_FUNCTION_ID", "overridden by the rocket");
    config.rockets = List.of(new BoosterWebhook(config, params).rocketForAzure());
    return config;
  }

  private ApplicationBuild build(BoosterConfig config, List<InfrastructureRocket> rockets) {
    InfrastructureContext context = InfrastructureContext.builder()
        .projectRoot(root)
        .applicationClass("com.example.webhooks.Main")
        .deploySources(sources(root))
        .httpTransport(request -> {
          throw new IllegalStateException("The synthesis must not call Azure: " + request.uri());
        })
        .configuration(Params.Configuration.of("app-id", "app-secret", "tenant-id", "subscription-id", "East US"))
        .build();
    return new ApplicationBuilder(config, rockets, context).buildApplication();
  }

  private ApplicationBuild buildDeterministic(BoosterConfig config) {
    @SuppressWarnings("unchecked")
    List<WebhookParamsEvent> params = (List<WebhookParamsEvent>) config.rockets.getFirst().parameters();
    return build(config, List.of(new AzureWebhook(params, new Synth(deterministicUuids()))));
  }

  private static DeploySources sources(Path root) {
    try {
      Path classes = root.resolve("target/classes");
      Files.createDirectories(classes.resolve("com/example/webhooks"));
      Files.write(classes.resolve("com/example/webhooks/Main.class"), new byte[] {(byte) 0xCA, (byte) 0xFE, 1});
      Path lib = root.resolve("target/lib");
      Files.createDirectories(lib);
      Files.write(lib.resolve("booster-rocket-webhook-4.1.0.jar"), new byte[] {'P', 'K', 1});
      return new DeploySources(root, List.of(classes, lib.resolve("booster-rocket-webhook-4.1.0.jar")), List.of());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void assertGolden(String goldenName, String actual) throws IOException {
    Path golden = GOLDEN.resolve(goldenName);
    if (Boolean.getBoolean("booster.golden.update")) {
      Files.createDirectories(GOLDEN);
      Files.writeString(golden, actual, StandardCharsets.UTF_8);
    }
    assertThat(actual).isEqualTo(Files.readString(golden, StandardCharsets.UTF_8));
  }

  private static String stackJson(ApplicationBuild build, Path root) throws IOException {
    return Files.readString(build.stackDirectory().resolve("cdk.tf.json"), StandardCharsets.UTF_8)
        .replace(root.toAbsolutePath().normalize().toString(), "<PROJECT_ROOT>");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> resource(Map<String, Object> stack, String type, String id) {
    Map<String, Object> resources = (Map<String, Object>) stack.get("resource");
    return (Map<String, Object>) ((Map<String, Object>) resources.get(type)).get(id);
  }

  @Test
  void synthesizes_the_function_app_and_the_gateway_rules_of_every_webhook() throws IOException {
    ApplicationBuild build = buildDeterministic(config(PARAMS));

    String json = stackJson(build, root);
    assertGolden("webhook.cdk.tf.json", json);

    Map<String, Object> stack = BoosterJson.parseObject(json);
    Map<String, Object> functionApp = resource(stack, "azurerm_windows_function_app", "estproductionwebhookfunc");
    assertThat(functionApp).containsEntry("name", "testproductionrgwebhookfunc")
        .containsEntry("functions_extension_version", "~4");
    @SuppressWarnings("unchecked")
    Map<String, Object> appSettings = (Map<String, Object>) functionApp.get("app_settings");
    assertThat(appSettings).containsOnlyKeys("WEBSITE_RUN_FROM_PACKAGE", "WEBSITE_CONTENTSHARE", "CUSTOM_SETTING",
        "BOOSTER_ROCKET_FUNCTION_ID", "BOOSTER_ENV", "COSMOSDB_CONNECTION_STRING",
        "WEBSITE_CONTENTAZUREFILECONNECTIONSTRING", "BOOSTER_APP_CLASS");
    // toTerraformName("testproduction", "webhookfunc") drops the first character to fit in 24, as in TypeScript
    assertThat(appSettings).containsEntry("WEBSITE_RUN_FROM_PACKAGE", "1")
        .containsEntry("WEBSITE_CONTENTSHARE", "estproductionwebhookfunc")
        .containsEntry("CUSTOM_SETTING", "custom value")
        .containsEntry("BOOSTER_ROCKET_FUNCTION_ID", "rocket-webhook")
        .containsEntry("BOOSTER_ENV", "production")
        .containsEntry("BOOSTER_APP_CLASS", "com.example.webhooks.Main");

    Map<String, Object> gateway = resource(stack, "azurerm_application_gateway", "ag");
    assertThat(gateway.get("backend_address_pool")).isEqualTo(List.of(Map.of("name",
        "rocketWebHookBackendAddressPool", "fqdns",
        List.of("${azurerm_windows_function_app.estproductionwebhookfunc.name}.azurewebsites.net"))));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> pathRules = (List<Map<String, Object>>) ((Map<String, Object>) ((List<?>) gateway
        .get("url_path_map")).getFirst()).get("path_rule");
    assertThat(pathRules).extracting(rule -> rule.get("paths"))
        .containsExactly(List.of("/test", "/test/*"), List.of("/clients/other", "/clients/other/*"));
    assertThat(pathRules).allSatisfy(rule -> assertThat(rule)
        .containsEntry("backend_address_pool_name", "rocketWebHookBackendAddressPool")
        .containsEntry("backend_http_settings_name", "mainBackendHttpSettings")
        .containsEntry("rewrite_rule_set_name", "mainRewriteRuleSet"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rewrites = (List<Map<String, Object>>) ((Map<String, Object>) ((List<?>) gateway
        .get("rewrite_rule_set")).getFirst()).get("rewrite_rule");
    assertThat(rewrites).extracting(rule -> rule.get("rule_sequence")).containsExactly(1, 2);
    assertThat(rewrites).extracting(rule -> (Object) ((Map<?, ?>) rule.get("url")).get("path"))
        .containsExactly("/api/test", "/api/clients/other");
    assertThat(json).contains("\\\"ROCKET_FUNCTION_APP_NAMES\\\" = \\\"testproductionrgwebhookfunc\\\"")
        .contains("\\\"ROCKET_PACKAGE_MAPPING\\\" = \\\"\\\"");
  }

  @Test
  void the_rule_names_come_from_the_app_prefix_and_23_characters_of_a_uuid() throws IOException {
    ApplicationBuild build = buildDeterministic(config(PARAMS));
    String json = stackJson(build, root);

    // toTerraformName("testproduction", "ru" + "f47ac10b-58cc-4372-a567") = the first 23 characters, dashes removed
    assertThat(json).contains("\"name\": \"testproductionruf47ac10\"", "\"name\": \"testproductionrwf47ac10\"",
        "\"name\": \"testproductionru9b2d5f3\"", "\"name\": \"testproductionrw9b2d5f3\"");
  }

  @Test
  void packages_one_http_function_per_webhook_bound_to_the_rocket_http_entry_point() throws IOException {
    ApplicationBuild build = buildDeterministic(config(PARAMS));

    assertThat(build.rocketsZipResources()).singleElement().satisfies(resource -> {
      assertThat(resource.functionAppName()).isEqualTo("testproductionrgwebhookfunc");
      assertThat(resource.zip().fileName()).isEqualTo("rocket_testproductionrgwebhookfunc.zip");
    });
    RocketZipResource resource = build.rocketsZipResources().getFirst();
    Map<String, String> entries = new TreeMap<>();
    try (ZipFile zip = new ZipFile(resource.zip().path().toFile())) {
      for (ZipEntry entry : zip.stream().toList()) {
        entries.put(entry.getName(), entry.getName().endsWith(".json")
            ? new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8) : "");
      }
    }
    StringBuilder listing = new StringBuilder();
    entries.forEach((name, content) -> listing.append("=== ").append(name).append('\n').append(content)
        .append(content.isEmpty() ? "" : "\n"));
    assertGolden("webhook-rocket-package.txt", listing.toString());
    assertThat(entries).containsKeys("test/function.json", "clients_other/function.json", "host.json", "test.jar");
  }

  @Test
  void the_published_rocket_uses_random_rule_names_on_every_synthesis() throws IOException {
    BoosterConfig config = config(PARAMS.subList(0, 1));
    InfrastructureRocket rocket = RocketLoader.loadRocket(config.rockets.getFirst(), InfrastructureRocket.class);

    String first = stackJson(build(config, List.of(rocket)), root);
    String second = stackJson(build(config, List.of(rocket)), root);

    assertThat(first).isNotEqualTo(second);
    assertThat(first).containsPattern("\"name\": \"testproductionru[0-9a-f]{7}\"");
  }

  @Test
  void the_rocket_is_loaded_from_the_descriptor_package_and_defines_the_v4_hooks() {
    BoosterConfig config = config(PARAMS);
    InfrastructureRocket rocket = RocketLoader.loadRocket(config.rockets.getFirst(), InfrastructureRocket.class);

    assertThat(rocket).isInstanceOf(AzureWebhook.class);
    assertThat(((AzureWebhook) rocket).params()).isEqualTo(PARAMS);
    assertThat(rocket.defines(InfrastructureRocket.Hook.MOUNT_FUNCTIONS_V4)).isTrue();
    assertThat(rocket.defines(InfrastructureRocket.Hook.GET_FUNCTION_APP_NAME)).isTrue();
    assertThat(rocket.defines(InfrastructureRocket.Hook.MOUNT_CODE)).isFalse();
    assertThat(rocket.defines(InfrastructureRocket.Hook.MOUNT_FUNCTIONS)).isFalse();
    assertThatThrownBy(() -> new Rocket().build("not a list")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void a_webhook_without_route_fails_the_synthesis() {
    BoosterConfig config = config(List.of(WebhookParamsEvent.builder().handlerClass(TestHandler.class).build()));

    assertThatThrownBy(() -> buildDeterministic(config)).isInstanceOf(PromisesError.class)
        .hasMessageContaining("Undefined route parameter [object Object]");
  }

  @Test
  void the_function_app_needs_the_database_the_storage_account_and_the_service_plan_in_that_order() {
    ApplicationSynthStack stack = buildDeterministic(config(PARAMS)).azureStack().applicationStack;
    RocketUtils utils = RocketUtils.buildRocketUtils();
    BoosterConfig config = new BoosterConfig("production");

    stack.cosmosdbDatabase = null;
    stack.storageAccount = null;
    stack.applicationServicePlan = null;
    assertThatThrownBy(() -> TerraformFunctionApp.build(stack, config, utils)).hasMessage("Cosmosdb database not found");
    stack.cosmosdbDatabase = stack.resourceGroup;
    assertThatThrownBy(() -> TerraformFunctionApp.build(stack, config, utils)).hasMessage("Storage account not found");
    stack.storageAccount = stack.resourceGroup;
    assertThatThrownBy(() -> TerraformFunctionApp.build(stack, config, utils))
        .isInstanceOf(IllegalStateException.class).hasMessage("Application service plan not found");
  }

  @Test
  void without_application_gateway_only_the_function_app_is_added_and_the_stack_is_returned() {
    ApplicationSynthStack stack = buildDeterministic(config(PARAMS)).azureStack().applicationStack;
    stack.appGateway = null;
    int before = stack.terraformStack.elements().size();
    stack.rocketStack = null;

    ApplicationSynthStack result = new Synth(deterministicUuids()).mountStack(
        List.of(WebhookParamsEvent.of("other", TestHandler.class)), new BoosterConfig("staging"), stack,
        (name, suffix) -> name + "-" + suffix);

    assertThat(result).isSameAs(stack);
    assertThat(stack.terraformStack.elements()).hasSize(before + 1);
    assertThat(stack.rocketStack).isNull();
  }
}
