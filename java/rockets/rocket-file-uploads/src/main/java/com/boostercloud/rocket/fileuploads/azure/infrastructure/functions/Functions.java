package com.boostercloud.rocket.fileuploads.azure.infrastructure.functions;

import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketUtils;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionAppV4Definition;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.fileuploads.azure.infrastructure.Helper;
import com.boostercloud.rocket.fileuploads.types.RocketFilesConfiguration;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of {@code functions/functions.ts}.
 */
public final class Functions {

  private Functions() {}

  /**
   * {@code Functions.mountFunctionsV4(configuration, config, applicationSynthStack, utils)}: one package, for the
   * rocket's function app {@code <rg>filesfunc}, with one blob trigger function per user configuration (TypeScript
   * returns the {@code functions.js} registering them; Java returns their {@code function.json} definitions) and the
   * default {@code host.json}.
   *
   * @param configuration the rocket parameters
   * @param config the application configuration
   * @param applicationSynthStack the stack bag
   * @param utils ignored
   * @return the package definition
   * @throws IllegalArgumentException {@code Invalid containerName: "<value>". Must match /^[a-z0-9_-]+$/.} (or
   *     {@code storageName})
   */
  public static List<FunctionAppV4Definition> mountFunctionsV4(RocketFilesConfiguration configuration,
      BoosterConfig config, ApplicationSynthStack applicationSynthStack, RocketUtils utils) {
    String functionAppName = Helper.getFunctionAppName(applicationSynthStack);
    List<FunctionDefinition> functions = new ArrayList<>();
    for (RocketFilesUserConfiguration userConfiguration : configuration.userConfiguration()) {
      functions.add(RocketFilesFileUploadedFunction.generateFunctionDefinition(config,
          userConfiguration.containerName(), userConfiguration.storageName()));
    }
    return List.of(FunctionAppV4Definition.of(functionAppName, functions));
  }

  /**
   * {@code Functions.getFunctionAppName(configuration, applicationSynthStack)}.
   *
   * @param configuration ignored
   * @param applicationSynthStack the stack bag
   * @return {@code <resourceGroupName>filesfunc}
   */
  public static String getFunctionAppName(RocketFilesConfiguration configuration,
      ApplicationSynthStack applicationSynthStack) {
    return Helper.getFunctionAppName(applicationSynthStack);
  }
}
