package com.boostercloud.rocket.fileuploads.core;

import com.boostercloud.framework.common.Promises;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import com.boostercloud.rocket.fileuploads.types.RocketFilesConcepts;
import com.boostercloud.rocket.fileuploads.types.RocketFilesConfiguration;
import com.boostercloud.rocket.fileuploads.types.RocketFilesParams;
import com.boostercloud.rocket.fileuploads.types.RocketFilesProvider;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import com.boostercloud.rocket.fileuploads.types.RocketProviderPackageType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * The rocket's entry point in an application configuration ({@code BoosterRocketFiles} of
 * {@code rocket-file-uploads-core/src/index.ts}):
 *
 * <pre>{@code
 * Booster.configure("local", config -> {
 *   config.appName = "my-app";
 *   config.providerPackage = "com.boostercloud.framework.provider.local";
 *   config.rockets = List.of(new BoosterRocketFiles(config, List.of(clientStorage, cmsStorage)).rocketForLocal());
 * });
 * }</pre>
 *
 * <p>{@link #rocketForLocal()} / {@link #rocketForAzure()} normalise the configuration (storage names lower-cased),
 * register the rocket function {@value RocketFilesParams#FUNCTION_ID} on the configuration and return the descriptor of
 * the provider's infrastructure rocket. Only one of them can be called on a configuration (the rocket function id is
 * unique); each environment has its own configuration.
 *
 * <p>Java-only: creating the object registers {@code UploadedFileEvent}, {@code UploadedFileEntity} and its reducer in
 * the configuration ({@link RocketFilesConcepts}); TypeScript does it when the rocket's types package is imported.
 */
public final class BoosterRocketFiles {

  /** The descriptor package of the Azure infrastructure rocket (TypeScript {@code @boostercloud/rocket-file-uploads-azure-infrastructure}). */
  public static final String AZURE_INFRASTRUCTURE_PACKAGE = "com.boostercloud.rocket.fileuploads.azure.infrastructure";
  /** The descriptor package of the local infrastructure rocket (TypeScript {@code @boostercloud/rocket-file-uploads-local-infrastructure}). */
  public static final String LOCAL_INFRASTRUCTURE_PACKAGE = "com.boostercloud.rocket.fileuploads.local.infrastructure";

  private final BoosterConfig config;
  private final List<RocketFilesUserConfiguration> userConfiguration;

  /**
   * @param config the configuration of the environment being configured
   * @param userConfiguration one storage
   */
  public BoosterRocketFiles(BoosterConfig config, RocketFilesUserConfiguration userConfiguration) {
    this(config, userConfiguration == null ? null : List.of(userConfiguration));
  }

  /**
   * @param config the configuration of the environment being configured
   * @param userConfiguration the storages
   */
  public BoosterRocketFiles(BoosterConfig config, List<RocketFilesUserConfiguration> userConfiguration) {
    this.config = Objects.requireNonNull(config, "config");
    this.userConfiguration = userConfiguration == null ? null : new ArrayList<>(userConfiguration);
    RocketFilesConcepts.register(config);
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * @return the storages as given
   */
  public List<RocketFilesUserConfiguration> userConfiguration() {
    return userConfiguration == null ? null : List.copyOf(userConfiguration);
  }

  /**
   * {@code rocketForAzure()}.
   *
   * @return {@code { packageName: AZURE_INFRASTRUCTURE_PACKAGE, parameters }} with the Azure runtime library
   * @throws IllegalStateException when the rocket function is already registered on this configuration
   */
  public RocketDescriptor rocketForAzure() {
    RocketFilesConfiguration configuration = buildParameters(userConfiguration, RocketProviderPackageType.AZURE);
    register(configuration);
    return new RocketDescriptor(AZURE_INFRASTRUCTURE_PACKAGE, configuration);
  }

  /**
   * {@code rocketForLocal()}.
   *
   * @return {@code { packageName: LOCAL_INFRASTRUCTURE_PACKAGE, parameters }} with the local runtime library
   * @throws IllegalStateException when the rocket function is already registered on this configuration
   */
  public RocketDescriptor rocketForLocal() {
    RocketFilesConfiguration configuration = buildParameters(userConfiguration, RocketProviderPackageType.LOCAL);
    register(configuration);
    return new RocketDescriptor(LOCAL_INFRASTRUCTURE_PACKAGE, configuration);
  }

  /**
   * {@code register(configuration)}: loads the provider library now (as TypeScript's eager {@code require}) and
   * registers the rocket function, which runs {@link FileUploaded#fileUploaded} for every user configuration with the
   * same request, concurrently, failing with the first failure ({@code Promise.all}).
   */
  private void register(RocketFilesConfiguration configuration) {
    RocketFilesProvider provider = RocketFilesProviders.load(configuration.rocketProviderPackage());
    config.registerRocketFunction(RocketFilesParams.FUNCTION_ID, (boosterConfig, request) -> {
      List<Callable<Void>> operations = new ArrayList<>();
      for (RocketFilesUserConfiguration userConf : configuration.userConfiguration()) {
        operations.add(() -> {
          FileUploaded.fileUploaded(boosterConfig, request, userConf, provider);
          return null;
        });
      }
      Promises.runAll(operations);
      return null;
    });
  }

  /**
   * {@code buildParameters(userConfiguration, rocketProviderPackage)}: every storage name lower-cased; containers and
   * directories untouched, nothing validated. (TypeScript lower-cases the caller's objects in place; Java records are
   * immutable, so the descriptor holds normalised copies.)
   *
   * @param userConfiguration the storages
   * @param rocketProviderPackage the provider runtime library
   * @return the rocket parameters
   * @throws IllegalArgumentException {@code Cannot read properties of undefined (reading 'toLowerCase')} for a storage
   *     without name (or a missing configuration)
   */
  static RocketFilesConfiguration buildParameters(
      List<RocketFilesUserConfiguration> userConfiguration, RocketProviderPackageType rocketProviderPackage) {
    if (userConfiguration == null) {
      throw new IllegalArgumentException("Cannot read properties of undefined (reading 'storageName')");
    }
    List<RocketFilesUserConfiguration> normalized = new ArrayList<>(userConfiguration.size());
    for (RocketFilesUserConfiguration configuration : userConfiguration) {
      if (configuration == null) {
        throw new IllegalArgumentException("Cannot read properties of undefined (reading 'storageName')");
      }
      if (configuration.storageName() == null) {
        throw new IllegalArgumentException("Cannot read properties of undefined (reading 'toLowerCase')");
      }
      normalized.add(configuration.withStorageName(configuration.storageName().toLowerCase(Locale.ROOT)));
    }
    return new RocketFilesConfiguration(normalized, rocketProviderPackage);
  }
}
