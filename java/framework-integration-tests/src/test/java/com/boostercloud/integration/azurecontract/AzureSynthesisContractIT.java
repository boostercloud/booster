package com.boostercloud.integration.azurecontract;

import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.APPLICATION_CLASS;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.APP_NAME;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.APP_PREFIX;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.DATABASE;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.EVENTS_STORE;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.FUNCTION_APP;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.RESOURCE_GROUP;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.STACK;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.STREAM_FUNCTION_APP;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.STREAM_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.integration.harness.E2eSettings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * The Azure contract of the application, part 1: <b>synthesis</b>. The application is launched as the {@code boost}
 * CLI launches it ({@code java -cp target/classes:target/lib/* com.boostercloud.integration.Main synth} with
 * {@code BOOSTER_ENV=azure}) in a sandbox laid out like a CLI-built project, and the Terraform configuration, the
 * {@code function.json} of every function, the {@code host.json} files and the package layouts are compared with what
 * the TypeScript synthesis produces for the TypeScript app (spec 12; the deploy suites of spec 17 §3.3 synthesize the
 * same thing before deploying it). The expectations are written from the TypeScript sources
 * ({@link TypeScriptAzureApp}), with the documented Java differences: {@code function.json} definitions for the Java
 * worker instead of a generated {@code functions.js}, the Java stack and the {@code BOOSTER_APP_CLASS} app setting.
 * Nothing is deployed and no Azure credential is needed.
 */
@DisplayName("Azure synthesis contract")
class AzureSynthesisContractIT {

  /** The deploying machine of the TypeScript {@code integration/azure} scripts, pinned. */
  static final Map<String, String> SYNTH_ENVIRONMENT = Map.of(
      "BOOSTER_ENV", "azure",
      "BOOSTER_APP_SUFFIX", TypeScriptAzureApp.APP_SUFFIX,
      "REGION", "East US",
      "AZURE_SUBSCRIPTION_ID", "00000000-0000-0000-0000-000000000000");

  /** Variables of the machine running the tests that would change the synthesis (their defaults are expected). */
  static final List<String> UNSET_VARIABLES = List.of("AZURE_MAX_CONTAINER_THROUGHPUT", "AZURE_MAX_DATABASE_THROUGHPUT",
      "BOOSTER_AZURE_SERVICE_PLAN_BASIC", "BOOSTER_USE_WAF", "BOOSTER_AZURE_JAVA_VERSION", "BOOSTER_TERRAFORM_BINARY",
      "BOOSTER_APP_NAME", "BOOSTER_APP_CLASS", "BOOSTER_CLI_HOOK");

  /** {@code appendDefaultHostConfig}: {@code JSON.stringify(hostConfig, null, 2)}. */
  static final String DEFAULT_HOST_JSON = """
      {
        "version": "2.0",
        "extensionBundle": {
          "id": "Microsoft.Azure.Functions.ExtensionBundle",
          "version": "[4.*, 5.0.0)"
        }
      }""";

  private static final String ENTRY_POINT_CLASS =
      "com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions";

  private CliBuiltProject project;
  private CliBuiltProject.Launch synth;
  private Map<String, Object> terraform;

  @BeforeAll
  void synthesize() {
    project = CliBuiltProject.create("azure-synth", TypeScriptAzureApp.ASSETS);
    synth = project.run("synth", SYNTH_ENVIRONMENT, UNSET_VARIABLES, Duration.ofMinutes(3));
    assertThat(synth.exitCode()).as("exit code of synth, output in %s:%n%s", synth.logFile(), synth.output()).isZero();
    Path stack = project.directory().resolve("cdktf.out").resolve("stacks").resolve(STACK).resolve("cdk.tf.json");
    terraform = Files.isRegularFile(stack) ? BoosterJson.parseObject(read(stack)) : Map.of();
  }

  @AfterAll
  void removeProject() {
    if (project != null) {
      project.close();
    }
  }

  @Nested
  @Order(1)
  @DisplayName("the synth command")
  class TheSynthCommand {

    @Test
    @Order(1)
    @DisplayName("exits successfully and writes the Terraform configuration and the three packages")
    void writesTheFiles() {
      assertThat(synth.exitCode()).as("exit code of synth, output in %s:%n%s", synth.logFile(), synth.output())
          .isZero();
      Path root = project.directory();
      assertThat(root.resolve("cdktf.out/manifest.json")).isRegularFile();
      assertThat(root.resolve("cdktf.out/stacks/" + STACK + "/cdk.tf.json")).isRegularFile();
      assertThat(root.resolve("functionApp.zip")).isRegularFile();
      assertThat(root.resolve("consumerFunctionApp.zip")).isRegularFile();
      assertThat(root.resolve("baseWebPubSubBinding.zip")).isRegularFile();
      // TypeScript also writes cdktf.json for cdktn-cli, which the Java deploy does not use (README, differences);
      // the app has no rockets, so no rocket_*.zip
      assertThat(root.resolve("cdktf.json")).doesNotExist();
      try (Stream<Path> files = Files.list(root)) {
        assertThat(files.map(path -> path.getFileName().toString()))
            .noneMatch(name -> name.startsWith("rocket_"));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Test
    @Order(2)
    @DisplayName("logs the steps of ApplicationBuilder in the TypeScript order")
    void logsTheSteps() {
      assertThat(synth.output()).containsSubsequence(
          "ApplicationBuilder#synthApplication: [\"Synth...\"]",
          "RocketBuilder#synthRocket: [\"Synth rockets...\"]",
          "function-zip#createZipV4: [\"Creating Azure Functions v4 ZIP package\"]",
          "FunctionsCodeGenerator#generateFunctionsCode: [\"Generating Azure Functions v4 registration code\"]",
          "function-zip#createConsumerZipV4: [\"Creating Azure Consumer Functions v4 ZIP package\"]");
    }

    @Test
    @Order(3)
    @DisplayName("writes the cdktn manifest of the single stack")
    void writesTheManifest() {
      Map<String, Object> manifest = BoosterJson.parseObject(read(project.directory().resolve("cdktf.out/manifest.json")));
      assertThat(manifest).containsKey("version");
      assertThat(manifest.get("stacks")).isEqualTo(Map.of(STACK, Map.of(
          "annotations", List.of(),
          "constructPath", STACK,
          "dependencies", List.of(),
          "name", STACK,
          "synthesizedStackPath", "stacks/" + STACK + "/cdk.tf.json",
          "workingDirectory", "stacks/" + STACK)));
    }
  }

  @Nested
  @Order(2)
  @DisplayName("the Terraform configuration")
  class TheTerraformConfiguration {

    @Test
    @DisplayName("declares exactly the resources the TypeScript stack declares for an app with subscriptions and event streaming")
    void declaresTheResources() {
      Map<String, List<String>> expected = new LinkedHashMap<>();
      expected.put("azurerm_application_gateway", List.of("ag"));
      expected.put("azurerm_cosmosdb_account", List.of(APP_PREFIX + "dba"));
      expected.put("azurerm_cosmosdb_sql_container",
          TypeScriptAzureApp.containers().stream().map(TypeScriptAzureApp.ContainerSpec::terraformId).toList());
      expected.put("azurerm_cosmosdb_sql_database", List.of(APP_PREFIX + "dbd"));
      expected.put("azurerm_eventhub", List.of(APP_PREFIX + "eh"));
      expected.put("azurerm_eventhub_namespace", List.of(APP_PREFIX + "ehn"));
      expected.put("azurerm_network_security_group", List.of(APP_PREFIX + "sg"));
      expected.put("azurerm_public_ip", List.of("pip"));
      expected.put("azurerm_resource_group", List.of(RESOURCE_GROUP));
      expected.put("azurerm_service_plan", List.of(APP_PREFIX + "psp", APP_PREFIX + "psc"));
      expected.put("azurerm_storage_account", List.of(APP_PREFIX + "sp", APP_PREFIX + "sc"));
      expected.put("azurerm_subnet", List.of(APP_PREFIX + "vsn"));
      expected.put("azurerm_subnet_network_security_group_association", List.of(APP_PREFIX + "snsg"));
      expected.put("azurerm_virtual_network", List.of(APP_PREFIX + "vn"));
      expected.put("azurerm_web_pubsub", List.of(APP_PREFIX + "wps"));
      expected.put("azurerm_web_pubsub_hub", List.of(APP_PREFIX + "wpsh"));
      expected.put("azurerm_windows_function_app", List.of(APP_PREFIX + "func", APP_PREFIX + "fhub"));
      Map<String, Object> resources = map(terraform.get("resource"));
      assertThat(resources.keySet()).containsExactlyInAnyOrderElementsOf(expected.keySet());
      expected.forEach((type, ids) ->
          assertThat(map(resources.get(type)).keySet()).as(type).containsExactlyInAnyOrderElementsOf(ids));
      assertThat(map(terraform.get("data")).keySet()).containsExactly("azurerm_public_ip");
      assertThat(map(map(terraform.get("data")).get("azurerm_public_ip")).keySet())
          .containsExactly("torecontractazuredatapip");
    }

    @Test
    @DisplayName("configures the azurerm provider with the subscription and a local backend in the project")
    void configuresProviderAndBackend() {
      assertThat(terraform.get("provider")).isEqualTo(Map.of("azurerm", List.of(Map.of(
          "features", List.of(Map.of()),
          "resource_provider_registrations", "legacy",
          "subscription_id", "00000000-0000-0000-0000-000000000000"))));
      assertThat(terraform.get("terraform")).isEqualTo(Map.of(
          "backend", Map.of("local", Map.of("path",
              project.directory().resolve("terraform." + STACK + ".tfstate").toString())),
          "required_providers", Map.of("azurerm", Map.of("source", "azurerm", "version", "~> 5.0"))));
    }

    @Test
    @DisplayName("creates the resource group, the Cosmos DB account and the application database")
    void createsTheDatabase() {
      assertThat(resource("azurerm_resource_group", RESOURCE_GROUP)).isEqualTo(Map.of(
          "location", "East US", "name", RESOURCE_GROUP, "provider", "azurerm"));
      Map<String, Object> account = resource("azurerm_cosmosdb_account", APP_PREFIX + "dba");
      assertThat(account).containsEntry("name", RESOURCE_GROUP + "cdba").containsEntry("kind", "GlobalDocumentDB")
          .containsEntry("offer_type", "Standard").containsEntry("automatic_failover_enabled", true)
          .containsEntry("consistency_policy", Map.of("consistency_level", "Session"))
          .containsEntry("depends_on", List.of("azurerm_resource_group." + RESOURCE_GROUP));
      assertThat(resource("azurerm_cosmosdb_sql_database", APP_PREFIX + "dbd")).isEqualTo(Map.of(
          "account_name", "${azurerm_cosmosdb_account." + APP_PREFIX + "dba.name}",
          "autoscale_settings", Map.of("max_throughput", 1000),
          "name", DATABASE,
          "provider", "azurerm",
          "resource_group_name", RESOURCE_GROUP));
    }

    @Test
    @DisplayName("creates one container per store and per read model with the TypeScript partition keys, unique keys and TTLs")
    void createsTheContainers() {
      for (TypeScriptAzureApp.ContainerSpec container : TypeScriptAzureApp.containers()) {
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("account_name", "${azurerm_cosmosdb_account." + APP_PREFIX + "dba.name}");
        expected.put("autoscale_settings", Map.of("max_throughput", 1000));
        expected.put("database_name", "${azurerm_cosmosdb_sql_database." + APP_PREFIX + "dbd.name}");
        expected.put("name", container.name());
        expected.put("partition_key_paths", List.of(container.partitionKeyPath()));
        expected.put("partition_key_version", 2);
        expected.put("provider", "azurerm");
        expected.put("resource_group_name", "${azurerm_cosmosdb_account." + APP_PREFIX + "dba.resource_group_name}");
        if (container.uniqueKeyPath() != null) {
          expected.put("unique_key", List.of(Map.of("paths", List.of(container.uniqueKeyPath()))));
        }
        if (container.defaultTtl() != null) {
          expected.put("default_ttl", container.defaultTtl());
        }
        assertThat(resource("azurerm_cosmosdb_sql_container", container.terraformId())).as(container.name())
            .isEqualTo(expected);
      }
    }

    @Test
    @DisplayName("creates the event hub of the stream topic and the consumer plan with one worker per partition")
    void createsTheEventHub() {
      assertThat(resource("azurerm_eventhub_namespace", APP_PREFIX + "ehn")).isEqualTo(Map.of(
          "auto_inflate_enabled", false, "capacity", 2,
          "location", "${azurerm_resource_group." + RESOURCE_GROUP + ".location}",
          "name", RESOURCE_GROUP + "ehn", "provider", "azurerm", "resource_group_name", RESOURCE_GROUP, "sku", "Basic"));
      assertThat(resource("azurerm_eventhub", APP_PREFIX + "eh")).isEqualTo(Map.of(
          "message_retention", 1, "name", STREAM_TOPIC,
          "namespace_id", "${azurerm_eventhub_namespace." + APP_PREFIX + "ehn.id}",
          "partition_count", 3, "provider", "azurerm"));
      assertThat(resource("azurerm_service_plan", APP_PREFIX + "psc")).containsEntry("name", RESOURCE_GROUP + "psc")
          .containsEntry("os_type", "Windows").containsEntry("sku_name", "B1").containsEntry("worker_count", 3);
      assertThat(resource("azurerm_service_plan", APP_PREFIX + "psp")).containsEntry("name", RESOURCE_GROUP + "psp")
          .containsEntry("os_type", "Windows").containsEntry("sku_name", "Y1").containsEntry("worker_count", 1);
      assertThat(resource("azurerm_storage_account", APP_PREFIX + "sp")).containsEntry("name", RESOURCE_GROUP + "sp")
          .containsEntry("account_replication_type", "LRS").containsEntry("account_tier", "Standard");
      assertThat(resource("azurerm_storage_account", APP_PREFIX + "sc")).containsEntry("name", RESOURCE_GROUP + "sc");
    }

    @Test
    @DisplayName("creates the main function app with the Web PubSub bootstrap package and the merged app settings")
    void createsTheMainFunctionApp() {
      Map<String, Object> functionApp = resource("azurerm_windows_function_app", APP_PREFIX + "func");
      assertThat(functionApp).containsEntry("name", FUNCTION_APP)
          .containsEntry("resource_group_name", RESOURCE_GROUP)
          .containsEntry("service_plan_id", "${azurerm_service_plan." + APP_PREFIX + "psp.id}")
          .containsEntry("storage_account_name", "${azurerm_storage_account." + APP_PREFIX + "sp.name}")
          .containsEntry("storage_account_access_key", "${azurerm_storage_account." + APP_PREFIX + "sp.primary_access_key}")
          .containsEntry("functions_extension_version", "~4")
          .containsEntry("site_config", Map.of("application_stack", Map.of("java_version", "21")))
          .containsEntry("lifecycle", Map.of("ignore_changes", List.of("app_settings[\"WEBSITE_RUN_FROM_PACKAGE\"]")))
          .containsEntry("depends_on", List.of("azurerm_resource_group." + RESOURCE_GROUP))
          .containsEntry("zip_deploy_file", project.directory().resolve("baseWebPubSubBinding.zip").toString());
      // TerraformFunctionAppSettings.build order (config.env, here BOOSTER_APP_SUFFIX, after the first two), then the
      // rocket variables of addAppSettingsToFunctionApp, merged with Fn.merge; BOOSTER_APP_CLASS is Java only
      String rg = APP_PREFIX;
      assertThat(functionApp.get("app_settings")).isEqualTo("${merge({"
          + "\"WEBSITE_RUN_FROM_PACKAGE\" = \"1\", "
          + "\"WEBSITE_CONTENTSHARE\" = \"" + APP_PREFIX + "func\", "
          + "\"BOOSTER_APP_SUFFIX\" = \"contract\", "
          + "\"WebPubSubConnectionString\" = azurerm_web_pubsub." + rg + "wps.primary_connection_string, "
          + "\"BOOSTER_ENV\" = \"azure\", "
          + "\"BOOSTER_REST_API_URL\" = \"http://" + RESOURCE_GROUP + "apis.eastus.cloudapp.azure.com/azure\", "
          + "\"EVENTHUB_CONNECTION_STRING\" = \"${azurerm_eventhub_namespace." + rg + "ehn.default_primary_connection_string}"
          + ";EntityPath=${azurerm_eventhub." + rg + "eh.name}\", "
          + "\"EVENTHUB_NAME\" = \"" + STREAM_TOPIC + "\", "
          + "\"EVENTHUB_MAX_RETRIES\" = \"5\", "
          + "\"EVENTHUB_MODE\" = \"exponential\", "
          + "\"COSMOSDB_CONNECTION_STRING\" = \"AccountEndpoint=https://${azurerm_cosmosdb_account." + rg + "dba.name}"
          + ".documents.azure.com:443/;AccountKey=${azurerm_cosmosdb_account." + rg + "dba.primary_key};\", "
          + "\"WEBSITE_CONTENTAZUREFILECONNECTIONSTRING\" = azurerm_storage_account." + rg + "sp.primary_connection_string, "
          + "\"BOOSTER_APP_NAME\" = \"\", "
          + "\"BOOSTER_APP_CLASS\" = \"" + APPLICATION_CLASS + "\"}, "
          + "{\"ROCKET_FUNCTION_APP_NAMES\" = \"\", \"ROCKET_PACKAGE_MAPPING\" = \"\"})}");
    }

    @Test
    @DisplayName("creates the event stream consumer function app with its own settings and no Web PubSub")
    void createsTheConsumerFunctionApp() {
      Map<String, Object> consumer = resource("azurerm_windows_function_app", APP_PREFIX + "fhub");
      assertThat(consumer).containsEntry("name", STREAM_FUNCTION_APP)
          .containsEntry("service_plan_id", "${azurerm_service_plan." + APP_PREFIX + "psc.id}")
          .containsEntry("storage_account_name", "${azurerm_storage_account." + APP_PREFIX + "sc.name}")
          .containsEntry("site_config", Map.of("application_stack", Map.of("java_version", "21")))
          .doesNotContainKey("zip_deploy_file");
      Map<String, Object> settings = new LinkedHashMap<>();
      settings.put("WEBSITE_RUN_FROM_PACKAGE", "1");
      settings.put("WEBSITE_CONTENTSHARE", APP_PREFIX + "fhub");
      settings.put("BOOSTER_APP_SUFFIX", "contract");
      // buildEventHub runs before the Web PubSub service exists
      settings.put("WebPubSubConnectionString", "");
      settings.put("BOOSTER_ENV", "azure");
      settings.put("BOOSTER_REST_API_URL", "http://" + RESOURCE_GROUP + "apis.eastus.cloudapp.azure.com/azure");
      settings.put("EVENTHUB_CONNECTION_STRING", "${azurerm_eventhub_namespace." + APP_PREFIX
          + "ehn.default_primary_connection_string};EntityPath=${azurerm_eventhub." + APP_PREFIX + "eh.name}");
      settings.put("EVENTHUB_NAME", STREAM_TOPIC);
      settings.put("EVENTHUB_MAX_RETRIES", "5");
      settings.put("EVENTHUB_MODE", "exponential");
      settings.put("COSMOSDB_CONNECTION_STRING", "AccountEndpoint=https://${azurerm_cosmosdb_account." + APP_PREFIX
          + "dba.name}.documents.azure.com:443/;AccountKey=${azurerm_cosmosdb_account." + APP_PREFIX + "dba.primary_key};");
      settings.put("WEBSITE_CONTENTAZUREFILECONNECTIONSTRING",
          "${azurerm_storage_account." + APP_PREFIX + "sc.primary_connection_string}");
      settings.put("BOOSTER_APP_NAME", "");
      settings.put("BOOSTER_APP_CLASS", APPLICATION_CLASS);
      assertThat(consumer.get("app_settings")).isEqualTo(settings);
    }

    @Test
    @DisplayName("creates the Web PubSub service and the booster hub with the placeholder event handler")
    void createsWebPubSub() {
      assertThat(resource("azurerm_web_pubsub", APP_PREFIX + "wps")).isEqualTo(Map.of(
          "capacity", 1, "location", "${azurerm_resource_group." + RESOURCE_GROUP + ".location}",
          "name", RESOURCE_GROUP + "wps", "provider", "azurerm", "resource_group_name", RESOURCE_GROUP,
          "sku", "Free_F1"));
      assertThat(resource("azurerm_web_pubsub_hub", APP_PREFIX + "wpsh")).isEqualTo(Map.of(
          "anonymous_connections_enabled", true,
          "depends_on", List.of("azurerm_windows_function_app." + APP_PREFIX + "func",
              "azurerm_web_pubsub." + APP_PREFIX + "wps"),
          "event_handler", List.of(Map.of(
              "system_events", List.of("connect", "disconnected"),
              "url_template", "https://${azurerm_windows_function_app." + APP_PREFIX
                  + "func.name}.azurewebsites.net/runtime/webhooks/webpubsub?code=PLACEHOLDER",
              "user_event_pattern", "*")),
          "lifecycle", Map.of("ignore_changes", List.of("event_handler")),
          "name", "booster",
          "provider", "azurerm",
          "web_pubsub_id", "${azurerm_web_pubsub." + APP_PREFIX + "wps.id}"));
    }

    @Test
    @DisplayName("routes /azure/graphql and /azure/sensor/health of the gateway to the main function app")
    void routesTheGateway() {
      Map<String, Object> gateway = resource("azurerm_application_gateway", "ag");
      assertThat(gateway).containsEntry("name", RESOURCE_GROUP + "apigw")
          .containsEntry("sku", Map.of("capacity", 1, "name", "Standard_v2", "tier", "Standard_v2"))
          .containsEntry("backend_address_pool", List.of(Map.of("fqdns", List.of(FUNCTION_APP + ".azurewebsites.net"),
              "name", "functionManagementBackendAddressPool")))
          .doesNotContainKey("waf_configuration");
      Map<String, Object> pathMap = map(((List<?>) gateway.get("url_path_map")).getFirst());
      assertThat((List<?>) pathMap.get("path_rule")).extracting(rule -> map(rule).get("paths"))
          .containsExactly(List.of("/azure/graphql/*"), List.of("/azure/sensor"));
      Map<String, Object> rewrites = map(((List<?>) gateway.get("rewrite_rule_set")).getFirst());
      assertThat((List<?>) rewrites.get("rewrite_rule")).extracting(rule -> map(rule).get("condition"), rule -> map(rule).get("url"))
          .containsExactly(
              org.assertj.core.groups.Tuple.tuple(List.of(Map.of("ignore_case", true, "pattern", "/azure/graphql",
                  "variable", "var_request_uri")), Map.of("components", "path_only", "path", "/api/graphql")),
              org.assertj.core.groups.Tuple.tuple(List.of(Map.of("ignore_case", true,
                  "pattern", "/azure/sensor/health(/.*)?", "variable", "var_request_uri")),
                  Map.of("components", "path_only", "path", "/api/sensor/health{var_request_uri_1}")));
    }

    @Test
    @DisplayName("outputs the TypeScript URLs, scoped to the provider construct, with the event hub name in the websocket URL")
    void outputsTheUrls() {
      Map<String, Object> outputs = map(terraform.get("output"));
      assertThat(outputs.keySet()).hasSize(4)
          .allMatch(id -> id.matches("azureFeature(httpURL|graphqlURL|sensorHealthURL|websocketURL)_[0-9A-F]{8}"));
      Map<String, Object> byName = new LinkedHashMap<>();
      outputs.forEach((id, value) -> byName.put(id.substring("azureFeature".length(), id.lastIndexOf('_')), value));
      String baseUrl = "http://${data.azurerm_public_ip.torecontractazuredatapip.fqdn}/azure";
      assertThat(byName).isEqualTo(Map.of(
          "httpURL", Map.of("description", "The base URL for all the auth endpoints", "value", baseUrl),
          "graphqlURL", Map.of("description", "The base URL for sending GraphQL mutations and queries",
              "value", baseUrl + "/graphql"),
          "sensorHealthURL", Map.of("description", "The base URL for getting health information",
              "value", baseUrl + "/sensor/health"),
          "websocketURL", Map.of("description", "The URL for the websocket communication. Used for subscriptions",
              "value", "wss://${azurerm_web_pubsub." + APP_PREFIX + "wps.hostname}/client/hubs/" + STREAM_TOPIC)));
    }
  }

  @Nested
  @Order(3)
  @DisplayName("the function app package")
  class TheFunctionAppPackage {

    @Test
    @DisplayName("declares exactly the functions the TypeScript functions.js registers for the app, in its order")
    void declaresTheFunctions() {
      List<String> functions = entries("functionApp.zip").stream().filter(name -> name.endsWith("/function.json"))
          .map(name -> name.substring(0, name.indexOf('/'))).toList();
      List<String> expected = TypeScriptAzureApp.mainFunctions();
      assertThat(functions).containsExactlyInAnyOrderElementsOf(expected).doesNotContain("eventHandler", "eventConsumer");
      // graphql, sensor-health, eventProducer, connect, disconnect, messages, the notifiers (one per read model, in
      // registration order: TypeScript import order, Java class path order), then the scheduled commands
      assertThat(functions.subList(0, 6)).containsExactlyElementsOf(expected.subList(0, 6));
      assertThat(functions.subList(6, 6 + TypeScriptAzureApp.READ_MODELS.size()))
          .allMatch(name -> name.endsWith("-subscriptions-notifier"));
      assertThat(functions.getLast()).isEqualTo("scheduleFunction-CheckCartCount");
    }

    @Test
    @DisplayName("binds the HTTP functions to the GraphQL and health entry points")
    void httpFunctions() {
      assertThat(entry("functionApp.zip", "graphql/function.json")).isEqualTo("""
          {
            "bindings": [
              {
                "type": "httpTrigger",
                "direction": "in",
                "name": "request",
                "methods": [
                  "POST"
                ],
                "authLevel": "anonymous"
              },
              {
                "type": "http",
                "direction": "out",
                "name": "$return"
              }
            ],
            "scriptFile": "../%s.jar",
            "entryPoint": "%s.graphql"
          }""".formatted(APP_NAME, ENTRY_POINT_CLASS));
      assertThat(entry("functionApp.zip", "sensor-health/function.json")).isEqualTo("""
          {
            "bindings": [
              {
                "type": "httpTrigger",
                "direction": "in",
                "name": "request",
                "methods": [
                  "GET"
                ],
                "authLevel": "anonymous",
                "route": "sensor/health/{*componentPath}"
              },
              {
                "type": "http",
                "direction": "out",
                "name": "$return"
              }
            ],
            "scriptFile": "../%s.jar",
            "entryPoint": "%s.sensorHealth"
          }""".formatted(APP_NAME, ENTRY_POINT_CLASS));
    }

    @Test
    @DisplayName("binds the events store change feed to the event stream producer (event streaming is enabled)")
    void eventProducer() {
      assertThat(entry("functionApp.zip", "eventProducer/function.json"))
          .isEqualTo(cosmosTrigger(EVENTS_STORE, "eventProducer"));
    }

    @Test
    @DisplayName("binds the Web PubSub connect, disconnect and message events of the booster hub")
    void webPubSubFunctions() {
      for (String[] function : new String[][] {{"connect", "system", "connect"}, {"disconnect", "system", "disconnect"},
          {"messages", "user", "message"}}) {
        assertThat(entry("functionApp.zip", function[0] + "/function.json")).isEqualTo("""
            {
              "bindings": [
                {
                  "type": "webPubSubTrigger",
                  "direction": "in",
                  "name": "request",
                  "hub": "booster",
                  "eventType": "%s",
                  "eventName": "%s",
                  "connection": "WebPubSubConnectionString"
                }
              ],
              "scriptFile": "../%s.jar",
              "entryPoint": "%s.%s"
            }""".formatted(function[1], function[2], APP_NAME, ENTRY_POINT_CLASS, function[0]));
      }
    }

    @Test
    @DisplayName("binds one subscriptions notifier to the container of every read model")
    void subscriptionsNotifiers() {
      for (String readModel : TypeScriptAzureApp.READ_MODELS) {
        assertThat(entry("functionApp.zip", readModel + "-subscriptions-notifier/function.json")).as(readModel)
            .isEqualTo(cosmosTrigger(DATABASE + "-" + readModel, "subscriptionsNotifier"));
      }
    }

    @Test
    @DisplayName("schedules CheckCartCount every minute with the NCRONTAB seconds field")
    void scheduledCommand() {
      assertThat(entry("functionApp.zip", "scheduleFunction-CheckCartCount/function.json")).isEqualTo("""
          {
            "bindings": [
              {
                "type": "timerTrigger",
                "direction": "in",
                "name": "timer",
                "schedule": "%s"
              }
            ],
            "scriptFile": "../%s.jar",
            "entryPoint": "%s.scheduledCommand"
          }""".formatted(TypeScriptAzureApp.SCHEDULED_FUNCTIONS.get("scheduleFunction-CheckCartCount"), APP_NAME,
          ENTRY_POINT_CLASS));
    }

    @Test
    @DisplayName("carries the default host.json, not the host.json asset (createZipV4 ignores it)")
    void hostJson() {
      assertThat(entry("functionApp.zip", "host.json")).isEqualTo(DEFAULT_HOST_JSON);
      assertThat(read(project.directory().resolve("host.json"))).contains("\"functionTimeout\": \"00:07:00\"");
      assertThat(entries("functionApp.zip")).filteredOn(name -> name.endsWith("host.json")).containsExactly("host.json");
    }

    @Test
    @DisplayName("carries the application jar, the runtime jars and the assets, and no Node file")
    void layout() {
      List<String> entries = entries("functionApp.zip");
      List<String> expected = new ArrayList<>();
      expected.add(APP_NAME + ".jar");
      expected.addAll(libraries());
      expected.addAll(TypeScriptAzureApp.ASSET_FILES);
      for (String function : TypeScriptAzureApp.mainFunctions()) {
        expected.add(function + "/function.json");
      }
      expected.add("host.json");
      assertThat(entries).containsExactlyInAnyOrderElementsOf(expected)
          .doesNotContain("functions.js", "package.json");
      assertThat(entries.getFirst()).isEqualTo(APP_NAME + ".jar");
      assertThat(entries).filteredOn(name -> name.startsWith("lib/"))
          .contains("lib/booster-framework-core-4.1.0.jar", "lib/booster-framework-provider-azure-4.1.0.jar");
      for (String asset : TypeScriptAzureApp.ASSET_FILES) {
        assertThat(entry("functionApp.zip", asset)).as(asset).isEqualTo(read(project.directory().resolve(asset)));
      }
    }

    @Test
    @DisplayName("packs every compiled class of the application in the application jar")
    void applicationJar() throws IOException {
      Path jar = Files.createTempFile(project.directory(), "app", ".jar");
      try (ZipFile zip = new ZipFile(project.directory().resolve("functionApp.zip").toFile())) {
        Files.write(jar, zip.getInputStream(zip.getEntry(APP_NAME + ".jar")).readAllBytes());
      }
      List<String> classes;
      Path classesDirectory = project.directory().resolve("target/classes");
      try (Stream<Path> files = Files.walk(classesDirectory)) {
        classes = files.filter(Files::isRegularFile).map(file -> classesDirectory.relativize(file).toString()
            .replace('\\', '/')).toList();
      }
      try (ZipFile application = new ZipFile(jar.toFile())) {
        List<String> names = application.stream().map(ZipEntry::getName).filter(name -> !name.endsWith("/")).toList();
        assertThat(names).containsAll(classes).contains("com/boostercloud/integration/Main.class",
            "com/boostercloud/integration/config/Config.class");
      } finally {
        Files.delete(jar);
      }
    }
  }

  @Nested
  @Order(4)
  @DisplayName("the event stream consumer package")
  class TheConsumerPackage {

    @Test
    @DisplayName("declares only the Event Hub consumer of the stream topic")
    void consumer() {
      List<String> entries = entries("consumerFunctionApp.zip");
      assertThat(entries).filteredOn(name -> name.endsWith("/function.json")).containsExactly("eventConsumer/function.json");
      assertThat(entry("consumerFunctionApp.zip", "eventConsumer/function.json")).isEqualTo("""
          {
            "bindings": [
              {
                "type": "eventHubTrigger",
                "direction": "in",
                "name": "messages",
                "connection": "EVENTHUB_CONNECTION_STRING",
                "eventHubName": "%s",
                "cardinality": "many",
                "dataType": "string"
              }
            ],
            "scriptFile": "../%s.jar",
            "entryPoint": "%s.eventConsumer"
          }""".formatted(STREAM_TOPIC, APP_NAME, ENTRY_POINT_CLASS));
      assertThat(entry("consumerFunctionApp.zip", "host.json")).isEqualTo(DEFAULT_HOST_JSON);
      List<String> expected = new ArrayList<>();
      expected.add(APP_NAME + ".jar");
      expected.addAll(libraries());
      expected.addAll(TypeScriptAzureApp.ASSET_FILES);
      expected.add("eventConsumer/function.json");
      expected.add("host.json");
      assertThat(entries).containsExactlyInAnyOrderElementsOf(expected);
    }
  }

  @Nested
  @Order(5)
  @DisplayName("the Web PubSub bootstrap package")
  class TheBootstrapPackage {

    @Test
    @DisplayName("declares the baseWebPubSubBinding user message function that makes the runtime create the extension key")
    void bootstrap() {
      assertThat(entries("baseWebPubSubBinding.zip"))
          .containsExactly("booster-base.jar", "baseWebPubSubBinding/function.json", "host.json");
      assertThat(entry("baseWebPubSubBinding.zip", "baseWebPubSubBinding/function.json")).isEqualTo("""
          {
            "bindings": [
              {
                "type": "webPubSubTrigger",
                "direction": "in",
                "name": "request",
                "hub": "booster",
                "eventType": "user",
                "eventName": "message",
                "connection": "WebPubSubConnectionString"
              }
            ],
            "scriptFile": "../booster-base.jar",
            "entryPoint": "com.boostercloud.framework.provider.azure.infrastructure.base.BaseWebPubSubBinding.run"
          }""");
      assertThat(entry("baseWebPubSubBinding.zip", "host.json")).isEqualTo(DEFAULT_HOST_JSON);
    }
  }

  // ---------------------------------------------------------------------------------------------------------------

  private static String cosmosTrigger(String container, String entryPoint) {
    return """
        {
          "bindings": [
            {
              "type": "cosmosDBTrigger",
              "direction": "in",
              "name": "documents",
              "connection": "COSMOSDB_CONNECTION_STRING",
              "databaseName": "%s",
              "containerName": "%s",
              "createLeaseContainerIfNotExists": true,
              "leaseContainerName": "leases",
              "dataType": "string"
            }
          ],
          "scriptFile": "../%s.jar",
          "entryPoint": "%s.%s"
        }""".formatted(DATABASE, container, APP_NAME, ENTRY_POINT_CLASS, entryPoint);
  }

  private List<String> libraries() {
    try (Stream<Path> jars = Files.list(E2eSettings.libDirectory())) {
      return jars.map(jar -> "lib/" + jar.getFileName()).filter(name -> name.endsWith(".jar")).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private List<String> entries(String zipName) {
    try (ZipFile zip = new ZipFile(project.directory().resolve(zipName).toFile())) {
      return zip.stream().map(ZipEntry::getName).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String entry(String zipName, String entryName) {
    try (ZipFile zip = new ZipFile(project.directory().resolve(zipName).toFile())) {
      ZipEntry entry = zip.getEntry(entryName);
      assertThat(entry).as("%s in %s", entryName, zipName).isNotNull();
      return new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Map<String, Object> resource(String type, String id) {
    Map<String, Object> resource = new LinkedHashMap<>(map(map(map(terraform.get("resource")).get(type)).get(id)));
    resource.remove("//");
    return resource;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    assertThat(value).isInstanceOf(Map.class);
    return (Map<String, Object>) value;
  }

  private static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
