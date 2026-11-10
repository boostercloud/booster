package com.boostercloud.framework.provider.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.infrastructure.Constants;
import com.boostercloud.framework.provider.azure.infrastructure.helper.Params;
import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import com.boostercloud.framework.provider.azure.infrastructure.synth.gateway.TerraformApplicationGateway;
import com.boostercloud.framework.provider.azure.infrastructure.synth.gateway.TerraformNetworkSecurityGroup;
import com.boostercloud.framework.provider.azure.infrastructure.synth.gateway.TerraformPublicIp;
import com.boostercloud.framework.provider.azure.infrastructure.synth.gateway.TerraformPublicIpData;
import com.boostercloud.framework.provider.azure.infrastructure.synth.gateway.TerraformSubnet;
import com.boostercloud.framework.provider.azure.infrastructure.synth.gateway.TerraformSubnetSecurity;
import com.boostercloud.framework.provider.azure.infrastructure.synth.gateway.TerraformVirtualNetwork;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformProvider;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformStack;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.provider.azure.infrastructure.types.StackNames;
import com.boostercloud.framework.types.BoosterConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Port of {@code synth/application-synth.ts}: creates the azurerm provider, computes the stack names and instantiates
 * every Terraform resource of the application, in the TypeScript order (each step reads the resources created by the
 * previous ones):
 *
 * <ol>
 *   <li>resource group;
 *   <li>gateway network: security group, virtual network, subnet, subnet/security group association, public IP,
 *       application gateway, public IP data source (always created);
 *   <li>Cosmos DB account, SQL database and containers;
 *   <li>with event streaming: Event Hub namespace and hub, consumer service plan, storage account and function app,
 *       dedup container;
 *   <li>with subscriptions: Web PubSub;
 *   <li>main service plan ({@code Y1}, or {@code B1} with {@code BOOSTER_AZURE_SERVICE_PLAN_BASIC=true}), storage
 *       account and function app;
 *   <li>with subscriptions: Web PubSub hub;
 *   <li>outputs.
 * </ol>
 *
 * <p>TypeScript re-reads the configuration from {@code <cwd>/dist/index.js}; Java uses the configuration it is given.
 */
public final class ApplicationSynth {

  /** The construct id of the azurerm provider. */
  public static final String PROVIDER_ID = "azureFeature";

  /** The azurerm provider source written to {@code required_providers}. */
  public static final String AZURERM_SOURCE = "azurerm";

  /** The azurerm version constraint ({@code @cdktn/provider-azurerm} 19 is generated for azurerm 5.x). */
  public static final String AZURERM_VERSION_CONSTRAINT = "~> 5.0";

  /** The Web PubSub hub of Booster. */
  public static final String WEB_PUB_SUB_HUB_NAME = "booster";

  /** {@code CONSUMPTION_PLAN}. */
  public static final Plan CONSUMPTION_PLAN = new Plan("Y1", 1);

  /** {@code BASIC_PLAN}. */
  public static final Plan BASIC_PLAN = new Plan("B1", 2);

  /**
   * A service plan size.
   *
   * @param skuName the SKU
   * @param workerCount the number of workers
   */
  public record Plan(String skuName, int workerCount) {}

  private final BoosterConfig config;
  private final StackNames stackNames;

  /**
   * Creates the azurerm provider ({@code features {}}, {@code subscription_id = AZURE_SUBSCRIPTION_ID},
   * {@code resource_provider_registrations = "legacy"}: azurerm 5.x defaults to {@code none}, which breaks deploys to
   * fresh subscriptions) and computes the names.
   *
   * @param terraformStack the stack
   * @param config the configuration
   * @param configuration the Azure credentials (only the subscription id is used)
   * @param boosterAppClass the application class the function apps boot ({@code BOOSTER_APP_CLASS})
   */
  public ApplicationSynth(
      TerraformStack terraformStack, BoosterConfig config, Params.Configuration configuration, String boosterAppClass) {
    this.config = Objects.requireNonNull(config, "config");
    TerraformProvider azurermProvider =
        new TerraformProvider(terraformStack, PROVIDER_ID, "azurerm", AZURERM_SOURCE, AZURERM_VERSION_CONSTRAINT);
    azurermProvider.setAttribute("features", List.of(Map.of()));
    azurermProvider.setAttribute("subscription_id", configuration.subscriptionId());
    azurermProvider.setAttribute("resource_provider_registrations", "legacy");
    String appPrefix = Utils.buildAppPrefix(config);
    String resourceGroupName = Utils.createResourceGroupName(config.appName, config.environmentName);
    this.stackNames = new StackNames(
        appPrefix,
        terraformStack,
        azurermProvider,
        resourceGroupName,
        Utils.createFunctionResourceGroupName(resourceGroupName),
        Utils.createStreamFunctionResourceGroupName(resourceGroupName),
        Utils.createDomainNameLabel(resourceGroupName),
        config.resourceNames().streamTopic(),
        WEB_PUB_SUB_HUB_NAME,
        boosterAppClass);
  }

