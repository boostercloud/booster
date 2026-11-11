package com.boostercloud.framework.provider.azure.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.azure.infrastructure.azure.ArmError;
import com.boostercloud.framework.provider.azure.infrastructure.azure.HttpTransport;
import com.boostercloud.framework.provider.azure.infrastructure.process.Command;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.InfrastructureRocket;
import com.boostercloud.framework.provider.azure.infrastructure.support.FakeHttpTransport;
import com.boostercloud.framework.provider.azure.infrastructure.support.Fixtures;
import com.boostercloud.framework.provider.azure.infrastructure.support.RecordingCommandRunner;
import com.boostercloud.framework.provider.azure.infrastructure.support.RecordingSleeper;
import com.boostercloud.framework.provider.azure.infrastructure.support.TestEnvironment;
import com.boostercloud.framework.provider.azure.infrastructure.support.TestRockets;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.provider.ProviderInfrastructure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/**
 * Contract tests of the {@code synth}, {@code deploy} and {@code nuke} flows: the exact commands (arguments, working
 * directory, environment) and HTTP calls (Entra ID, Resource Manager, Kudu, the function app), with nothing executed.
 */
@ExtendWith(TestEnvironment.class)
class AzureInfrastructureTest {

  private static final String SUBSCRIPTION = "https://management.azure.com/subscriptions/subscription-id";
  private static final String RG = SUBSCRIPTION + "/resourceGroups/mystoreproductionrg";
  private static final String SITES = RG + "/providers/Microsoft.Web/sites/";
  private static final String HUB = RG + "/providers/Microsoft.SignalRService/webPubSub/mystoreproductionrgwps/hubs/booster";
  private static final String KEYS = SITES + "mystoreproductionrgfpr/host/default/listkeys";

  @TempDir Path root;

  private RecordingCommandRunner commands;
  private FakeHttpTransport http;
  private RecordingSleeper sleeper;

  @BeforeEach
  void setUp() {
    commands = new RecordingCommandRunner();
    http = new FakeHttpTransport();
    sleeper = new RecordingSleeper();
  }

  private AzureInfrastructure infrastructure(List<InfrastructureRocket> rockets) {
    return new AzureInfrastructure(rockets, config -> Fixtures.context(root, commands, http, sleeper));
  }

  private void scriptPublishingCredentials(String app) {
    http.on("POST " + SITES + app + "/config/publishingcredentials/list", HttpTransport.Response.of(200,
        "{\"name\":\"" + app + "\",\"properties\":{\"publishingUserName\":\"$" + app
            + "\",\"publishingPassword\":\"pw\"}}"));
    http.on("POST https://" + app + ".scm.azurewebsites.net/api/zipDeploy", HttpTransport.Response.of(202, ""));
  }

  private void scriptKeys(HttpTransport.Response... responses) {
    http.on("POST " + KEYS, responses);
    http.on("POST https://mystoreproductionrgfpr.azurewebsites.net/api/graphql", HttpTransport.Response.of(200, "{}"));
    http.on("PUT " + HUB, HttpTransport.Response.of(200, "{\"name\":\"booster\"}"));
  }

  @Test
  void synth_builds_the_terraform_json_and_the_packages_without_running_anything() {
    infrastructure(null).synth(Fixtures.config());
    Path normalizedRoot = root.toAbsolutePath().normalize();
    assertThat(normalizedRoot.resolve("cdktf.out/stacks/my-storeproduction/cdk.tf.json")).isRegularFile();
    assertThat(normalizedRoot.resolve("cdktf.out/manifest.json")).isRegularFile();
    assertThat(normalizedRoot.resolve("functionApp.zip")).isRegularFile();
    assertThat(normalizedRoot.resolve("baseWebPubSubBinding.zip")).isRegularFile();
    assertThat(normalizedRoot.resolve("consumerFunctionApp.zip")).doesNotExist();
    assertThat(normalizedRoot.resolve("cdktf.json")).doesNotExist();
    assertThat(commands.commands()).isEmpty();
    assertThat(http.requests()).isEmpty();
  }

