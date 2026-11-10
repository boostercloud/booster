package com.boostercloud.framework.provider.azure.infrastructure.rockets;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.common.Promises;
import com.boostercloud.framework.common.PromisesError;
import com.boostercloud.framework.provider.azure.infrastructure.helper.FunctionZip;
import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.InfrastructureRocket.Hook;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionAppFunctionsDefinition;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionAppV4Definition;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.provider.azure.infrastructure.types.ZipResource;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * Port of {@code rockets/rocket-builder.ts}: calls the rocket hooks, builds the rocket packages and uploads them.
 */
public final class RocketBuilder {

  /** {@code /(\W+)/gi}: runs of characters outside {@code [A-Za-z0-9_]}. */
  private static final Pattern NON_WORD = Pattern.compile("(\\W+)");

  /** What TypeScript's {@code `${rocket}`} renders. */
  static final String ROCKET_STRING = "[object Object]";

  private final BoosterConfig config;
  private final ApplicationSynthStack applicationSynthStack;
  private final List<InfrastructureRocket> rockets;
  private final FunctionZip functionZip;

  /**
   * @param config the configuration
   * @param applicationSynthStack the stack bag
   * @param rockets the rockets, or {@code null} (TypeScript {@code undefined})
   * @param functionZip the package builder and uploader
   */
  public RocketBuilder(
      BoosterConfig config,
      ApplicationSynthStack applicationSynthStack,
      List<InfrastructureRocket> rockets,
      FunctionZip functionZip) {
    this.config = config;
    this.applicationSynthStack = applicationSynthStack;
    this.rockets = rockets;
    this.functionZip = functionZip;
  }

  /**
   * {@code synthRocket()}: calls every rocket's {@code mountStack} in order with a fresh {@link RocketUtils}; all of
   * them run even when one fails, then the failures are thrown together. The returned bags are ignored.
   *
   * <p>TypeScript starts every {@code mountStack} promise synchronously in order and awaits them together; since the
   * hooks mutate the shared stack, Java runs them one after the other in that order.
   *
   * @throws PromisesError with the failures (message = their messages joined with {@code ". "})
   */
  public void synthRocket() {
    Logger logger = Loggers.getLogger(config, "RocketBuilder#synthRocket");
    logger.info("Synth rockets...");
    if (rockets == null) {
      return;
    }
    List<Throwable> failures = new ArrayList<>();
    for (InfrastructureRocket rocket : rockets) {
      try {
        rocket.mountStack(config, applicationSynthStack, RocketUtils.buildRocketUtils());
      } catch (RuntimeException e) {
        failures.add(e);
      }
    }
    if (!failures.isEmpty()) {
      throw new PromisesError(failures);
    }
  }

  /**
   * {@code uploadRocketsFiles(rocketsZipResources)}: uploads every package concurrently (fail fast) to its function
   * app in the application's resource group.
   *
   * @param rocketsZipResources the packages, or {@code null} (nothing to do)
   */
  public void uploadRocketsFiles(List<RocketZipResource> rocketsZipResources) {
    if (rocketsZipResources == null) {
      return;
    }
    String resourceGroupName = Utils.createResourceGroupName(config.appName, config.environmentName);
    List<Callable<Void>> uploads = new ArrayList<>();
    for (RocketZipResource rocketZipResource : rocketsZipResources) {
      uploads.add(() -> {
        uploadRocketFile(rocketZipResource, resourceGroupName);
        return null;
      });
    }
    Promises.runAll(uploads);
  }

  private void uploadRocketFile(RocketZipResource rocketZipResource, String resourceGroupName) {
    Logger logger = Loggers.getLogger(config, "RocketBuilder#uploadRocketFile");
    logger.info("Uploading rockets zip file: ", rocketZipResource.functionAppName(), rocketZipResource.zip().fileName());
    functionZip.deployZip(rocketZipResource.functionAppName(), resourceGroupName, rocketZipResource.zip());
    logger.info("Rocket zip files uploaded", rocketZipResource.functionAppName(), rocketZipResource.zip().fileName());
  }

  /**
   * {@code mountRocketsZipResources()}: for each rocket the first defined of {@code mountFunctionsV4},
   * {@code mountCode} and {@code mountFunctions} builds its packages; rockets are processed concurrently (fail fast),
   * the function apps of one rocket sequentially.
   *
   * @return the packages in rocket order, or {@code null} without rockets
   */
  public List<RocketZipResource> mountRocketsZipResources() {
    if (rockets == null) {
      return null;
    }
    List<Callable<List<RocketZipResource>>> mounts = new ArrayList<>();
    for (InfrastructureRocket rocket : rockets) {
      if (rocket.defines(Hook.MOUNT_FUNCTIONS_V4)) {
        mounts.add(() -> mountRocketCodeV4(rocket));
      } else if (rocket.defines(Hook.MOUNT_CODE)) {
        mounts.add(() -> mountRocketCode(rocket));
      } else if (rocket.defines(Hook.MOUNT_FUNCTIONS)) {
        mounts.add(() -> mountRocketZipResource(rocket));
      }
    }
    List<RocketZipResource> resources = new ArrayList<>();
    for (List<RocketZipResource> rocketResources : Promises.runAll(mounts)) {
      resources.addAll(rocketResources);
    }
    return resources;
  }

