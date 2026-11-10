package com.boostercloud.framework.provider.azure.infrastructure.azure;

import static com.boostercloud.framework.provider.azure.infrastructure.azure.ArmClient.segment;

import java.util.Objects;

/** The {@code @azure/arm-resources} ({@code ResourceManagementClient.resourceGroups}) operation the nuke flow uses. */
public final class ResourceManagementClient {

  /** The {@code Microsoft.Resources} API version. */
  public static final String API_VERSION = "2021-04-01";

  private final ArmClient arm;

  /**
   * @param arm the Resource Manager client
   */
  public ResourceManagementClient(ArmClient arm) {
    this.arm = Objects.requireNonNull(arm, "arm");
  }

  /**
   * {@code resourceGroups.beginDeleteAndWait(resourceGroupName)}: {@code DELETE /resourcegroups/<name>}, waiting until
   * every resource of the group is deleted.
   *
   * @param resourceGroupName the resource group
   * @throws ArmError e.g. {@code ResourceGroupNotFound}
   */
  public void deleteResourceGroup(String resourceGroupName) {
    arm.beginAndWait("DELETE", "/resourcegroups/" + segment(resourceGroupName), API_VERSION, null);
  }
}
