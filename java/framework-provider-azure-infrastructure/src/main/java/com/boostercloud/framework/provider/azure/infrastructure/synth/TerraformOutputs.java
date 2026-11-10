package com.boostercloud.framework.provider.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformOutput;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.types.EnvironmentVariables;

/**
 * Port of {@code synth/terraform-outputs.ts}: the stack outputs. As in TypeScript they are created inside the azurerm
 * provider construct (so their logical ids are {@code azureFeature<id>_<hash>}) and {@code websocketURL} uses the event
 * hub name instead of the Web PubSub hub name (both kept on purpose).
 */
public final class TerraformOutputs {

  private TerraformOutputs() {}

  /**
   * {@code httpURL}, {@code graphqlURL}, {@code sensorHealthURL} and, with Web PubSub, {@code websocketURL}; the base
   * URL is {@code http://<public IP fqdn>/<BOOSTER_ENV or 'azure'>}.
   *
   * @param stack the stack bag
   */
  public static void build(ApplicationSynthStack stack) {
    String environment = EnvironmentVariables.get("BOOSTER_ENV").orElse("azure");
    String fdqn = stack.publicIPData != null ? stack.publicIPData.ref("fqdn") : "localhost";
    String baseUrl = "http://" + fdqn + "/" + environment;

    new TerraformOutput(stack.azureProvider, "httpURL", baseUrl, "The base URL for all the auth endpoints");
    new TerraformOutput(stack.azureProvider, "graphqlURL", baseUrl + "/graphql",
        "The base URL for sending GraphQL mutations and queries");
    new TerraformOutput(stack.azureProvider, "sensorHealthURL", baseUrl + "/sensor/health",
        "The base URL for getting health information");
    if (stack.webPubSub != null) {
      new TerraformOutput(stack.azureProvider, "websocketURL",
          "wss://" + stack.webPubSub.ref("hostname") + "/client/hubs/" + stack.eventHubName,
          "The URL for the websocket communication. Used for subscriptions");
    }
  }
}
