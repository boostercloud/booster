package com.boostercloud.framework.provider.azure.infrastructure;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.common.Retrier;
import com.boostercloud.framework.provider.azure.infrastructure.azure.HttpTransport;
import com.boostercloud.framework.provider.azure.infrastructure.azure.ResourceManagementClient;
import com.boostercloud.framework.provider.azure.infrastructure.azure.WebPubSubManagementClient;
import com.boostercloud.framework.provider.azure.infrastructure.azure.WebSiteManagementClient;
import com.boostercloud.framework.provider.azure.infrastructure.helper.FunctionZip;
import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import com.boostercloud.framework.provider.azure.infrastructure.process.Command;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.InfrastructureRocket;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketBuilder;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.provider.ProviderInfrastructure;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Port of {@code infrastructure/index.ts}: the {@code synth}, {@code deploy} and {@code nuke} flows of the Azure
 * provider (the object {@code Infrastructure(rockets)} returns).
 *
 * <ul>
 *   <li><b>synth</b>: {@link ApplicationBuilder#buildApplication()} only (Terraform JSON and packages; nothing is run).
 *   <li><b>deploy</b>: build; {@code terraform init}, {@code plan} and {@code apply} in the synthesized stack directory
 *       (TypeScript runs {@code npx cdktn-cli deploy --auto-approve --ignore-missing-stack-dependencies}, which does the
 *       same on the same JSON); upload the main package, the consumer package (event streaming) and the rocket
 *       packages through Kudu; with subscriptions, point the Web PubSub hub at the function app with the real
 *       {@code webpubsub_extension} key.
 *   <li><b>nuke</b>: the rockets' {@code unmountStack}, then the deletion of the resource group (Terraform is not
 *       involved and the local state is left as is, as in TypeScript).
 * </ul>
 *
 * There is no {@code start}: Azure applications cannot run locally.
 */
public final class AzureInfrastructure implements ProviderInfrastructure {

  /** The message of {@link #start}: what the {@code boost} CLI reports for a provider without {@code start}. */
  public static final String START_NOT_SUPPORTED =
      "Attempted to perform the 'start' operation with a provider that does not support this feature, please check"
          + " your environment configuration.";

  /** Wait after the first cold start, before polling the host keys. */
  public static final long HOST_INITIALIZATION_DELAY_MS = 30000;
  /** Attempts to read the {@code webpubsub_extension} key. */
  public static final int MAX_KEY_RETRIES = 30;
  /** Wait between attempts. */
  public static final long KEY_RETRY_INTERVAL_MS = 10000;
  /** Timeout of a cold start request. */
  public static final Duration COLD_START_TIMEOUT = Duration.ofSeconds(10);
  /** The Web PubSub system key created by the extension. */
  public static final String WEB_PUB_SUB_EXTENSION_KEY = "webpubsub_extension";
  /** The environment of the Terraform commands (added to the inherited one). */
  public static final Map<String, String> TERRAFORM_ENVIRONMENT = Map.of("TF_IN_AUTOMATION", "true");
  /** The saved plan file, in the stack directory. */
  public static final String PLAN_FILE = "plan";

  private final List<InfrastructureRocket> rockets;
  private final Function<BoosterConfig, InfrastructureContext> contextFactory;

  /**
   * @param rockets the loaded rockets, or {@code null}
   * @param contextFactory creates the context of each operation
   */
  public AzureInfrastructure(
      List<InfrastructureRocket> rockets, Function<BoosterConfig, InfrastructureContext> contextFactory) {
    this.rockets = rockets == null ? null : List.copyOf(rockets);
    this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory");
  }

  /**
   * @return the loaded rockets, or {@code null}
   */
  public List<InfrastructureRocket> rockets() {
    return rockets;
  }

  @Override
  public void synth(BoosterConfig config) {
    new ApplicationBuilder(config, rockets, contextFactory.apply(config)).buildApplication();
  }