  /**
   * @return the stack names
   */
  public StackNames stackNames() {
    return stackNames;
  }

  /**
   * Creates every resource.
   *
   * @param zipFile the WebPubSub bootstrap package the main function app is created with (subscriptions only), or
   *     {@code null}
   * @return the stack bag
   */
  public ApplicationSynthStack synth(String zipFile) {
    TerraformResource resourceGroup = TerraformResourceGroup.build(stackNames);
    ApplicationSynthStack stack = new ApplicationSynthStack(stackNames, resourceGroup);

    stack.networkSecurityGroup = TerraformNetworkSecurityGroup.build(
        stack, Utils.toTerraformName(stackNames.appPrefix, "sg"), stackNames.resourceGroupName + "rjsg");
    stack.virtualNetwork = TerraformVirtualNetwork.build(stack);
    stack.subnet = TerraformSubnet.build(stack);
    stack.subnetNetworkSecurityGroupAssociation = TerraformSubnetSecurity.build(stack);
    stack.publicIP = TerraformPublicIp.build(stack, "pip");
    stack.appGateway = TerraformApplicationGateway.build(stack);
    stack.publicIPData = TerraformPublicIpData.build(stack);

    stack.cosmosdbDatabase = TerraformCosmosdbDatabase.build(stack);
    stack.cosmosdbSqlDatabase = TerraformCosmosdbSqlDatabase.build(stack, config);
    stack.containers = new ArrayList<>(TerraformContainers.build(stack, config));
    buildEventHub(stack);
    buildWebPubSub(stack);
    Plan plan = "true".equals(Constants.basicServicePlan()) ? BASIC_PLAN : CONSUMPTION_PLAN;
    stack.applicationServicePlan = TerraformServicePlan.build(stack, "psp", plan.skuName(), plan.workerCount());
    stack.storageAccount = TerraformStorageAccount.build(stack, "sp");
    stack.functionApp = TerraformFunctionApp.build(
        stack, stack.applicationServicePlan, stack.storageAccount, "func", stack.functionAppName, zipFile, null);
    buildWebPubSubHub(stack);
    TerraformOutputs.build(stack);
    return stack;
  }

  private void buildEventHub(ApplicationSynthStack stack) {
    if (config.eventStreamConfiguration.enabled()) {
      stack.eventHubNamespace = TerraformEventHubNamespace.build(stack);
      stack.eventHub = TerraformEventHub.build(stack, config);
      int instanceCount = config.eventStreamConfiguration.parameters() != null
          ? config.eventStreamConfiguration.parameters().partitionCount()
          : 3;
      stack.eventConsumerServicePlan = TerraformServicePlan.build(stack, "psc", "B1", instanceCount);
      stack.eventConsumerStorageAccount = TerraformStorageAccount.build(stack, "sc");
      stack.eventConsumerFunctionApp = TerraformFunctionApp.build(
          stack,
          stack.eventConsumerServicePlan,
          stack.eventConsumerStorageAccount,
          "fhub",
          stack.streamFunctionAppName,
          null,
          buildDefaultAppSettings(stack, stack.eventConsumerStorageAccount, "fhub"));
      if (stack.containers == null) {
        stack.containers = new ArrayList<>();
      }
      stack.containers.add(TerraformContainers.createDedupEventsContainer(stack, config));
    }
  }

  private void buildWebPubSub(ApplicationSynthStack stack) {
    if (config.enableSubscriptions) {
      stack.webPubSub = TerraformWebPubsub.build(stack);
    }
  }

  private void buildWebPubSubHub(ApplicationSynthStack stack) {
    if (stack.webPubSub != null && stack.functionApp != null && stack.resourceGroup != null) {
      // Create the hub with a placeholder key; the deploy flow sets the real one after the package is uploaded
      stack.webPubSubHub = TerraformWebPubsubHub.build(stack);
    }
  }

  /**
   * {@code buildDefaultAppSettings(stack, storageAccount, suffixName)}.
   *
   * @param stack the stack bag
   * @param storageAccount the host storage account
   * @param suffixName {@code func} or {@code fhub}
   * @return the default app settings ({@link TerraformFunctionAppSettings#build})
   */
  public Map<String, String> buildDefaultAppSettings(
      ApplicationSynthStack stack, TerraformResource storageAccount, String suffixName) {
    return TerraformFunctionAppSettings.build(stack, config, storageAccount, suffixName);
  }
}
