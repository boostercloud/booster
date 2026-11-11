package com.boostercloud.framework.provider.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import java.util.List;

/**
 * Port of {@code synth/terraform-web-pubsub-hub.ts}: the {@code azurerm_web_pubsub_hub} with a placeholder event
 * handler key. The real {@code webpubsub_extension} key only exists once the function package is deployed, so the
 * deploy flow updates the hub afterwards and Terraform ignores later changes of {@code event_handler}.
 */
public final class TerraformWebPubsubHub {

  private TerraformWebPubsubHub() {}

  /**
   * Construct id {@code toTerraformName(appPrefix, 'wpsh')}, name {@code booster}, anonymous connections, event
   * handler {@code https://<function app>.azurewebsites.net/runtime/webhooks/webpubsub?code=PLACEHOLDER} for every user
   * event and the {@code connect}/{@code disconnected} system events.
   *
   * @param stack the stack bag
   * @return the resource
   * @throws IllegalStateException {@code Undefined webPubSub resource} / {@code Undefined functionApp resource}
   */
  public static TerraformResource build(ApplicationSynthStack stack) {
    if (stack.webPubSub == null) {
      throw new IllegalStateException("Undefined webPubSub resource");
    }
    if (stack.functionApp == null) {
      throw new IllegalStateException("Undefined functionApp resource");
    }
    String id = Utils.toTerraformName(stack.appPrefix, "wpsh");
    return new TerraformResource(stack.terraformStack, id, "azurerm_web_pubsub_hub")
        .setAttributes(Attributes.of(
            "name", stack.webPubSubHubName,
            "web_pubsub_id", stack.webPubSub.ref("id"),
            "event_handler", List.of(Attributes.of(
                // Placeholder URL - will be updated after deployZip completes
                "url_template", "https://" + stack.functionApp.ref("name")
                    + ".azurewebsites.net/runtime/webhooks/webpubsub?code=PLACEHOLDER",
                "system_events", List.of("connect", "disconnected"),
                "user_event_pattern", "*")),
            "anonymous_connections_enabled", true))
        .dependsOn(stack.functionApp, stack.webPubSub)
        .provider(stack.azureProvider)
        // Ignore changes to event_handler since it will be managed post-deployment
        .ignoreChanges("event_handler");
  }
}
