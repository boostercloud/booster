package com.boostercloud.framework.provider.azure.infrastructure.types;

import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformProvider;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformStack;
import java.util.Objects;

/**
 * The names and handles every resource builder needs ({@code StackNames} of {@code types/application-synth-stack.ts}).
 */
public class StackNames {

  /** {@code buildAppPrefix(config)}: the prefix of the Terraform construct ids. */
  public final String appPrefix;
  /** The stack resources are added to. */
  public final TerraformStack terraformStack;
  /** The azurerm provider (construct id {@code azureFeature}). */
  public final TerraformProvider azureProvider;
  /** {@code createResourceGroupName(appName, environmentName)}. */
  public final String resourceGroupName;
  /** The main function app ({@code <rg>fpr}). */
  public final String functionAppName;
  /** The event stream consumer function app ({@code <rg>fcs}). */
  public final String streamFunctionAppName;
  /** The public IP domain name label ({@code <rg>apis}). */
  public final String domainNameLabel;
  /** {@code config.resourceNames.streamTopic}. */
  public final String eventHubName;
  /** {@code 'booster'}. */
  public final String webPubSubHubName;
  /**
   * Java only: the application class the function apps boot ({@code BOOSTER_APP_CLASS} app setting); rockets that
   * create function apps running the application must set it too.
   */
  public final String boosterAppClass;

  /**
   * @param appPrefix the app prefix
   * @param terraformStack the stack
   * @param azureProvider the azurerm provider
   * @param resourceGroupName the resource group name
   * @param functionAppName the main function app name
   * @param streamFunctionAppName the consumer function app name
   * @param domainNameLabel the domain name label
   * @param eventHubName the event hub name
   * @param webPubSubHubName the Web PubSub hub name
   * @param boosterAppClass the application class
   */
  public StackNames(
      String appPrefix,
      TerraformStack terraformStack,
      TerraformProvider azureProvider,
      String resourceGroupName,
      String functionAppName,
      String streamFunctionAppName,
      String domainNameLabel,
      String eventHubName,
      String webPubSubHubName,
      String boosterAppClass) {
    this.appPrefix = Objects.requireNonNull(appPrefix, "appPrefix");
    this.terraformStack = Objects.requireNonNull(terraformStack, "terraformStack");
    this.azureProvider = Objects.requireNonNull(azureProvider, "azureProvider");
    this.resourceGroupName = Objects.requireNonNull(resourceGroupName, "resourceGroupName");
    this.functionAppName = Objects.requireNonNull(functionAppName, "functionAppName");
    this.streamFunctionAppName = Objects.requireNonNull(streamFunctionAppName, "streamFunctionAppName");
    this.domainNameLabel = Objects.requireNonNull(domainNameLabel, "domainNameLabel");
    this.eventHubName = Objects.requireNonNull(eventHubName, "eventHubName");
    this.webPubSubHubName = Objects.requireNonNull(webPubSubHubName, "webPubSubHubName");
    this.boosterAppClass = boosterAppClass == null ? "" : boosterAppClass;
  }

  /**
   * @param names the names to copy
   */
  protected StackNames(StackNames names) {
    this(names.appPrefix, names.terraformStack, names.azureProvider, names.resourceGroupName, names.functionAppName,
        names.streamFunctionAppName, names.domainNameLabel, names.eventHubName, names.webPubSubHubName,
        names.boosterAppClass);
  }
}
