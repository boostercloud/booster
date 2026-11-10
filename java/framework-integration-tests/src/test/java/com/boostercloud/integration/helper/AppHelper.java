package com.boostercloud.integration.helper;

import com.boostercloud.application.tester.ProviderTestHelper;
import com.boostercloud.framework.provider.azure.infrastructure.testhelper.AzureTestHelper;
import com.boostercloud.framework.provider.local.infrastructure.testhelper.LocalTestHelper;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.integration.harness.AzureProviderTestHelper;
import com.boostercloud.integration.harness.E2eSettings;
import com.boostercloud.integration.harness.LocalProviderTestHelper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Application name, provider test helper and environment checks of the integration suites. Port of
 * {@code integration/helper/app-helper.ts}. Environment variables are read through {@link EnvironmentVariables} (its
 * in-process overrides replace TypeScript's assignments to {@code process.env}).
 *
 * <p>Differences from TypeScript: the AWS provider is not ported, so the supported providers are {@code AZURE} and
 * {@code LOCAL}; {@link #getProviderTestHelper(LocalTestHelper.Options)} lets the harness give the local helper the
 * ports and project directory of the application it launched (TypeScript's local helper uses fixed ports).
 */
public final class AppHelper {

  /** Environment variable selecting the provider under test. */
  public static final String TESTED_PROVIDER = "TESTED_PROVIDER";
  /** Environment variable with the application name suffix. */
  public static final String BOOSTER_APP_SUFFIX = "BOOSTER_APP_SUFFIX";
  /** {@code TESTED_PROVIDER} of the Azure provider. */
  public static final String AZURE = "AZURE";
  /** {@code TESTED_PROVIDER} of the local provider. */
  public static final String LOCAL = "LOCAL";

  private AppHelper() {}

  /**
   * @return {@code my-store-<BOOSTER_APP_SUFFIX>} ({@code my-store-undefined} when the suffix is not set)
   */
  public static String applicationName() {
    return "my-store-" + EnvironmentVariables.get(BOOSTER_APP_SUFFIX).orElse("undefined");
  }

  /**
   * The provider test helper of {@code TESTED_PROVIDER}, built like TypeScript's {@code getProviderTestHelper()}; the
   * local helper reads its ports from the environment.
   *
   * @return the helper
   * @throws IllegalStateException when {@code BOOSTER_ENV} is missing or the provider is not supported
   */
  public static ProviderTestHelper getProviderTestHelper() {
    return getProviderTestHelper(LocalTestHelper.Options.defaults());
  }

  /**
   * {@link #getProviderTestHelper()} with explicit options for the local helper.
   *
   * @param localOptions where the local application runs
   * @return the helper
   * @throws IllegalStateException when {@code BOOSTER_ENV} is missing or the provider is not supported
   */
  public static ProviderTestHelper getProviderTestHelper(LocalTestHelper.Options localOptions) {
    String provider = EnvironmentVariables.get(TESTED_PROVIDER).orElse(null);
    String environmentName = checkAndGetCurrentEnv();
    Map<String, Supplier<ProviderTestHelper>> providerHelpers = new LinkedHashMap<>();
    providerHelpers.put(AZURE,
        () -> new AzureProviderTestHelper(AzureTestHelper.build(applicationName(), environmentName)));
    providerHelpers.put(LOCAL,
        () -> new LocalProviderTestHelper(LocalTestHelper.build(applicationName(), localOptions)));
    List<String> supportedProviders = List.copyOf(providerHelpers.keySet());
    if (provider == null || provider.isEmpty() || !supportedProviders.contains(provider)) {
      throw new IllegalStateException("Invalid provider to run tests. Environment variable TESTED_PROVIDER is "
          + (provider == null ? "undefined" : provider) + " and the supported ones are ["
          + String.join(",", supportedProviders) + "]");
    }
    return providerHelpers.get(provider).get();
  }

  /**
   * @return {@code TESTED_PROVIDER}, or {@code null}
   */
  public static String testedProvider() {
    return EnvironmentVariables.get(TESTED_PROVIDER).orElse(null);
  }

  /**
   * When {@code BOOSTER_APP_SUFFIX} is not set, sets it to the first 7 characters of {@code git rev-parse HEAD} (a
   * unique suffix per commit, so that tests of different branches do not collide) and logs
   * {@code setting BOOSTER_APP_SUFFIX=<suffix>}.
   *
   * @throws IllegalStateException when git fails
   */
  public static void setEnv() {
    if (EnvironmentVariables.get(BOOSTER_APP_SUFFIX).filter(value -> !value.isEmpty()).isEmpty()) {
      String stdout = gitRevParseHead();
      EnvironmentVariables.set(BOOSTER_APP_SUFFIX, stdout.trim().substring(0, Math.min(7, stdout.trim().length())));
      System.out.println("setting BOOSTER_APP_SUFFIX=" + EnvironmentVariables.get(BOOSTER_APP_SUFFIX).orElse(""));
    }
  }

  /**
   * @return {@code BOOSTER_ENV} (untrimmed)
   * @throws IllegalStateException when it is missing or blank
   */
  public static String checkAndGetCurrentEnv() {
    String env = EnvironmentVariables.get(EnvironmentVariables.BOOSTER_ENV).orElse(null);
    if (env == null || env.trim().isEmpty()) {
      throw new IllegalStateException(
          "Booster environment is missing. You need to provide an environment to configure your Booster project");
    }
    return env;
  }

  private static String gitRevParseHead() {
    try {
      Process git = new ProcessBuilder("git", "rev-parse", "HEAD")
          .directory(E2eSettings.moduleDirectory().toFile())
          .redirectErrorStream(true)
          .start();
      String output = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int exitCode = git.waitFor();
      if (exitCode != 0) {
        throw new IllegalStateException("Command failed: git rev-parse HEAD\n" + output);
      }
      return output;
    } catch (IOException e) {
      throw new IllegalStateException("Command failed: git rev-parse HEAD", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted running git rev-parse HEAD", e);
    }
  }
}
