package com.boostercloud.framework.provider.azure.infrastructure.types;

import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformDataSource;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import java.util.List;
import java.util.Objects;

/**
 * The mutable "stack bag" of {@code types/application-synth-stack.ts}: the names plus every resource the synthesis has
 * created so far. Builders read earlier resources from it (and fail with {@code Undefined <x> resource} when one is
 * missing); rockets receive it by reference in {@code mountStack} and add resources to {@link #terraformStack} and/or
 * modify the bag.
 *
 * <p>Resources are generic {@link TerraformResource}s (the TypeScript fields have the typed {@code @cdktn} classes):
 * read attributes through references ({@code functionApp.ref("name")}) and patch them with
 * {@link TerraformResource#setAttribute} / {@link TerraformResource#addOverride}. Fields that TypeScript declares but
 * never sets ({@code dataFunctionAppHostKeys}, {@code apiManagementApi}, {@code webPubSubHubUpdater},
 * {@code functionDefinitions}, {@code consumerFunctionDefinitions}, {@code rocketStack}) are kept for rockets.
 */
public class ApplicationSynthStack extends StackNames {

  /** {@code azurerm_resource_group}. */
  public final TerraformResource resourceGroup;
  /** {@code azurerm_service_plan} of the main function app. */
  public TerraformResource applicationServicePlan;
  /** {@code azurerm_storage_account} of the main function app. */
  public TerraformResource storageAccount;
  /** {@code azurerm_windows_function_app}, the main function app. */
  public TerraformResource functionApp;
  /** {@code azurerm_service_plan} of the event stream consumer (event streaming only). */
  public TerraformResource eventConsumerServicePlan;
  /** {@code azurerm_storage_account} of the event stream consumer (event streaming only). */
  public TerraformResource eventConsumerStorageAccount;
  /** {@code azurerm_windows_function_app} of the event stream consumer (event streaming only). */
  public TerraformResource eventConsumerFunctionApp;
  /** Never set by the infrastructure (kept for rockets). */
  public TerraformDataSource dataFunctionAppHostKeys;
  /** {@code azurerm_application_gateway}. */
  public TerraformResource appGateway;
  /** {@code azurerm_network_security_group}. */
  public TerraformResource networkSecurityGroup;
  /** {@code azurerm_subnet}. */
  public TerraformResource subnet;
  /** {@code azurerm_subnet_network_security_group_association}. */
  public TerraformResource subnetNetworkSecurityGroupAssociation;
  /** {@code azurerm_virtual_network}. */
  public TerraformResource virtualNetwork;
  /** {@code azurerm_public_ip}. */
  public TerraformResource publicIP;
  /** {@code data.azurerm_public_ip}. */
  public TerraformDataSource publicIPData;
  /** Never set by the infrastructure (kept for rockets). */
  public TerraformResource apiManagementApi;
  /** {@code azurerm_cosmosdb_account}. */
  public TerraformResource cosmosdbDatabase;
  /** {@code azurerm_cosmosdb_sql_database}. */
  public TerraformResource cosmosdbSqlDatabase;
  /** The {@code azurerm_cosmosdb_sql_container}s returned by the containers builder (plus the dedup container). */
  public List<TerraformResource> containers;
  /** {@code azurerm_web_pubsub} (subscriptions only). */
  public TerraformResource webPubSub;
  /** {@code azurerm_web_pubsub_hub} (subscriptions only). */
  public TerraformResource webPubSubHub;
  /** Never set by the infrastructure (kept for rockets). */
  public TerraformResource webPubSubHubUpdater;
  /** Never set by the infrastructure (kept for rockets). */
  public List<FunctionDefinition> functionDefinitions;
  /** Never set by the infrastructure (kept for rockets). */
  public List<FunctionDefinition> consumerFunctionDefinitions;
  /** {@code azurerm_eventhub_namespace} (event streaming only). */
  public TerraformResource eventHubNamespace;
  /** {@code azurerm_eventhub} (event streaming only). */
  public TerraformResource eventHub;
  /** Never set by the infrastructure (free for rockets). */
  public List<TerraformResource> rocketStack;

  /**
   * {@code { ...stackNames, resourceGroup }}.
   *
   * @param names the stack names
   * @param resourceGroup the resource group
   */
  public ApplicationSynthStack(StackNames names, TerraformResource resourceGroup) {
    super(names);
    this.resourceGroup = Objects.requireNonNull(resourceGroup, "resourceGroup");
  }
}