  @Override
  public void deploy(BoosterConfig config) {
    InfrastructureContext context = contextFactory.apply(config);
    Logger logger = Loggers.getLogger(config, "index#deployApp");
    logger.info("Deploying app " + config.appName);
    ApplicationBuild applicationBuild = new ApplicationBuilder(config, rockets, context).buildApplication();

    try {
      for (Command command : terraformCommands(applicationBuild.stackDirectory())) {
        context.commandRunner().run(command);
      }
    } catch (RuntimeException error) {
      throw new IllegalStateException("Deployment of application " + config.appName
          + " failed. Check Terraform logs. \n" + error.getMessage(), error);
    }

    FunctionZip functionZip = new FunctionZip(config, context);
    String resourceGroupName = Utils.createResourceGroupName(config.appName, config.environmentName);
    String functionAppName = Utils.createFunctionResourceGroupName(resourceGroupName);
    functionZip.deployZip(functionAppName, resourceGroupName, applicationBuild.zipResource());
    if (config.eventStreamConfiguration.enabled()) {
      String streamFunctionAppName = Utils.createStreamFunctionResourceGroupName(resourceGroupName);
      functionZip.deployZip(streamFunctionAppName, resourceGroupName, applicationBuild.consumerZipResource());
    }
    if (applicationBuild.rocketsZipResources() != null && !applicationBuild.rocketsZipResources().isEmpty()) {
      RocketBuilder rocketBuilder =
          new RocketBuilder(config, applicationBuild.azureStack().applicationStack, rockets, functionZip);
      rocketBuilder.uploadRocketsFiles(applicationBuild.rocketsZipResources());
    }

    // Update Web PubSub hub with the correct function key after functions are deployed
    if (config.enableSubscriptions) {
      String webPubSubName = resourceGroupName + "wps";
      updateWebPubSubHub(config, context, resourceGroupName, functionAppName, webPubSubName, "booster");
    }
  }

  /**
   * The Terraform commands of a deploy, run in the stack directory: {@code init -input=false},
   * {@code plan -input=false -out=plan} and {@code apply -input=false -auto-approve plan}, with
   * {@code TF_IN_AUTOMATION=true}.
   *
   * @param stackDirectory the synthesized stack directory
   * @return the commands
   */
  public static List<Command> terraformCommands(Path stackDirectory) {
    String terraform = Constants.terraformBinary();
    List<Command> commands = new ArrayList<>();
    commands.add(new Command(stackDirectory, List.of(terraform, "init", "-input=false"), TERRAFORM_ENVIRONMENT, false));
    commands.add(new Command(stackDirectory, List.of(terraform, "plan", "-input=false", "-out=" + PLAN_FILE),
        TERRAFORM_ENVIRONMENT, false));
    commands.add(new Command(stackDirectory, List.of(terraform, "apply", "-input=false", "-auto-approve", PLAN_FILE),
        TERRAFORM_ENVIRONMENT, false));
    return commands;
  }

  /**
   * {@code triggerColdStart(functionAppName)}: {@code POST https://<app>.azurewebsites.net/api/graphql} with
   * {@code {"query":"{ __typename }"}} and a 10 s timeout; the response and every error are ignored.
   *
   * @param context the context
   * @param functionAppName the function app
   */
  static void triggerColdStart(InfrastructureContext context, String functionAppName) {
    try {
      Map<String, String> headers = new LinkedHashMap<>();
      headers.put("Content-Type", "application/json");
      context.httpTransport().send(new HttpTransport.Request("POST",
          URI.create("https://" + functionAppName + ".azurewebsites.net/api/graphql"), headers,
          "{\"query\":\"{ __typename }\"}".getBytes(StandardCharsets.UTF_8), COLD_START_TIMEOUT));
    } catch (RuntimeException ignored) {
      // Ignore errors - the function might not be ready yet
    }
  }