  @Test
  void synth_with_event_streaming_builds_the_consumer_package_and_skips_the_base_one_without_subscriptions() {
    BoosterConfig config = Fixtures.streamingConfig();
    config.enableSubscriptions = false;
    infrastructure(List.of()).synth(config);
    assertThat(root.resolve("consumerFunctionApp.zip")).isRegularFile();
    assertThat(root.resolve("baseWebPubSubBinding.zip")).doesNotExist();
  }

  @Test
  void deploy_runs_terraform_uploads_the_package_and_updates_the_web_pub_sub_hub() {
    scriptPublishingCredentials("mystoreproductionrgfpr");
    scriptKeys(HttpTransport.Response.of(200, "{\"masterKey\":\"m\",\"systemKeys\":{}}"),
        HttpTransport.Response.of(200, "{\"masterKey\":\"m\",\"systemKeys\":{\"webpubsub_extension\":\"the-key\"}}"));

    infrastructure(null).deploy(Fixtures.config());

    Path stackDirectory = root.toAbsolutePath().normalize().resolve("cdktf.out/stacks/my-storeproduction");
    assertThat(commands.commands()).containsExactly(
        new Command(stackDirectory, List.of("terraform", "init", "-input=false"), Map.of("TF_IN_AUTOMATION", "true"),
            false),
        new Command(stackDirectory, List.of("terraform", "plan", "-input=false", "-out=plan"),
            Map.of("TF_IN_AUTOMATION", "true"), false),
        new Command(stackDirectory, List.of("terraform", "apply", "-input=false", "-auto-approve", "plan"),
            Map.of("TF_IN_AUTOMATION", "true"), false));
    assertThat(http.calls()).containsExactly(
        "POST " + SITES + "mystoreproductionrgfpr/config/publishingcredentials/list?api-version=2023-12-01",
        "POST https://mystoreproductionrgfpr.scm.azurewebsites.net/api/zipDeploy?isAsync=true",
        "POST https://mystoreproductionrgfpr.azurewebsites.net/api/graphql",
        "POST " + KEYS + "?api-version=2023-12-01",
        "POST " + KEYS + "?api-version=2023-12-01",
        "PUT " + HUB + "?api-version=2023-02-01");
    HttpTransport.Request coldStart = http.requests("POST", "https://mystoreproductionrgfpr.azurewebsites.net").get(0);
    assertThat(coldStart.bodyText()).isEqualTo("{\"query\":\"{ __typename }\"}");
    assertThat(coldStart.header("Content-Type")).contains("application/json");
    assertThat(coldStart.timeout()).hasSeconds(10);
    HttpTransport.Request upload = http.requests("POST", "https://mystoreproductionrgfpr.scm").get(0);
    assertThat(upload.body()).isEqualTo(root.toAbsolutePath().normalize().resolve("functionApp.zip"));
    assertThat(BoosterJson.parse(http.requests("PUT", HUB).get(0).bodyText())).isEqualTo(Map.of("properties", Map.of(
        "eventHandlers", List.of(Map.of(
            "urlTemplate", "https://mystoreproductionrgfpr.azurewebsites.net/runtime/webhooks/webpubsub?code=the-key",
            "userEventPattern", "*",
            "systemEvents", List.of("connect", "disconnected"))))));
    assertThat(sleeper.sleeps()).containsExactly(30000L, 10000L);
    assertThat(http.requests().get(0).uri().getHost()).isEqualTo("login.microsoftonline.com");
    assertThat(http.requests("POST", "https://login.microsoftonline.com")).hasSize(1);
  }

