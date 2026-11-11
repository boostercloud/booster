package com.boostercloud.framework.provider.azure.infrastructure;

import com.boostercloud.framework.provider.azure.Constants.EnvironmentVarNames;
import com.boostercloud.framework.provider.azure.infrastructure.helper.Params;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.InfrastructureRocket;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.InfrastructureRocket.Hook;
import com.boostercloud.framework.provider.azure.infrastructure.synth.ApplicationSynth;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.Fn;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformApp;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformStack;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Port of {@code azure-stack.ts}: the single Terraform stack of the application. Its constructor synthesizes every
 * base resource ({@link ApplicationSynth}) and computes the default app settings of the main function app;
 * {@link #addAppSettingsToFunctionApp} attaches them, together with the rocket settings, once the rockets have mounted
 * their resources.
 */
public final class AzureStack extends TerraformStack {

  /** The stack bag. */
  public final ApplicationSynthStack applicationStack;

  /** The default app settings of the main function app (attached by {@link #addAppSettingsToFunctionApp}). */
  public final Map<String, String> defaultApplicationSettings;

  private final BoosterConfig config;

  /**
   * @param scope the application
   * @param name the stack id ({@code appName + environmentName})
   * @param config the configuration
   * @param zipFile the WebPubSub bootstrap package of the main function app, or {@code null}
   * @param configuration the Azure settings
   * @param boosterAppClass the application class ({@code BOOSTER_APP_CLASS})
   */
  public AzureStack(
      TerraformApp scope,
      String name,
      BoosterConfig config,
      String zipFile,
      Params.Configuration configuration,
      String boosterAppClass) {
    super(scope, name);
    this.config = config;
    ApplicationSynth applicationSynth = new ApplicationSynth(this, config, configuration, boosterAppClass);
    this.applicationStack = applicationSynth.synth(zipFile);
    this.defaultApplicationSettings = Collections.unmodifiableMap(new LinkedHashMap<>(
        applicationSynth.buildDefaultAppSettings(applicationStack, applicationStack.storageAccount, "func")));
  }

  /**
   * {@code addAppSettingsToFunctionApp(rockets)}: sets the main function app's {@code app_settings} to
   * {@code merge(<defaults>, { ROCKET_FUNCTION_APP_NAMES, ROCKET_PACKAGE_MAPPING })}.
   *
   * <ul>
   *   <li>{@code ROCKET_FUNCTION_APP_NAMES}: every rocket's {@code getFunctionAppName(stack)} (empty for rockets without
   *       it), joined with {@code ,}; empty without rockets.
   *   <li>{@code ROCKET_PACKAGE_MAPPING}: {@code <package>:<functionApp>} pairs joined with {@code ;}, one per rocket
   *       descriptor of {@code config.rockets} whose parameters have a non-empty {@code rocketProviderPackage};
   *       {@code <package>} is that value without a leading {@code @scope/} and without its last {@code -segment}.
   *       Quirk kept from TypeScript: the function app is always the one of the <em>first</em> rocket that defines
   *       {@code getFunctionAppName}; pairs without a function app are skipped and duplicate packages overwrite.
   * </ul>
   *
   * @param rockets the loaded rockets, or {@code null}
   * @throws IllegalStateException {@code Function app not defined}
   */
  public void addAppSettingsToFunctionApp(List<InfrastructureRocket> rockets) {
    if (applicationStack.functionApp == null) {
      throw new IllegalStateException("Function app not defined");
    }
    Map<String, String> rocketPackageMapping = new LinkedHashMap<>();
    if (config.rockets != null) {
      for (RocketDescriptor rocket : config.rockets) {
        String rocketProviderPackage = rocketProviderPackageOf(rocket.parameters());
        if (rocketProviderPackage != null && !rocketProviderPackage.isEmpty()) {
          String basePackage = rocketProviderPackage
              .replaceFirst("^@[^/]+/", "") // Remove scope (@org/)
              .replaceFirst("-[^-]+$", ""); // Remove last segment after dash (provider)
          String functionAppName = firstFunctionAppName(rockets);
          if (functionAppName != null && !functionAppName.isEmpty()) {
            rocketPackageMapping.put(basePackage, functionAppName);
          }
        }
      }
    }
    StringJoiner mappingString = new StringJoiner(";");
    rocketPackageMapping.forEach((pkg, func) -> mappingString.add(pkg + ":" + func));

    String functionAppNames = "";
    if (rockets != null) {
      List<String> names = new ArrayList<>();
      for (InfrastructureRocket rocket : rockets) {
        String name = rocket.defines(Hook.GET_FUNCTION_APP_NAME) ? rocket.getFunctionAppName(applicationStack) : "";
        names.add(name == null ? "" : name);
      }
      functionAppNames = String.join(",", names);
    }
    Map<String, Object> rocketSettings = new LinkedHashMap<>();
    rocketSettings.put(EnvironmentVarNames.ROCKET_FUNCTION_APP_NAMES, functionAppNames);
    rocketSettings.put(EnvironmentVarNames.ROCKET_PACKAGE_MAPPING, mappingString.toString());
    applicationStack.functionApp.setAttribute("app_settings",
        Fn.merge(List.of(new LinkedHashMap<String, Object>(defaultApplicationSettings), rocketSettings)));
  }

  private String firstFunctionAppName(List<InfrastructureRocket> rockets) {
    if (rockets == null) {
      return null;
    }
    for (InfrastructureRocket rocket : rockets) {
      if (rocket.defines(Hook.GET_FUNCTION_APP_NAME)) {
        return rocket.getFunctionAppName(applicationStack);
      }
    }
    return null;
  }

  /**
   * @param parameters the parameters of a rocket descriptor (a map, a record, a bean...)
   * @return their {@code rocketProviderPackage} string, or {@code null}
   */
  static String rocketProviderPackageOf(Object parameters) {
    Object raw = parameters;
    if (parameters != null && !(parameters instanceof Map<?, ?>)) {
      try {
        raw = BoosterJson.toRaw(parameters);
      } catch (RuntimeException notSerializable) {
        return null;
      }
    }
    if (raw instanceof Map<?, ?> map && map.get("rocketProviderPackage") instanceof String value) {
      return value;
    }
    return null;
  }
}