  /**
   * {@code updateWebPubSubHub(...)}: once the full package is deployed the Functions runtime discovers the Web PubSub
   * triggers and creates the {@code webpubsub_extension} key; this waits for it (a cold start, 30 s, then up to 30
   * attempts 10 s apart, re-triggering the host every 5 attempts) and points the hub's event handler at
   * {@code https://<app>.azurewebsites.net/runtime/webhooks/webpubsub?code=<key>}.
   *
   * @param config the configuration
   * @param context the context
   * @param resourceGroupName the resource group
   * @param functionAppName the main function app
   * @param webPubSubName the Web PubSub service
   * @param hubName the hub
   * @throws IllegalStateException when the key never appears
   */
  static void updateWebPubSubHub(
      BoosterConfig config,
      InfrastructureContext context,
      String resourceGroupName,
      String functionAppName,
      String webPubSubName,
      String hubName) {
    Logger logger = Loggers.getLogger(config, "index#updateWebPubSubHub");
    logger.info("Updating Web PubSub hub with function key...");

    WebSiteManagementClient websiteClient = context.webSiteManagementClient();
    WebPubSubManagementClient webPubSubClient = context.webPubSubManagementClient();
    Retrier.Sleeper sleeper = context.sleeper();

    logger.info("Triggering function app cold start...");
    triggerColdStart(context, functionAppName);

    logger.info("Waiting for function host to initialize...");
    sleeper.sleep(HOST_INITIALIZATION_DELAY_MS);

    String key = null;
    for (int i = 1; i <= MAX_KEY_RETRIES; i++) {
      try {
        WebSiteManagementClient.HostKeys hostKeys = websiteClient.listHostKeys(resourceGroupName, functionAppName);
        String extensionKey = hostKeys.systemKeys() == null ? null : hostKeys.systemKeys().get(WEB_PUB_SUB_EXTENSION_KEY);
        if (extensionKey != null && !extensionKey.isEmpty()) {
          key = extensionKey;
          logger.info("webpubsub_extension key found after " + i + " attempts");
          break;
        }
      } catch (RuntimeException ignored) {
        // Ignore errors
      }

      // Re-trigger function app every 5 attempts
      if (i % 5 == 0) {
        logger.info("Re-triggering function host...");
        triggerColdStart(context, functionAppName);
      }

      logger.info("Attempt " + i + "/" + MAX_KEY_RETRIES + ": Key not available yet, waiting...");
      sleeper.sleep(KEY_RETRY_INTERVAL_MS);
    }

    if (key == null) {
      String errorMessage = "webpubsub_extension key not found after waiting for function host initialization. "
          + "Web PubSub subscriptions will not work. "
          + "You can manually retrieve the key from: Azure Portal > Function App \"" + functionAppName
          + "\" > App keys > System keys > webpubsub_extension";
      logger.error(errorMessage);
      throw new IllegalStateException(errorMessage);
    }

    logger.info("Updating Web PubSub hub...");
    try {
      webPubSubClient.createOrUpdateHub(hubName, resourceGroupName, webPubSubName, hubParameters(functionAppName, key));
    } catch (RuntimeException error) {
      logger.error("Failed to update Web PubSub hub:", error);
      throw error;
    }
  }

  /**
   * @param functionAppName the main function app
   * @param key the {@code webpubsub_extension} key
   * @return {@code { properties: { eventHandlers: [{ urlTemplate, userEventPattern: '*', systemEvents: ['connect',
   *     'disconnected'] }] } }}
   */
  static Map<String, Object> hubParameters(String functionAppName, String key) {
    Map<String, Object> eventHandler = new LinkedHashMap<>();
    eventHandler.put("urlTemplate",
        "https://" + functionAppName + ".azurewebsites.net/runtime/webhooks/webpubsub?code=" + key);
    eventHandler.put("userEventPattern", "*");
    eventHandler.put("systemEvents", List.of("connect", "disconnected"));
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("eventHandlers", List.of(eventHandler));
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("properties", properties);
    return parameters;
  }

  @Override
  public void nuke(BoosterConfig config) {
    InfrastructureContext context = contextFactory.apply(config);
    ResourceManagementClient resourceManagementClient = context.resourceManagementClient();
    if (rockets != null) {
      rockets.forEach(InfrastructureRocket::unmountStack);
    }
    // By deleting the resource group we are deleting all the resources within it.
    resourceManagementClient.deleteResourceGroup(
        Utils.createResourceGroupName(config.appName, config.environmentName));
  }

  /**
   * Azure applications cannot be started locally.
   *
   * @param config the configuration
   * @param port the port
   * @throws UnsupportedOperationException always ({@link #START_NOT_SUPPORTED})
   */
  @Override
  public void start(BoosterConfig config, int port) {
    throw new UnsupportedOperationException(START_NOT_SUPPORTED);
  }

  /**
   * @param operation an operation
   * @return {@code true} for deploy, nuke and synth; {@code false} for start (TypeScript defines no {@code start})
   */
  @Override
  public boolean supports(Operation operation) {
    return operation != Operation.START;
  }
}
