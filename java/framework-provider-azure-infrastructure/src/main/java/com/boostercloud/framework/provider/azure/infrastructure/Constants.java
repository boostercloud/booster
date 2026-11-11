package com.boostercloud.framework.provider.azure.infrastructure;

import com.boostercloud.framework.provider.azure.helpers.JsValues;
import com.boostercloud.framework.types.EnvironmentVariables;

/**
 * Port of {@code infrastructure/constants.ts}: deployment knobs read from the deploying machine's environment, plus
 * the Java-only ones. TypeScript reads them at module load; Java reads them each time the synthesis asks for them
 * (once per synth), through {@link EnvironmentVariables}.
 */
public final class Constants {

  /** {@code DEFAULT_MAX_THROUGHPUT}. */
  public static final String DEFAULT_MAX_THROUGHPUT = "1000";

  /** Autoscale maximum RU/s of every Cosmos DB container. */
  public static final String AZURE_MAX_CONTAINER_THROUGHPUT = "AZURE_MAX_CONTAINER_THROUGHPUT";
  /** Autoscale maximum RU/s of the Cosmos DB database. */
  public static final String AZURE_MAX_DATABASE_THROUGHPUT = "AZURE_MAX_DATABASE_THROUGHPUT";
  /** Exactly {@code true}: B1 service plan with 2 workers instead of the Y1 consumption plan. */
  public static final String BOOSTER_AZURE_SERVICE_PLAN_BASIC = "BOOSTER_AZURE_SERVICE_PLAN_BASIC";
  /** Exactly {@code true}: {@code WAF_v2} application gateway with a WAF configuration. */
  public static final String BOOSTER_USE_WAF = "BOOSTER_USE_WAF";
  /** Passed through as the {@code BOOSTER_APP_NAME} app setting. */
  public static final String BOOSTER_APP_NAME = "BOOSTER_APP_NAME";

  /**
   * Java only: the {@code java_version} of the function apps' {@code application_stack} (TypeScript:
   * {@code nodeVersion: '~22'}). Default {@value #DEFAULT_JAVA_VERSION}.
   */
  public static final String BOOSTER_AZURE_JAVA_VERSION = "BOOSTER_AZURE_JAVA_VERSION";
  /**
   * The default {@code java_version}: the newest one the azurerm {@code application_stack} accepts. It cannot load the
   * Java 27 class files of the Booster Java port, so {@code deploy} refuses packages it cannot run
   * ({@link #BOOSTER_AZURE_SKIP_JAVA_VERSION_CHECK}; module README, "Java runtime of the function apps").
   */
  public static final String DEFAULT_JAVA_VERSION = "21";

  /**
   * Java only: exactly {@code true} skips the check that the function apps' Java runtime ({@link #javaVersion()}) can
   * load the packaged class files ({@code JavaRuntimeCompatibility}), for function apps that run another JVM.
   */
  public static final String BOOSTER_AZURE_SKIP_JAVA_VERSION_CHECK = "BOOSTER_AZURE_SKIP_JAVA_VERSION_CHECK";

  /** Java only: the Terraform executable (default {@code terraform}; e.g. {@code tofu}). */
  public static final String BOOSTER_TERRAFORM_BINARY = "BOOSTER_TERRAFORM_BINARY";
  /** The default Terraform executable. */
  public static final String DEFAULT_TERRAFORM_BINARY = "terraform";

  private Constants() {}

  /**
   * {@code MAX_CONTAINER_THROUGHPUT = parseInt(process.env.AZURE_MAX_CONTAINER_THROUGHPUT ?? '1000', 10)}.
   *
   * @return the value ({@code NaN}, written as {@code null}, when it does not start with digits)
   */
  public static double maxContainerThroughput() {
    return JsValues.parseInt(EnvironmentVariables.get(AZURE_MAX_CONTAINER_THROUGHPUT).orElse(DEFAULT_MAX_THROUGHPUT));
  }

  /**
   * {@code MAX_DATABASE_THROUGHPUT = parseInt(process.env.AZURE_MAX_DATABASE_THROUGHPUT ?? '1000', 10)}.
   *
   * @return the value
   */
  public static double maxDatabaseThroughput() {
    return JsValues.parseInt(EnvironmentVariables.get(AZURE_MAX_DATABASE_THROUGHPUT).orElse(DEFAULT_MAX_THROUGHPUT));
  }

  /**
   * @return {@code BASIC_SERVICE_PLAN} ({@code process.env.BOOSTER_AZURE_SERVICE_PLAN_BASIC ?? 'false'})
   */
  public static String basicServicePlan() {
    return EnvironmentVariables.get(BOOSTER_AZURE_SERVICE_PLAN_BASIC).orElse("false");
  }

  /**
   * @return {@code USE_WAF} ({@code process.env.BOOSTER_USE_WAF ?? 'false'})
   */
  public static String useWaf() {
    return EnvironmentVariables.get(BOOSTER_USE_WAF).orElse("false");
  }

  /**
   * @return the {@code java_version} of the function apps
   */
  public static String javaVersion() {
    return EnvironmentVariables.getOrDefault(BOOSTER_AZURE_JAVA_VERSION, DEFAULT_JAVA_VERSION);
  }

  /**
   * @return whether {@code BOOSTER_AZURE_SKIP_JAVA_VERSION_CHECK} is exactly {@code true}
   */
  public static boolean skipJavaVersionCheck() {
    return "true".equals(EnvironmentVariables.get(BOOSTER_AZURE_SKIP_JAVA_VERSION_CHECK).orElse("false"));
  }

  /**
   * @return the Terraform executable
   */
  public static String terraformBinary() {
    return EnvironmentVariables.getOrDefault(BOOSTER_TERRAFORM_BINARY, DEFAULT_TERRAFORM_BINARY);
  }
}
