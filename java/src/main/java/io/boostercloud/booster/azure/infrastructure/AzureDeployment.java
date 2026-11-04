package io.boostercloud.booster.azure.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.Json;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** Resource-group scoped deployment/update and explicit, ownership-checked teardown. */
public final class AzureDeployment {
  private static final String GROUP_API = "?api-version=2022-09-01";
  private static final String DEPLOYMENT_API = "?api-version=2022-09-01";
  private final AzureArmClient arm;
  public AzureDeployment(AzureArmClient arm) { this.arm = Objects.requireNonNull(arm); }
  public JsonNode deploy(AzureDeploymentConfig config, Duration timeout) throws IOException, InterruptedException {
    String group = config.resourceGroupPath();
    var existing = arm.request("GET", group + GROUP_API, null);
    if (existing.status() != 404) requireOwned(config, AzureArmClient.requireSuccess(existing));
    else AzureArmClient.requireSuccess(arm.request("PUT", group + GROUP_API,
        Json.MAPPER.valueToTree(Map.of("location", config.location(), "tags", Map.of(AzureInfrastructure.OWNER_TAG, config.application())))));
    String path = group + "/providers/Microsoft.Resources/deployments/" + config.deploymentName() + DEPLOYMENT_API;
    var request = Json.MAPPER.createObjectNode();
    var properties = request.putObject("properties"); properties.put("mode", "Incremental");
    properties.set("template", AzureInfrastructure.template(config));
    AzureArmClient.requireSuccess(arm.request("PUT", path, request));
    return arm.await(path, timeout, false).path("properties").path("outputs");
  }
  public JsonNode status(AzureDeploymentConfig config) throws IOException, InterruptedException {
    return AzureArmClient.requireSuccess(arm.request("GET", config.resourceGroupPath()
        + "/providers/Microsoft.Resources/deployments/" + config.deploymentName() + DEPLOYMENT_API, null));
  }
  public void nuke(AzureDeploymentConfig config, String confirmedResourceGroup, Duration timeout) throws IOException, InterruptedException {
    if (!config.resourceGroup().equals(confirmedResourceGroup))
      throw new IllegalArgumentException("Teardown requires --confirm-resource-group " + config.resourceGroup());
    String path = config.resourceGroupPath() + GROUP_API;
    var current = arm.request("GET", path, null);
    if (current.status() == 404) return;
    requireOwned(config, AzureArmClient.requireSuccess(current));
    AzureArmClient.requireSuccess(arm.request("DELETE", path, null));
    arm.await(path, timeout, true);
  }
  private static void requireOwned(AzureDeploymentConfig config, JsonNode group) {
    if (!config.application().equals(group.path("tags").path(AzureInfrastructure.OWNER_TAG).asText()))
      throw new IllegalStateException("Resource group is not marked as owned by this Booster application: " + config.resourceGroup());
  }
}
