package com.boostercloud.rocket.fileuploads.e2e.app;

import com.boostercloud.framework.core.Booster;

/** The main class of the end-to-end application, as {@code boost new} generates it ({@code boost start} runs it). */
public final class Main {

  private Main() {}

  /**
   * @param args {@code start --port <N>}, {@code synth}...
   */
  public static void main(String[] args) {
    Booster.run(Main.class, args);
  }
}
