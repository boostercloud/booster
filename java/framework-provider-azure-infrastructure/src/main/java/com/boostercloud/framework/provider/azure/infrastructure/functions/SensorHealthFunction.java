package com.boostercloud.framework.provider.azure.infrastructure.functions;

import com.boostercloud.framework.provider.azure.infrastructure.types.Binding;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionConfig;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import java.util.List;

/** Port of {@code functions/sensor-health-function.ts}: the legacy definition of the health HTTP function (unused). */
public final class SensorHealthFunction {

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public SensorHealthFunction(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return {@code sensor-health}: anonymous GET {@code httpTrigger} {@code rawRequest} on
   *     {@code sensor/health/{*url}} and {@code $return}, {@code boosterHealth}
   */
  public FunctionDefinition getFunctionDefinition() {
    return new FunctionDefinition("sensor-health", new FunctionConfig(
        List.of(
            Binding.of(LegacyFunctions.ordered(
                "authLevel", "anonymous", "type", "httpTrigger", "direction", "in", "name", "rawRequest",
                "methods", List.of("get"), "route", "sensor/health/{*url}")),
            Binding.of(LegacyFunctions.ordered("type", "http", "direction", "out", "name", "$return"))),
        config.functionRelativePath,
        LegacyFunctions.entryPoint(config.sensorHealthHandler)));
  }
}
