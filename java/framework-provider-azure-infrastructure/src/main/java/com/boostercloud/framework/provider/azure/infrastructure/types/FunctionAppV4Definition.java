package com.boostercloud.framework.provider.azure.infrastructure.types;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The code of one rocket function app returned by {@code mountFunctionsV4} ({@code FunctionAppV4Definition}).
 * {@code FunctionAppV4Definitions} is a {@code List} of these.
 *
 * <p>TypeScript rockets return the text of a {@code functions.js} that registers their functions with the Node v4
 * programming model. A Java function app registers functions with one {@code function.json} per function pointing at
 * methods of the application jar, so Java rockets return those definitions (typically bound to
 * {@code BoosterAzureFunctions.rocketHttp} / {@code rocketBlob}); the package is built like the main function app's
 * (application jar, {@code lib/}, one folder per function, {@code host.json}).
 *
 * @param functionAppName the function app the package is deployed to
 * @param functionsDefinitions the functions (the Java counterpart of {@code functionsCode})
 * @param hostJsonPath a custom {@code host.json}, or {@code null} for the default one
 */
public record FunctionAppV4Definition(
    String functionAppName, List<FunctionDefinition> functionsDefinitions, Path hostJsonPath) {

  /** Canonical constructor: copies the definitions. */
  public FunctionAppV4Definition {
    Objects.requireNonNull(functionAppName, "functionAppName");
    functionsDefinitions = List.copyOf(functionsDefinitions);
  }

  /**
   * @param functionAppName the function app
   * @param functionsDefinitions the functions
   * @return a definition with the default {@code host.json}
   */
  public static FunctionAppV4Definition of(String functionAppName, List<FunctionDefinition> functionsDefinitions) {
    return new FunctionAppV4Definition(functionAppName, functionsDefinitions, null);
  }
}