  /**
   * @param functionAppName a function app name
   * @return the name with every run of non-word characters replaced by {@code _}
   */
  static String sanitize(String functionAppName) {
    return NON_WORD.matcher(functionAppName).replaceAll("_");
  }

  @SuppressWarnings("deprecation")
  private List<RocketZipResource> mountRocketZipResource(InfrastructureRocket rocket) {
    Logger logger = Loggers.getLogger(config, "RocketBuilder#mountRocketZipResource");
    String rocketFunctionAppName = getFunctionAppName(rocket);
    String functionAppName = sanitize(rocketFunctionAppName);
    String fileName = "rocket_" + functionAppName + ".zip";
    List<FunctionDefinition> rocketFeaturesDefinitions = mountFunction(rocket);
    logger.info("Generating Rocket zip file " + fileName);
    // Rockets still use the legacy layout for backward compatibility
    ZipResource rocketZipResource = functionZip.copyZipLegacy(rocketFeaturesDefinitions, fileName, null);
    return List.of(new RocketZipResource(rocketFunctionAppName, rocketZipResource));
  }

  @SuppressWarnings("deprecation")
  private List<RocketZipResource> mountRocketCode(InfrastructureRocket rocket) {
    Logger logger = Loggers.getLogger(config, "RocketBuilder#mountRocketCode");
    List<FunctionAppFunctionsDefinition> rocketFeaturesDefinitions = mountCodeFunction(rocket);
    List<RocketZipResource> result = new ArrayList<>();
    for (FunctionAppFunctionsDefinition functionDefinition : rocketFeaturesDefinitions) {
      String functionAppName = sanitize(functionDefinition.functionAppName());
      String fileName = "rocket_" + functionAppName + ".zip";
      logger.info("Generating Rocket zip file " + fileName + " for function " + functionAppName);
      ZipResource rocketZipResource = functionZip.copyZipLegacy(
          functionDefinition.functionsDefinitions(), fileName, functionDefinition.hostJsonPath());
      result.add(new RocketZipResource(functionAppName, rocketZipResource));
    }
    return result;
  }

  @SuppressWarnings("deprecation")
  private List<FunctionDefinition> mountFunction(InfrastructureRocket rocket) {
    if (!rocket.defines(Hook.MOUNT_FUNCTIONS)) {
      throw new IllegalStateException("mountFunctions method not found on rocket " + ROCKET_STRING);
    }
    return rocket.mountFunctions(config, applicationSynthStack, RocketUtils.buildRocketUtils());
  }

  private List<FunctionAppFunctionsDefinition> mountCodeFunction(InfrastructureRocket rocket) {
    if (!rocket.defines(Hook.MOUNT_CODE)) {
      throw new IllegalStateException("mountCode method not found on rocket " + ROCKET_STRING);
    }
    return rocket.mountCode(config, applicationSynthStack, RocketUtils.buildRocketUtils());
  }

  private List<RocketZipResource> mountRocketCodeV4(InfrastructureRocket rocket) {
    Logger logger = Loggers.getLogger(config, "RocketBuilder#mountRocketCodeV4");
    List<FunctionAppV4Definition> rocketFeaturesDefinitions = mountCodeFunctionV4(rocket);
    List<RocketZipResource> result = new ArrayList<>();
    for (FunctionAppV4Definition functionDefinition : rocketFeaturesDefinitions) {
      String functionAppName = sanitize(functionDefinition.functionAppName());
      String fileName = "rocket_" + functionAppName + ".zip";
      logger.info("Generating Rocket v4 zip file " + fileName + " for function " + functionAppName);
      ZipResource rocketZipResource = functionZip.copyZipV4ForRocket(
          functionDefinition.functionsDefinitions(), fileName, functionDefinition.hostJsonPath());
      result.add(new RocketZipResource(functionAppName, rocketZipResource));
    }
    return result;
  }

  private List<FunctionAppV4Definition> mountCodeFunctionV4(InfrastructureRocket rocket) {
    if (!rocket.defines(Hook.MOUNT_FUNCTIONS_V4)) {
      throw new IllegalStateException("mountFunctionsV4 method not found on rocket " + ROCKET_STRING);
    }
    return rocket.mountFunctionsV4(config, applicationSynthStack, RocketUtils.buildRocketUtils());
  }

  private String getFunctionAppName(InfrastructureRocket rocket) {
    if (!rocket.defines(Hook.GET_FUNCTION_APP_NAME)) {
      throw new IllegalStateException("FunctionAppName not found on rocket " + ROCKET_STRING);
    }
    return rocket.getFunctionAppName(applicationSynthStack);
  }
}
