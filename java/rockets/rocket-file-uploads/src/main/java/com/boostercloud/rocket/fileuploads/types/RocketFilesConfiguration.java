package com.boostercloud.rocket.fileuploads.types;

import com.boostercloud.framework.types.BoosterJson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The parameters of the rocket descriptor ({@code RocketFilesConfiguration} of {@code rocket-files-params.ts}): every
 * user configuration and the provider runtime library. {@code BoosterRocketFiles} builds it; the infrastructure rockets,
 * {@code FileHandler} and the framework (rocket health, {@code ROCKET_PACKAGE_MAPPING}) read it.
 *
 * @param userConfiguration the storages, storage names already lower-cased
 * @param rocketProviderPackage the provider runtime library
 */
public record RocketFilesConfiguration(
    List<RocketFilesUserConfiguration> userConfiguration, RocketProviderPackageType rocketProviderPackage) {

  /** Canonical constructor: keeps an unmodifiable copy of the configurations ({@code null} stays {@code null}). */
  public RocketFilesConfiguration {
    userConfiguration = userConfiguration == null
        ? null : Collections.unmodifiableList(new ArrayList<>(userConfiguration));
  }

  /**
   * Reads descriptor parameters: the record itself (what {@code BoosterRocketFiles} puts in the descriptor) or its raw
   * JSON form ({@code { userConfiguration: [...], rocketProviderPackage: '@boostercloud/...' }}).
   *
   * @param parameters the descriptor parameters
   * @return the configuration
   * @throws IllegalArgumentException when the parameters cannot be read as a configuration
   */
  public static RocketFilesConfiguration from(Object parameters) {
    if (parameters instanceof RocketFilesConfiguration configuration) {
      return configuration;
    }
    if (parameters == null) {
      throw new IllegalArgumentException("Missing rocket files configuration");
    }
    try {
      return BoosterJson.convert(parameters, RocketFilesConfiguration.class);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid rocket files configuration: " + e.getMessage(), e);
    }
  }
}
