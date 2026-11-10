package com.boostercloud.framework.provider.azure.infrastructure.helper;

import com.boostercloud.framework.common.RunCommandError;
import com.boostercloud.framework.provider.azure.infrastructure.process.Command;
import com.boostercloud.framework.provider.azure.infrastructure.process.CommandRunner;
import com.boostercloud.framework.provider.azure.infrastructure.process.ProcessCommandRunner;
import com.boostercloud.framework.provider.azure.infrastructure.types.Resource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ResourceGroup;
import com.boostercloud.framework.types.BoosterJson;
import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Port of {@code helper/az-cli-helper.ts}: thin wrappers around the {@code az} CLI, used by the test helper. Every
 * command runs in {@code .} without echoing its output; a non-zero exit throws a {@link RunCommandError} whose message
 * is {@code az}'s standard error (e.g. containing {@code ResourceGroupNotFound}), and an output containing
 * {@code could not be found} throws an {@link IllegalStateException} with the TypeScript rejection text.
 */
public final class AzCliHelper {

  private static final String AZ_COMMAND = "az";
  private static final String NOT_FOUND = "could not be found";

  private final CommandRunner commandRunner;

  /** Uses {@link ProcessCommandRunner}. */
  public AzCliHelper() {
    this(ProcessCommandRunner.INSTANCE);
  }

  /**
   * @param commandRunner the command runner
   */
  public AzCliHelper(CommandRunner commandRunner) {
    this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
  }

  /**
   * {@code getResourceGroup(appName, environmentName)}: {@code az group show --name <rg>}, printing
   * {@code Get resource group <rg>} before and {@code Command success <output>} after.
   *
   * @param appName the application name
   * @param environmentName the environment name
   * @return the resource group
   * @throws IllegalStateException {@code Resource Group for application <appName> does not exist}
   */
  public ResourceGroup getResourceGroup(String appName, String environmentName) {
    String resourceGroupName = Utils.createResourceGroupName(appName, environmentName);
    System.out.println("Get resource group " + resourceGroupName);
    String commandOutput = run(AZ_COMMAND + " group show --name " + resourceGroupName);
    if (commandOutput.contains(NOT_FOUND)) {
      throw new IllegalStateException("Resource Group for application " + appName + " does not exist");
    }
    System.out.println("Command success " + commandOutput);
    return BoosterJson.fromJson(commandOutput, ResourceGroup.class);
  }

  /**
   * {@code showResourcesInResourceGroup(resourceGroupName)}: {@code az resource list --resource-group <rg>}.
   *
   * @param resourceGroupName the resource group
   * @return the resources
   * @throws IllegalStateException {@code Resource Group <rg> does not exist}
   */
  public List<Resource> showResourcesInResourceGroup(String resourceGroupName) {
    String commandOutput = run(AZ_COMMAND + " resource list --resource-group " + resourceGroupName);
    if (commandOutput.contains(NOT_FOUND)) {
      throw new IllegalStateException("Resource Group " + resourceGroupName + " does not exist");
    }
    return BoosterJson.fromJson(commandOutput, new TypeReference<List<Resource>>() {});
  }

  /**
   * {@code showResourceInfo(resourceGroupName, resourceName, resourceType)}:
   * {@code az resource show --resource-group <rg> --name <name> --resource-type <type>}.
   *
   * @param resourceGroupName the resource group
   * @param resourceName the resource
   * @param resourceType the resource type
   * @return the resource
   * @throws IllegalStateException {@code Resource <name> does not exist}
   */
  public Resource showResourceInfo(String resourceGroupName, String resourceName, String resourceType) {
    String commandOutput = run(AZ_COMMAND + " resource show --resource-group " + resourceGroupName + " --name "
        + resourceName + " --resource-type " + resourceType);
    if (commandOutput.contains(NOT_FOUND)) {
      throw new IllegalStateException("Resource " + resourceName + " does not exist");
    }
    return BoosterJson.fromJson(commandOutput, Resource.class);
  }

  /**
   * {@code getCosmosConnectionStrings(appName, environmentName)}:
   * {@code az cosmosdb list-connection-strings --resource-group <rg> --name <rg>cdba}.
   *
   * @param appName the application name
   * @param environmentName the environment name
   * @return the parsed output ({@code { connectionStrings: [{ connectionString, description }] }})
   * @throws IllegalStateException {@code Resource <rg> does not exist}
   */
  public Map<String, Object> getCosmosConnectionStrings(String appName, String environmentName) {
    String resourceGroupName = Utils.createResourceGroupName(appName, environmentName);
    String commandOutput = run(AZ_COMMAND + " cosmosdb list-connection-strings --resource-group " + resourceGroupName
        + " --name " + resourceGroupName + "cdba");
    if (commandOutput.contains(NOT_FOUND)) {
      throw new IllegalStateException("Resource " + resourceGroupName + " does not exist");
    }
    return BoosterJson.parseObject(commandOutput);
  }

  private String run(String commandLine) {
    return commandRunner.run(Command.of(Path.of("."), commandLine, true));
  }
}
