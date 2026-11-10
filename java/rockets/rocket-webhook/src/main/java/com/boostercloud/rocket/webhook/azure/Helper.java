package com.boostercloud.rocket.webhook.azure;

/**
 * Port of {@code helper.ts} of the Azure infrastructure: the name of the webhook function app.
 */
public final class Helper {

  /** The suffix of the function app name. */
  public static final String FUNCTION_APP_SUFFIX = "webhookfunc";

  private Helper() {}

  /**
   * {@code getFunctionAppName(resourceGroupName)}.
   *
   * @param resourceGroupName the application's resource group ({@code <app><env>rg})
   * @return {@code <resourceGroupName>webhookfunc}
   */
  public static String getFunctionAppName(String resourceGroupName) {
    return resourceGroupName + FUNCTION_APP_SUFFIX;
  }
}
