package com.boostercloud.integration;

import com.boostercloud.framework.core.Booster;

/**
 * Entry point of the integration application ("my-store"). Port of {@code src/index.ts}: TypeScript re-exports the
 * framework entry functions and calls {@code Booster.start(__dirname)}; in Java the framework registers those entry
 * points itself ({@code UserApp}) and {@link Booster#run(Class, String[])} starts the application and runs the action
 * given on the command line ({@code start [--port N]}, {@code synth}, {@code deploy}, {@code nuke}).
 */
public final class Main {

  private Main() {}

  /**
   * Starts the application ({@code Booster.start} scans this package tree) and runs the requested action.
   *
   * @param args the action and its arguments, e.g. {@code start --port 3000}
   */
  public static void main(String[] args) {
    Booster.run(Main.class, args);
  }
}
