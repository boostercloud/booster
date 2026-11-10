package com.boostercloud.framework.provider.azure.infrastructure.types;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The functions of one rocket function app returned by {@code mountCode} ({@code FunctionAppFunctionsDefinition}).
 * {@code FunctionAppFunctionsDefinitions} is a {@code List} of these.
 *
 * @param functionAppName the function app the package is deployed to
 * @param functionsDefinitions the functions ({@code function.json} files)
 * @param hostJsonPath a custom {@code host.json}, or {@code null} for the default one
 */
public record FunctionAppFunctionsDefinition(
    String functionAppName, List<FunctionDefinition> functionsDefinitions, Path hostJsonPath) {

  /** Canonical constructor: copies the definitions. */
  public FunctionAppFunctionsDefinition {
    Objects.requireNonNull(functionAppName, "functionAppName");
    functionsDefinitions = List.copyOf(functionsDefinitions);
  }

  /**
   * @param functionAppName the function app
   * @param functionsDefinitions the functions
   * @return a definition with the default {@code host.json}
   */
  public static FunctionAppFunctionsDefinition of(String functionAppName, List<FunctionDefinition> functionsDefinitions) {
    return new FunctionAppFunctionsDefinition(functionAppName, functionsDefinitions, null);
  }
}
