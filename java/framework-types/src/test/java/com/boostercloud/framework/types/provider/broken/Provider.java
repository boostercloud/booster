package com.boostercloud.framework.types.provider.broken;

/** A provider class without the factory method. */
public final class Provider {
  private Provider() {}

  /**
   * @return nothing useful
   */
  public static String somethingElse() {
    return "";
  }
}
