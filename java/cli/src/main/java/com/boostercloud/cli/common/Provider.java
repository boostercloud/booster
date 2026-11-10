package com.boostercloud.cli.common;

import java.util.Arrays;
import java.util.Optional;

/**
 * The labels shown in the provider chooser of {@code new:project} ({@code common/provider.ts}).
 *
 * <p>The AWS provider is not ported to Java, so its entry is replaced by a local-only option.
 */
public enum Provider {
  /** Microsoft Azure. */
  AZURE("com.boostercloud.framework.provider.azure (Azure)", "com.boostercloud.framework.provider.azure"),
  /** Only the local provider (no cloud environment is configured). */
  LOCAL("com.boostercloud.framework.provider.local (Local only)", "com.boostercloud.framework.provider.local"),
  /** Any other provider package typed by the user. */
  OTHER("Other", "");

  private final String label;
  private final String packageName;

  Provider(String label, String packageName) {
    this.label = label;
    this.packageName = packageName;
  }

  /**
   * The text shown in the chooser.
   *
   * @return the label
   */
  public String label() {
    return label;
  }

  /**
   * The Java package of the provider ({@code config.providerPackage}); empty for {@link #OTHER}.
   *
   * @return the package name
   */
  public String packageName() {
    return packageName;
  }

  /**
   * Finds the provider whose label is the given text.
   *
   * @param label a label returned by the chooser
   * @return the provider, if any
   */
  public static Optional<Provider> fromLabel(String label) {
    return Arrays.stream(values()).filter(p -> p.label.equals(label)).findFirst();
  }

  @Override
  public String toString() {
    return label;
  }
}
