package com.boostercloud.framework.provider.azure.infrastructure.functions;

import com.boostercloud.framework.provider.azure.infrastructure.types.Binding;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionConfig;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import java.util.List;

/**
 * Port of {@code functions/websocket-messages-function.ts}: the legacy definition of the Web PubSub
 * {@code user/message} function (unused). The script file and entry point are hard-coded, as in TypeScript.
 */
public final class WebsocketMessagesFunction {

  /**
   * @param config the configuration (unused, as in TypeScript)
   */
  public WebsocketMessagesFunction(BoosterConfig config) {}

  /**
   * @return {@code messages}: {@code webPubSubTrigger} {@code data} on hub {@code booster}, {@code ../dist/index.js} /
   *     {@code boosterServeGraphQL}
   */
  public FunctionDefinition getFunctionDefinition() {
    return new FunctionDefinition("messages", new FunctionConfig(
        List.of(Binding.of(LegacyFunctions.ordered(
            "type", "webPubSubTrigger",
            "direction", "in",
            "name", "data",
            "hub", "booster",
            "eventType", "user",
            "eventName", "message"))),
        "../dist/index.js",
        "boosterServeGraphQL"));
  }
}
