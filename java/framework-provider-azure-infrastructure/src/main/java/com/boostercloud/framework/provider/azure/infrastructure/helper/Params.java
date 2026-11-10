package com.boostercloud.framework.provider.azure.infrastructure.helper;

import com.boostercloud.framework.types.EnvironmentVariables;

/**
 * Port of {@code helper/params.ts}: the Azure credentials and region of the deploying machine.
 *
 * <p>TypeScript reads them once, when the module is loaded; Java reads them when {@link #configuration()} is called
 * (the infrastructure calls it once per {@code Infrastructure.infrastructure(...)}), through
 * {@link EnvironmentVariables} so tests can override them. Unset variables are {@code ""} ({@code ?? ''}).
 */
public final class Params {

  /** {@code AZURE_APP_ID}: service principal client id. */
  public static final String AZURE_APP_ID = "AZURE_APP_ID";
  /** {@code AZURE_SECRET}: service principal secret. */
  public static final String AZURE_SECRET = "AZURE_SECRET";
  /** {@code AZURE_TENANT_ID}: tenant id. */
  public static final String AZURE_TENANT_ID = "AZURE_TENANT_ID";
  /** {@code AZURE_SUBSCRIPTION_ID}: subscription of the ARM calls and of the azurerm Terraform provider. */
  public static final String AZURE_SUBSCRIPTION_ID = "AZURE_SUBSCRIPTION_ID";
  /** {@code REGION}: Azure location, e.g. {@code East US}. */
  public static final String REGION = "REGION";

  private Params() {}

  /**
   * The {@code configuration} object of {@code params.ts}.
   *
   * @param appId {@code AZURE_APP_ID}
   * @param secret {@code AZURE_SECRET}
   * @param tenantId {@code AZURE_TENANT_ID}
   * @param subscriptionId {@code AZURE_SUBSCRIPTION_ID}
   * @param region {@code REGION}
   * @param publisherEmail {@code noreply@booster.cloud} (unused, kept for parity)
   * @param publisherName {@code Booster App} (unused, kept for parity)
   */
  public record Configuration(
      String appId,
      String secret,
      String tenantId,
      String subscriptionId,
      String region,
      String publisherEmail,
      String publisherName) {

    /**
     * @param appId client id
     * @param secret client secret
     * @param tenantId tenant id
     * @param subscriptionId subscription id
     * @param region region
     * @return a configuration with the constant publisher fields
     */
    public static Configuration of(String appId, String secret, String tenantId, String subscriptionId, String region) {
      return new Configuration(appId, secret, tenantId, subscriptionId, region, "noreply@booster.cloud", "Booster App");
    }

    @Override
    public String toString() {
      return "Configuration[appId=" + appId + ", tenantId=" + tenantId + ", subscriptionId=" + subscriptionId
          + ", region=" + region + "]";
    }
  }

  /**
   * @return the configuration read from the environment now
   */
  public static Configuration configuration() {
    return Configuration.of(
        EnvironmentVariables.get(AZURE_APP_ID).orElse(""),
        EnvironmentVariables.get(AZURE_SECRET).orElse(""),
        EnvironmentVariables.get(AZURE_TENANT_ID).orElse(""),
        EnvironmentVariables.get(AZURE_SUBSCRIPTION_ID).orElse(""),
        EnvironmentVariables.get(REGION).orElse(""));
  }
}
