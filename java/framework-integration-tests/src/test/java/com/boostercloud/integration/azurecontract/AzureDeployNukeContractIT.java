package com.boostercloud.integration.azurecontract;

import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.APP_NAME;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.FUNCTION_APP;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.RESOURCE_GROUP;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.STACK;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.STREAM_FUNCTION_APP;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.WEB_PUB_SUB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.common.RocketLoader;
import com.boostercloud.framework.provider.azure.infrastructure.AzureInfrastructure;
import com.boostercloud.framework.provider.azure.infrastructure.Infrastructure;
import com.boostercloud.framework.provider.azure.infrastructure.InfrastructureContext;
import com.boostercloud.framework.provider.azure.infrastructure.helper.AzCliHelper;
import com.boostercloud.framework.provider.azure.infrastructure.helper.Params;
import com.boostercloud.framework.provider.azure.infrastructure.process.Command;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.InfrastructureRocket;
import com.boostercloud.framework.provider.azure.infrastructure.testhelper.AzureTestHelper;
import com.boostercloud.framework.provider.azure.infrastructure.types.ResourceGroup;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.integration.helper.AppHelper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * The Azure contract of the application, part 2: <b>deploy and nuke</b> (spec 17 §3.3, TypeScript
 * {@code integration/provider-specific/azure/deployment} and {@code nuke}), with nothing deployed. The application is
 * started as {@code Booster.run} starts it before {@code deploy}/{@code nuke} ({@code BOOSTER_ENV=azure}, the deploying
 * machine's settings, the sandbox projects of the TypeScript setups), and its Azure infrastructure runs with the
 * injectable command runner, HTTP transport and sleeper of {@link InfrastructureContext} wired to a stateful fake
 * subscription ({@link FakeAzureCloud}). The suites assert what TypeScript asserts after the real deploy and nuke (the
 * resource group exists, then {@code ResourceGroupNotFound}), and the exact command sequence and Azure calls that lead
 * there: {@code terraform init/plan/apply} in the stack directory, the Kudu uploads of the main and the event stream
 * consumer packages, the post-deploy Web PubSub hub update with the extension key, and the resource group deletion.
 */
@DisplayName("Azure deploy and nuke contract")
class AzureDeployNukeContractIT {

  static final String TENANT = "tenant-id";
  static final String SUBSCRIPTION = "subscription-id";
  static final String SUBSCRIPTION_URL = FakeAzureCloud.ARM + "/subscriptions/" + SUBSCRIPTION;
  static final String SITES = SUBSCRIPTION_URL + "/resourceGroups/" + RESOURCE_GROUP + "/providers/Microsoft.Web/sites/";
  static final String HUB = SUBSCRIPTION_URL + "/resourceGroups/" + RESOURCE_GROUP
      + "/providers/Microsoft.SignalRService/webPubSub/" + WEB_PUB_SUB + "/hubs/booster?api-version=2023-02-01";
  static final Map<String, String> TERRAFORM_ENVIRONMENT = Map.of("TF_IN_AUTOMATION", "true");

  /** The Azure settings of the deploying machine ({@code AzureTestHelper.ensureAzureConfiguration} checks them). */
  static final Params.Configuration AZURE = Params.Configuration.of("app-id", "app-secret", TENANT, SUBSCRIPTION,
      "East US");

  /** The deploying machine: the TypeScript {@code integration/azure} scripts plus every setting the deploy reads. */
  static final Map<String, String> DEPLOYING_MACHINE = deployingMachine();

  private static Map<String, String> deployingMachine() {
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put(AppHelper.BOOSTER_APP_SUFFIX, TypeScriptAzureApp.APP_SUFFIX);
    variables.put("TESTED_PROVIDER", "AZURE");
    variables.put("REGION", "East US");
    variables.put("AZURE_APP_ID", AZURE.appId());
    variables.put("AZURE_SECRET", AZURE.secret());
    variables.put("AZURE_TENANT_ID", AZURE.tenantId());
    variables.put("AZURE_SUBSCRIPTION_ID", AZURE.subscriptionId());
    variables.put("AZURE_MAX_CONTAINER_THROUGHPUT", "1000");
    variables.put("AZURE_MAX_DATABASE_THROUGHPUT", "1000");
    variables.put("BOOSTER_AZURE_SERVICE_PLAN_BASIC", "false");
    variables.put("BOOSTER_USE_WAF", "false");
    variables.put("BOOSTER_AZURE_JAVA_VERSION", "21");
    variables.put("BOOSTER_TERRAFORM_BINARY", "terraform");
    variables.put("BOOSTER_APP_NAME", "");
    variables.put("BOOSTER_APP_CLASS", "");
    return Map.copyOf(variables);
  }

  private InProcessAzureApplication application;
  private BoosterConfig config;
  private final FakeAzureCloud cloud = new FakeAzureCloud(TENANT, SUBSCRIPTION);

  @BeforeAll
  void startApplication() {
    application = InProcessAzureApplication.prepare(DEPLOYING_MACHINE);
    config = application.start();
  }

  @AfterAll
  void stopApplication() {
    if (application != null) {
      application.close();
    }
  }

  /**
   * {@code Infrastructure(config.rockets)} as the provider loads it, with the given outside world (the class the
   * function apps boot is what {@code InfrastructureContext.resolveApplicationClass()} returns in a JVM the CLI
   * launched: its main class).
   */
  private AzureInfrastructure infrastructure(CliBuiltProject project, FakeAzureCloud outside) {
    return Infrastructure.infrastructure(config.rockets,
        descriptor -> RocketLoader.loadRocket(descriptor, InfrastructureRocket.class),
        ignored -> InfrastructureContext.builder()
            .projectRoot(project.directory())
            .applicationClass(TypeScriptAzureApp.APPLICATION_CLASS)
            .deploySources(project.deploySources(config.assets))
            .commandRunner(outside)
            .httpTransport(outside)
            .sleeper(outside)
            .configuration(AZURE)
            .clock(Clock.fixed(Instant.parse("2026-11-10T09:00:00Z"), ZoneOffset.UTC))
            .build());
  }

  @Test
  @Order(0)
  @DisplayName("the provider of the azure environment loads the Azure infrastructure, which cannot start locally")
  void theProviderLoadsTheInfrastructure() {
    assertThat(config.environmentName).isEqualTo("azure");
    assertThat(config.appName).isEqualTo(APP_NAME);
    assertThat(config.provider().infrastructure()).isInstanceOf(AzureInfrastructure.class);
    assertThat(config.provider().infrastructure().supports(
        com.boostercloud.framework.types.provider.ProviderInfrastructure.Operation.START)).isFalse();
    assertThat(config.assets).containsExactlyElementsOf(TypeScriptAzureApp.ASSETS);
  }

  @Nested
  @Order(1)
  @DisplayName("After deployment")
  class AfterDeployment {

    private CliBuiltProject project;
    private Path stackDirectory;

    /** {@code azure/deployment/setup.ts}: sandbox {@code deploy} with the azure assets, configuration check, deploy. */
    @BeforeAll
    void deploy() {
      project = CliBuiltProject.create("deploy", List.of("assets", "assetFile.txt", "host.json"));
      AzureTestHelper.ensureAzureConfiguration(AZURE);
      infrastructure(project, cloud).deploy(config);
      stackDirectory = project.directory().resolve("cdktf.out").resolve("stacks").resolve(STACK);
    }

    @AfterAll
    void removeSandbox() {
      if (project != null) {
        project.close();
      }
    }

    @Nested
    @Order(1)
    @DisplayName("the ARM template")
    class TheArmTemplate {

      @Test
      @DisplayName("has been created successfully")
      void hasBeenCreatedSuccessfully() {
        // AzureTestHelper.checkResourceGroup(applicationName(), environmentName) runs az group show
        ResourceGroup resourceGroup = new AzCliHelper(cloud)
            .getResourceGroup(AppHelper.applicationName(), AppHelper.checkAndGetCurrentEnv());
        assertThat(resourceGroup).isNotNull();
        assertThat(resourceGroup.name()).isEqualTo(RESOURCE_GROUP);
        assertThat(resourceGroup.location()).isEqualTo("eastus");
        assertThat(cloud.commands()).extracting(Command::commandLine)
            .endsWith("az group show --name " + RESOURCE_GROUP);
      }
    }

    @Test
    @Order(2)
    @DisplayName("synthesizes the stack and then runs terraform init, plan and apply in its directory")
    void runsTerraform() {
      assertThat(stackDirectory.resolve("cdk.tf.json")).isRegularFile();
      assertThat(cloud.commands()).startsWith(
          new Command(stackDirectory, List.of("terraform", "init", "-input=false"), TERRAFORM_ENVIRONMENT, false),
          new Command(stackDirectory, List.of("terraform", "plan", "-input=false", "-out=plan"), TERRAFORM_ENVIRONMENT,
              false),
          new Command(stackDirectory, List.of("terraform", "apply", "-input=false", "-auto-approve", "plan"),
              TERRAFORM_ENVIRONMENT, false));
      assertThat(cloud.resourceGroups()).containsEntry(RESOURCE_GROUP, "East US");
      assertThat(cloud.failures()).isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("uploads the main and the event stream consumer packages, then points the Web PubSub hub at the function app")
    void callsAzure() {
      String keys = SITES + FUNCTION_APP + "/host/default/listkeys?api-version=2023-12-01";
      assertThat(cloud.calls()).containsExactly(
          "POST " + SITES + FUNCTION_APP + "/config/publishingcredentials/list?api-version=2023-12-01",
          "POST https://" + FUNCTION_APP + ".scm.azurewebsites.net/api/zipDeploy?isAsync=true",
          "POST " + SITES + STREAM_FUNCTION_APP + "/config/publishingcredentials/list?api-version=2023-12-01",
          "POST https://" + STREAM_FUNCTION_APP + ".scm.azurewebsites.net/api/zipDeploy?isAsync=true",
          "POST https://" + FUNCTION_APP + ".azurewebsites.net/api/graphql",
          "POST " + keys,
          "PUT " + HUB);
      // One Entra ID token (client credentials for Resource Manager), reused by every call
      assertThat(cloud.requests()).filteredOn(request -> request.uri().getHost().equals("login.microsoftonline.com"))
          .hasSize(1);
      // updateWebPubSubHub: the host initialisation delay, then the key is found at the first attempt
      assertThat(cloud.sleeps()).containsExactly(30000L);
      assertThat(cloud.failures()).isEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("uploads the synthesized packages with the publishing credentials of each function app")
    void uploadsThePackages() {
      assertThat(cloud.uploads()).extracting(FakeAzureCloud.Upload::functionApp)
          .containsExactly(FUNCTION_APP, STREAM_FUNCTION_APP);
      assertThat(cloud.uploads().get(0).body()).isEqualTo(project.directory().resolve("functionApp.zip"));
      assertThat(cloud.uploads().get(1).body()).isEqualTo(project.directory().resolve("consumerFunctionApp.zip"));
      assertThat(cloud.uploads().get(0).authorization()).isEqualTo("Basic " + Base64.getEncoder().encodeToString(
          ("$" + FUNCTION_APP + ":pw-" + FUNCTION_APP).getBytes(StandardCharsets.UTF_8)));
      assertThat(functions(project.directory().resolve("functionApp.zip")))
          .containsExactlyInAnyOrderElementsOf(TypeScriptAzureApp.mainFunctions());
      assertThat(functions(project.directory().resolve("consumerFunctionApp.zip"))).containsExactly("eventConsumer");
    }

    @Test
    @Order(5)
    @DisplayName("sends the cold start request the TypeScript deploy sends")
    void coldStart() {
      var coldStart = cloud.requests().stream()
          .filter(request -> request.uri().toString().equals("https://" + FUNCTION_APP + ".azurewebsites.net/api/graphql"))
          .findFirst().orElseThrow();
      assertThat(coldStart.bodyText()).isEqualTo("{\"query\":\"{ __typename }\"}");
      assertThat(coldStart.header("Content-Type")).contains("application/json");
      assertThat(coldStart.timeout()).hasSeconds(10);
    }

    @Test
    @Order(6)
    @DisplayName("replaces the PLACEHOLDER key of the hub's event handler with the webpubsub_extension key")
    void updatesTheHub() {
      assertThat(cloud.hubs()).containsOnlyKeys(WEB_PUB_SUB + "/booster");
      assertThat(cloud.hubs().get(WEB_PUB_SUB + "/booster")).isEqualTo(Map.of("properties", Map.of(
          "eventHandlers", List.of(Map.of(
              "urlTemplate", "https://" + FUNCTION_APP + ".azurewebsites.net/runtime/webhooks/webpubsub?code="
                  + "extension-key-" + FUNCTION_APP,
              "userEventPattern", "*",
              "systemEvents", List.of("connect", "disconnected"))))));
    }

    @Test
    @Order(7)
    @DisplayName("deploys a stack whose main function app boots this application")
    void bootsThisApplication() throws IOException {
      Map<String, Object> stack = BoosterJson.parseObject(
          Files.readString(stackDirectory.resolve("cdk.tf.json"), StandardCharsets.UTF_8));
      String settings = (String) at(stack, "resource", "azurerm_windows_function_app",
          TypeScriptAzureApp.APP_PREFIX + "func", "app_settings");
      assertThat(settings).contains("\"BOOSTER_APP_CLASS\" = \"" + TypeScriptAzureApp.APPLICATION_CLASS + "\"",
          "\"BOOSTER_ENV\" = \"azure\"", "\"BOOSTER_APP_SUFFIX\" = \"contract\"");
      assertThat(at(stack, "resource", "azurerm_windows_function_app", TypeScriptAzureApp.APP_PREFIX + "func",
          "zip_deploy_file")).isEqualTo(project.directory().resolve("baseWebPubSubBinding.zip").toString());
    }
  }

  @Nested
  @Order(2)
  @DisplayName("After a failed deployment")
  class AfterAFailedDeployment {

    @Test
    @DisplayName("reports the Terraform error and uploads nothing")
    void reportsTheTerraformError() {
      FakeAzureCloud failing = new FakeAzureCloud(TENANT, SUBSCRIPTION)
          .failing("terraform apply", "Error: creating Resource Group: unexpected status 403");
      try (CliBuiltProject project = CliBuiltProject.create("failed-deploy", TypeScriptAzureApp.ASSETS)) {
        assertThatThrownBy(() -> infrastructure(project, failing).deploy(config))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Deployment of application " + APP_NAME + " failed. Check Terraform logs. \n"
                + "Error: creating Resource Group: unexpected status 403");
      }
      assertThat(failing.commands()).extracting(command -> command.arguments().get(1))
          .containsExactly("init", "plan", "apply");
      assertThat(failing.requests()).isEmpty();
    }
  }

  @Nested
  @Order(3)
  @DisplayName("After nuke")
  class AfterNuke {

    private List<String> callsBeforeNuke;
    private int commandsBeforeNuke;

    /** {@code azure/nuke/setup.ts}: sandbox {@code nuke} with two assets, configuration check, nuke, sandbox removed. */
    @BeforeAll
    void nuke() {
      callsBeforeNuke = cloud.calls();
      commandsBeforeNuke = cloud.commands().size();
      CliBuiltProject project = CliBuiltProject.create("nuke", List.of("assets", "assetFile.txt"));
      try {
        AzureTestHelper.ensureAzureConfiguration(AZURE);
        infrastructure(project, cloud).nuke(config);
      } finally {
        project.close();
      }
    }

    @Nested
    @Order(1)
    @DisplayName("the resource group")
    class TheResourceGroup {

      @Test
      @DisplayName("is deleted successfully")
      void isDeletedSuccessfully() {
        assertThatThrownBy(() -> new AzCliHelper(cloud)
            .getResourceGroup(AppHelper.applicationName(), AppHelper.checkAndGetCurrentEnv()))
            .hasMessageContaining("ResourceGroupNotFound");
      }
    }

    @Test
    @Order(2)
    @DisplayName("deletes the resource group through Resource Manager, waiting for the operation, without Terraform")
    void deletesTheResourceGroup() {
      List<String> nukeCalls = cloud.calls().subList(callsBeforeNuke.size(), cloud.calls().size());
      assertThat(nukeCalls).containsExactly(
          "DELETE " + SUBSCRIPTION_URL + "/resourcegroups/" + RESOURCE_GROUP + "?api-version=2021-04-01",
          "GET " + SUBSCRIPTION_URL + "/operationresults/delete-1?api-version=2021-04-01");
      // the Retry-After of the operation; the only command after the nuke is the az check of the test
      assertThat(cloud.sleeps()).endsWith(15000L);
      assertThat(cloud.commands().subList(commandsBeforeNuke, cloud.commands().size()))
          .extracting(Command::commandLine).allMatch(line -> line.startsWith("az group show"));
      assertThat(cloud.resourceGroups()).isEmpty();
      assertThat(cloud.failures()).isEmpty();
    }
  }

  // ---------------------------------------------------------------------------------------------------------------

  private static List<String> functions(Path zip) {
    try (ZipFile file = new ZipFile(zip.toFile())) {
      return file.stream().map(ZipEntry::getName).filter(name -> name.endsWith("/function.json"))
          .map(name -> name.substring(0, name.indexOf('/'))).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Object at(Map<String, Object> root, String... path) {
    Object current = root;
    for (String key : path) {
      current = ((Map<?, ?>) current).get(key);
    }
    return current;
  }
}
