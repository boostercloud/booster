package com.boostercloud.framework.provider.azure.infrastructure.azure;

import static com.boostercloud.framework.provider.azure.infrastructure.azure.ArmClient.segment;

import java.util.Map;
import java.util.Objects;

/** The {@code @azure/arm-webpubsub} ({@code WebPubSubManagementClient.webPubSubHubs}) operation the deploy flow uses. */
public final class WebPubSubManagementClient {

  /** The {@code Microsoft.SignalRService} API version. */
  public static final String API_VERSION = "2023-02-01";

  private final ArmClient arm;

  /**
   * @param arm the Resource Manager client
   */
  public WebPubSubManagementClient(ArmClient arm) {
    this.arm = Objects.requireNonNull(arm, "arm");
  }

  /**
   * {@code webPubSubHubs.beginCreateOrUpdateAndWait(hubName, resourceGroupName, resourceName, parameters)}:
   * {@code PUT /resourceGroups/<rg>/providers/Microsoft.SignalRService/webPubSub/<resource>/hubs/<hub>}.
   *
   * @param hubName the hub
   * @param resourceGroupName the resource group
   * @param resourceName the Web PubSub service
   * @param parameters the hub ({@code { properties: { eventHandlers: [...] } }})
   * @return the hub as returned by Azure
   */
  public Map<String, Object> createOrUpdateHub(
      String hubName, String resourceGroupName, String resourceName, Map<String, Object> parameters) {
    return arm.beginAndWait("PUT", "/resourceGroups/" + segment(resourceGroupName)
        + "/providers/Microsoft.SignalRService/webPubSub/" + segment(resourceName) + "/hubs/" + segment(hubName),
        API_VERSION, parameters);
  }
}
