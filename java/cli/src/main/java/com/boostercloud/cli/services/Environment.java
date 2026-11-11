package com.boostercloud.cli.services;

import com.boostercloud.cli.CliContext;

/** Selection of the Booster environment ({@code services/environment.ts}). */
public final class Environment {
  /** The variable holding the selected environment. */
  public static final String BOOSTER_ENV = "BOOSTER_ENV";

  private Environment() {}

  /**
   * Sets {@code BOOSTER_ENV} from the {@code -e} flag (when given, it overrides the variable) and checks that an
   * environment is selected.
   *
   * @param ctx the CLI context, whose environment is updated
   * @param logger where the error is reported
   * @param environment the value of the flag, may be {@code null}
   * @return {@code false} (after logging the error) when no environment is selected
   */
  public static boolean initializeEnvironment(CliContext ctx, Logger logger, String environment) {
    if (environment != null && !environment.isEmpty()) {
      ctx.environment().put(BOOSTER_ENV, environment);
    }
    String current = currentEnvironment(ctx);
    if (current == null || current.isEmpty()) {
      logger.error(
          "Error: No environment set. Use the flag `-e` or set the environment variable BOOSTER_ENV to set it before"
              + " running this command. Example usage: `boost deploy -e <environment>`.");
      return false;
    }
    return true;
  }

  /**
   * @param ctx the CLI context
   * @return the value of {@code BOOSTER_ENV}, or {@code null}
   */
  public static String currentEnvironment(CliContext ctx) {
    return ctx.environment().get(BOOSTER_ENV);
  }
}
