package com.boostercloud.framework.provider.azure.infrastructure.functions;

import com.boostercloud.framework.provider.azure.infrastructure.types.Binding;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionConfig;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import java.util.List;

/**
 * Port of {@code functions/websocket-connect-function.ts}: the legacy definition of the Web PubSub
 * {@code system/connect} function (unused). The script file and entry point are hard-coded, as in TypeScript.
 */
public final class WebsocketConnectFunction {

  /**
   * @param config the configuration (unused, as in TypeScript)
   */
  public WebsocketConnectFunction(BoosterConfig config) {}

  /**
   * @return {@code connect}: {@code webPubSubTrigger} {@code data} on hub {@code booster}, {@code ../dist/index.js} /
   *     {@code boosterServeGraphQL}
   */
  public FunctionDefinition getFunctionDefinition() {
    return new FunctionDefinition("connect", new FunctionConfig(
        List.of(Binding.of(LegacyFunctions.ordered(
            "type", "webPubSubTrigger",
            "direction", "in",
            "name", "data",
            "hub", "booster",
            "eventType", "system",
            "eventName", "connect"))),
        "../dist/index.js",
        "boosterServeGraphQL"));
  }
}