  @Test
  void deploy_with_event_streaming_and_rockets_uploads_every_package() {
    BoosterConfig config = Fixtures.streamingConfig();
    config.enableSubscriptions = false;
    config.rockets = TestRockets.descriptors();
    scriptPublishingCredentials("mystoreproductionrgfpr");
    scriptPublishingCredentials("mystoreproductionrgfcs");
    scriptPublishingCredentials("mystoreproductionrgwebhookfunc");
    scriptPublishingCredentials("mystoreproductionrgfilesfunc");

    infrastructure(List.of(new TestRockets.WebhookRocket(), new TestRockets.FilesRocket())).deploy(config);

    List<String> uploads = http.calls().stream().filter(call -> call.contains(".scm.azurewebsites.net")).toList();
    assertThat(uploads.subList(0, 2)).containsExactly(
        "POST https://mystoreproductionrgfpr.scm.azurewebsites.net/api/zipDeploy?isAsync=true",
        "POST https://mystoreproductionrgfcs.scm.azurewebsites.net/api/zipDeploy?isAsync=true");
    assertThat(uploads.subList(2, 4)).containsExactlyInAnyOrder(
        "POST https://mystoreproductionrgwebhookfunc.scm.azurewebsites.net/api/zipDeploy?isAsync=true",
        "POST https://mystoreproductionrgfilesfunc.scm.azurewebsites.net/api/zipDeploy?isAsync=true");
    assertThat(http.requests("POST", "https://mystoreproductionrgfcs.scm").get(0).body())
        .isEqualTo(root.toAbsolutePath().normalize().resolve("consumerFunctionApp.zip"));
    assertThat(http.requests("POST", "https://mystoreproductionrgwebhookfunc.scm").get(0).body())
        .isEqualTo(root.toAbsolutePath().normalize().resolve("rocket_mystoreproductionrgwebhookfunc.zip"));
    assertThat(http.calls()).noneMatch(call -> call.contains("webPubSub"));
    assertThat(sleeper.sleeps()).isEmpty();
  }

