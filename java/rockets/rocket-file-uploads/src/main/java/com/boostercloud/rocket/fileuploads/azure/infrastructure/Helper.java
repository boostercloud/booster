package com.boostercloud.rocket.fileuploads.azure.infrastructure;

import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;

/**
 * Port of {@code helper.ts}.
 */
public final class Helper {

  /** The suffix of the rocket's function app name. */
  public static final String FUNCTION_APP_SUFFIX = "filesfunc";

  private Helper() {}

  /**
   * {@code getFunctionAppName(applicationSynthStack)}.
   *
   * @param applicationSynthStack the stack bag
   * @return {@code <resourceGroupName>filesfunc}
   */
  public static String getFunctionAppName(ApplicationSynthStack applicationSynthStack) {
    return applicationSynthStack.resourceGroupName + FUNCTION_APP_SUFFIX;
  }
}
