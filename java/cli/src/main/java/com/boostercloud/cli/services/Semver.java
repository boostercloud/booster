package com.boostercloud.cli.services;

import com.boostercloud.cli.common.CliException;
import java.util.Arrays;
import java.util.List;

/**
 * A minimal {@code X.Y.Z} version ({@code services/semver.ts}).
 *
 * <p>Each part is parsed like JavaScript's {@code parseInt}: leading digits are used ({@code 3-beta} is 3) and a
 * part without digits is "not a number", which is never equal to nor greater than anything.
 */
public final class Semver {
  private final List<Integer> versionParts;

  /**
   * Parses a version.
   *
   * @param semverString a version such as {@code 4.1.0}
   * @throws CliException when the version does not have exactly three parts
   */
  public Semver(String semverString) {
    this.versionParts = Arrays.stream(semverString.split("\\.", -1)).map(Semver::parseInt).toList();
    if (versionParts.size() != 3) {
      throw new CliException(
          "Versions must follow semantic convention X.Y.Z | current version: " + semverString + ".");
    }
  }

  /**
   * The parsed parts; {@code null} stands for {@code NaN}.
   *
   * @return the breaking, feature and fix numbers
   */
  public List<Integer> versionParts() {
    return java.util.Collections.unmodifiableList(versionParts);
  }

  /**
   * @param version another version
   * @return whether both have the same breaking (major) number
   */
  public boolean equalsInBreakingSection(Semver version) {
    return same(0, version);
  }

  /**
   * @param version another version
   * @return whether both have the same feature (minor) number
   */
  public boolean equalsInFeatureSection(Semver version) {
    return same(1, version);
  }

  /**
   * @param version another version
   * @return whether both have the same fix (patch) number
   */
  public boolean equalsInFixSection(Semver version) {
    return same(2, version);
  }

  /**
   * @param version another version
   * @return whether the three parts are equal
   */
  public boolean equals(Semver version) {
    return equalsInBreakingSection(version) && equalsInFeatureSection(version) && equalsInFixSection(version);
  }

  /**
   * @param version another version
   * @return whether this version has a greater breaking number
   */
  public boolean greaterInBreakingSectionThan(Semver version) {
    return greater(0, version);
  }

  /**
   * @param version another version
   * @return whether this version has a greater feature number
   */
  public boolean greaterInFeatureSectionThan(Semver version) {
    return greater(1, version);
  }

  @Override
  public String toString() {
    return String.join(".", versionParts.stream().map(p -> p == null ? "NaN" : p.toString()).toList());
  }

  private boolean same(int index, Semver other) {
    Integer mine = versionParts.get(index);
    Integer theirs = other.versionParts.get(index);
    return mine != null && mine.equals(theirs);
  }

  private boolean greater(int index, Semver other) {
    Integer mine = versionParts.get(index);
    Integer theirs = other.versionParts.get(index);
    return mine != null && theirs != null && mine > theirs;
  }

  // JavaScript parseInt(value, 10): optional leading whitespace and sign, then as many digits as possible
  private static Integer parseInt(String value) {
    String s = value.stripLeading();
    int i = 0;
    boolean negative = false;
    if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
      negative = s.charAt(i) == '-';
      i++;
    }
    int start = i;
    while (i < s.length() && Character.isDigit(s.charAt(i)) && s.charAt(i) < 128) {
      i++;
    }
    if (i == start) {
      return null;
    }
    try {
      int parsed = Integer.parseInt(s.substring(start, i));
      return negative ? -parsed : parsed;
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
