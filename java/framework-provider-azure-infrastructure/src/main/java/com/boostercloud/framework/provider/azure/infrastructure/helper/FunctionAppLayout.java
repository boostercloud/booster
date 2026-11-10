package com.boostercloud.framework.provider.azure.infrastructure.helper;

import com.boostercloud.framework.types.BoosterConfig;

/**
 * The layout of a Java function app package (what the Azure Functions Java worker expects, the counterpart of the
 * Node layout {@code functions.js} + {@code package.json} + {@code dist/} + {@code node_modules/}):
 *
 * <pre>
 * &lt;appName&gt;.jar            the application classes (the {@code scriptFile} of every function)
 * lib/*.jar                the runtime dependencies (Booster, the Azure provider with its entry points, ...)
 * &lt;function&gt;/function.json one folder per function
 * host.json                the extension bundle
 * </pre>
 */
public final class FunctionAppLayout {

  /** The dependencies folder the Java worker adds to the class path. */
  public static final String LIB_DIRECTORY = "lib";

  /** The host configuration file. */
  public static final String HOST_JSON = "host.json";

  /** The file describing one function, inside the function's folder. */
  public static final String FUNCTION_JSON = "function.json";

  private FunctionAppLayout() {}

  /**
   * @param config the configuration
   * @return {@code <appName>.jar}, the jar with the application classes
   */
  public static String appJarName(BoosterConfig config) {
    return config.appName + ".jar";
  }

  /**
   * @param config the configuration
   * @return {@code ../<appName>.jar}, the {@code scriptFile} of every function (relative to its folder)
   */
  public static String scriptFile(BoosterConfig config) {
    return "../" + appJarName(config);
  }
}