  private void addClassFile(int major) throws java.io.IOException {
    Path file = root.resolve("target/classes/com/example/store/Modern.class");
    java.nio.file.Files.createDirectories(file.getParent());
    java.nio.file.Files.write(file,
        new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, (byte) (major >> 8), (byte) major});
  }

  @Test
  void deploy_refuses_class_files_the_function_apps_java_runtime_cannot_load() throws Exception {
    addClassFile(71);

    assertThatThrownBy(() -> infrastructure(null).deploy(Fixtures.config()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("The function apps run Java 21 (BOOSTER_AZURE_JAVA_VERSION), which loads class files"
            + " up to version 65, but ")
        .hasMessageContaining("Modern.class has class file version 71 (Java 27)");
    assertThat(commands.commands()).isEmpty();
    assertThat(http.requests()).isEmpty();
    assertThat(root.resolve("cdktf.out")).doesNotExist();
  }

  @Test
  void deploy_proceeds_with_loadable_class_files_or_when_the_check_is_skipped() throws Exception {
    addClassFile(65);
    scriptPublishingCredentials("mystoreproductionrgfpr");
    infrastructure(null).deploy(Fixtures.noSubscriptionsConfig());
    assertThat(commands.commands()).hasSize(3);

    addClassFile(71);
    com.boostercloud.framework.types.EnvironmentVariables.set("BOOSTER_AZURE_SKIP_JAVA_VERSION_CHECK", "true");
    infrastructure(null).deploy(Fixtures.noSubscriptionsConfig());
    assertThat(commands.commands()).hasSize(6);
  }

  @Test
  void synth_only_warns_about_class_files_the_runtime_cannot_load() throws Exception {
    addClassFile(71);
    BoosterConfig config = Fixtures.config();
    List<String> warnings = new java.util.ArrayList<>();
    config.logger = new com.boostercloud.framework.types.Logger() {
      @Override
      public void debug(Object message, Object... optionalParams) {}

      @Override
      public void info(Object message, Object... optionalParams) {}

      @Override
      public void warn(Object message, Object... optionalParams) {
        warnings.add(message + " " + java.util.Arrays.toString(optionalParams));
      }

      @Override
      public void error(Object message, Object... optionalParams) {}
    };

    infrastructure(null).synth(config);

    assertThat(root.resolve("functionApp.zip")).isRegularFile();
    assertThat(warnings).singleElement().asString().contains("has class file version 71 (Java 27)");
  }

  @Test
  void deploy_reports_terraform_failures_and_stops() {
    commands.failing("terraform plan", "Error: Invalid provider configuration");
    assertThatThrownBy(() -> infrastructure(null).deploy(Fixtures.config()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Deployment of application my-store failed. Check Terraform logs. \n"
            + "Error: Invalid provider configuration");
    assertThat(commands.commands()).hasSize(2);
    assertThat(http.requests()).isEmpty();
  }

  @Test
  void deploy_uses_the_configured_terraform_binary() {
    com.boostercloud.framework.types.EnvironmentVariables.set("BOOSTER_TERRAFORM_BINARY", "tofu");
    BoosterConfig config = Fixtures.noSubscriptionsConfig();
    scriptPublishingCredentials("mystoreproductionrgfpr");
    infrastructure(null).deploy(config);
    assertThat(commands.commands()).extracting(command -> command.arguments().get(0)).containsOnly("tofu");
  }

  @Test
  void the_hub_update_gives_up_after_thirty_attempts_re_triggering_the_host_every_five() {
    BoosterConfig config = Fixtures.config();
    Logger logger = Mockito.mock(Logger.class);
    config.logger = logger;
    scriptKeys(HttpTransport.Response.of(500, "{\"error\":{\"code\":\"Busy\",\"message\":\"busy\"}}"),
        HttpTransport.Response.of(200, "{\"systemKeys\":{\"webpubsub_extension\":\"\"}}"));
    http.failing("POST https://mystoreproductionrgfpr.azurewebsites.net/api/graphql");
    InfrastructureContext context = Fixtures.context(root, commands, http, sleeper);

    String message = "webpubsub_extension key not found after waiting for function host initialization. Web PubSub"
        + " subscriptions will not work. You can manually retrieve the key from: Azure Portal > Function App"
        + " \"mystoreproductionrgfpr\" > App keys > System keys > webpubsub_extension";
    assertThatThrownBy(() -> AzureInfrastructure.updateWebPubSubHub(config, context, "mystoreproductionrg",
        "mystoreproductionrgfpr", "mystoreproductionrgwps", "booster"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(message);

    List<Long> expectedSleeps = new ArrayList<>(List.of(30000L));
    expectedSleeps.addAll(Collections.nCopies(30, 10000L));
    assertThat(sleeper.sleeps()).isEqualTo(expectedSleeps);
    assertThat(http.requests("POST", KEYS)).hasSize(30);
    assertThat(http.requests("POST", "https://mystoreproductionrgfpr.azurewebsites.net/api/graphql")).hasSize(7);
    assertThat(http.requests("PUT", HUB)).isEmpty();
    Mockito.verify(logger).error("[Booster]|index#updateWebPubSubHub: ", message);
    Mockito.verify(logger).info("[Booster]|index#updateWebPubSubHub: ", "Attempt 30/30: Key not available yet, waiting...");
    Mockito.verify(logger, Mockito.times(6)).info("[Booster]|index#updateWebPubSubHub: ", "Re-triggering function host...");
  }

  @Test
  void the_hub_update_logs_and_rethrows_arm_failures() {
    BoosterConfig config = Fixtures.config();
    Logger logger = Mockito.mock(Logger.class);
    config.logger = logger;
    scriptKeys(HttpTransport.Response.of(200, "{\"systemKeys\":{\"webpubsub_extension\":\"k\"}}"));
    http.on("PUT " + HUB, HttpTransport.Response.of(400,
        "{\"error\":{\"code\":\"BadRequest\",\"message\":\"Invalid hub\"}}"));
    InfrastructureContext context = Fixtures.context(root, commands, http, sleeper);
    assertThatThrownBy(() -> AzureInfrastructure.updateWebPubSubHub(config, context, "mystoreproductionrg",
        "mystoreproductionrgfpr", "mystoreproductionrgwps", "booster"))
        .isInstanceOf(ArmError.class).hasMessage("Invalid hub");
    Mockito.verify(logger).info("[Booster]|index#updateWebPubSubHub: ", "webpubsub_extension key found after 1 attempts");
    Mockito.verify(logger).error(Mockito.eq("[Booster]|index#updateWebPubSubHub: "),
        Mockito.eq("Failed to update Web PubSub hub:"), Mockito.any(ArmError.class));
    assertThat(sleeper.sleeps()).containsExactly(30000L);
  }

  @Test
  void nuke_unmounts_the_rockets_and_deletes_the_resource_group() {
    http.on("DELETE " + SUBSCRIPTION + "/resourcegroups/mystoreproductionrg", HttpTransport.Response.of(202, "",
        "Location", "https://management.azure.com/operationresults/1", "Retry-After", "15"));
    http.on("GET https://management.azure.com/operationresults/1", HttpTransport.Response.of(200, ""));
    TestRockets.WebhookRocket rocket = new TestRockets.WebhookRocket();

    infrastructure(List.of(rocket, new TestRockets.FilesRocket())).nuke(Fixtures.config());

    assertThat(rocket.unmounted).isEqualTo(1);
    assertThat(http.calls()).containsExactly(
        "DELETE " + SUBSCRIPTION + "/resourcegroups/mystoreproductionrg?api-version=2021-04-01",
        "GET https://management.azure.com/operationresults/1");
    assertThat(sleeper.sleeps()).containsExactly(15000L);
    assertThat(commands.commands()).isEmpty();
    assertThat(Files.exists(root.resolve("cdktf.out"))).isFalse();
  }

  @Test
  void nuke_propagates_resource_manager_errors() {
    http.on("DELETE " + SUBSCRIPTION + "/resourcegroups/", HttpTransport.Response.of(404,
        "{\"error\":{\"code\":\"ResourceGroupNotFound\",\"message\":\"Resource group 'mystoreproductionrg' could not be"
            + " found.\"}}"));
    assertThatThrownBy(() -> infrastructure(null).nuke(Fixtures.config()))
        .isInstanceOfSatisfying(ArmError.class, error -> assertThat(error.code()).isEqualTo("ResourceGroupNotFound"));
  }

  @Test
  void start_is_not_supported() {
    AzureInfrastructure infrastructure = infrastructure(null);
    assertThatThrownBy(() -> infrastructure.start(Fixtures.config(), 3000))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Attempted to perform the 'start' operation with a provider that does not support this feature,"
            + " please check your environment configuration.");
    assertThat(infrastructure.supports(ProviderInfrastructure.Operation.START)).isFalse();
    assertThat(infrastructure.supports(ProviderInfrastructure.Operation.DEPLOY)).isTrue();
    assertThat(infrastructure.supports(ProviderInfrastructure.Operation.NUKE)).isTrue();
    assertThat(infrastructure.supports(ProviderInfrastructure.Operation.SYNTH)).isTrue();
  }

  @Test
  void the_build_logs_the_typescript_messages() {
    BoosterConfig config = Fixtures.config();
    Logger logger = Mockito.mock(Logger.class);
    config.logger = logger;
    scriptPublishingCredentials("mystoreproductionrgfpr");
    scriptKeys(HttpTransport.Response.of(200, "{\"systemKeys\":{\"webpubsub_extension\":\"k\"}}"));
    infrastructure(null).deploy(config);
    Mockito.verify(logger).info("[Booster]|index#deployApp: ", "Deploying app my-store");
    Mockito.verify(logger).info("[Booster]|ApplicationBuilder#synthApplication: ", "Synth...");
    Mockito.verify(logger).info("[Booster]|RocketBuilder#synthRocket: ", "Synth rockets...");
    Mockito.verify(logger).info("[Booster]|function-zip#deployZip: ", "Uploading zip file");
    Mockito.verify(logger).info("[Booster]|function-zip#deployZip: ", "Zip file uploaded");
    Mockito.verify(logger).info("[Booster]|index#updateWebPubSubHub: ", "Updating Web PubSub hub...");
  }
}
