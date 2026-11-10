package com.boostercloud.rocket.fileuploads.azure.infrastructure;

import com.boostercloud.framework.provider.azure.infrastructure.rockets.InfrastructureRocket;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketUtils;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionAppV4Definition;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.fileuploads.azure.infrastructure.functions.Functions;
import com.boostercloud.rocket.fileuploads.azure.infrastructure.synth.Synth;
import com.boostercloud.rocket.fileuploads.types.RocketFilesConfiguration;
import java.util.List;
import java.util.Objects;

/**
 * The Azure infrastructure rocket ({@code { mountStack, mountFunctionsV4, getFunctionAppName }} of
 * {@code src/index.ts}): the storage accounts, containers, the rocket's function app and its role assignments
 * ({@link Synth}), and the blob trigger functions of that app ({@link Functions}).
 */
public final class AzureRocketFiles implements InfrastructureRocket {

  private final RocketFilesConfiguration configuration;

  /**
   * @param configuration the rocket parameters
   */
  public AzureRocketFiles(RocketFilesConfiguration configuration) {
    this.configuration = Objects.requireNonNull(configuration, "configuration");
  }

  /**
   * @return the rocket parameters
   */
  public RocketFilesConfiguration configuration() {
    return configuration;
  }

  @Override
  public ApplicationSynthStack mountStack(BoosterConfig config, ApplicationSynthStack applicationSynthStack,
      RocketUtils utils) {
    return Synth.mountStack(configuration, config, applicationSynthStack, utils);
  }

  @Override
  public List<FunctionAppV4Definition> mountFunctionsV4(BoosterConfig config,
      ApplicationSynthStack applicationSynthStack, RocketUtils utils) {
    return Functions.mountFunctionsV4(configuration, config, applicationSynthStack, utils);
  }

  @Override
  public String getFunctionAppName(ApplicationSynthStack applicationSynthStack) {
    return Functions.getFunctionAppName(configuration, applicationSynthStack);
  }
}
