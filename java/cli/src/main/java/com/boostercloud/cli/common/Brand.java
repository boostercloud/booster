package com.boostercloud.cli.common;

/** Outputs text with the Booster brand colours ({@code common/brand.ts}). */
public final class Brand {
  private Brand() {}

  /**
   * Applies a dangerous feeling (red, {@code #FA6E59}) to some text.
   *
   * @param text the text
   * @return the coloured text
   */
  public static String dangerize(String text) {
    return ColorSupport.hex("#FA6E59", text);
  }

  /**
   * Applies a melancholic blue ({@code #4797D8}) to some text.
   *
   * @param text the text
   * @return the coloured text
   */
  public static String mellancholize(String text) {
    return ColorSupport.hex("#4797D8", text);
  }

  /**
   * Applies a nice canary yellow tone ({@code #FFD243}) to some text.
   *
   * @param text the text
   * @return the coloured text
   */
  public static String canarize(String text) {
    return ColorSupport.hex("#FFD243", text);
  }

  /**
   * Applies an energic orange tone ({@code #FF8D40}) to some text.
   *
   * @param text the text
   * @return the coloured text
   */
  public static String energize(String text) {
    return ColorSupport.hex("#FF8D40", text);
  }
}
