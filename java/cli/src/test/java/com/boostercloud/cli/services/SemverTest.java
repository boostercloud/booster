package com.boostercloud.cli.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.cli.common.CliException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Port of {@code test/services/semver.test.ts}. */
class SemverTest {
  private final Semver versionA = new Semver("1.2.3");

  @ParameterizedTest
  @ValueSource(strings = {"", "1.2", "1.2.3.4"})
  void badConstruction(String version) {
    assertThatThrownBy(() -> new Semver(version))
        .isInstanceOf(CliException.class)
        .hasMessage("Versions must follow semantic convention X.Y.Z | current version: " + version + ".");
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({
    // version, equals, breakingEq, featureEq, fixEq, greaterBreaking, greaterFeature
    "1.2.3, true, true, true, true, false, false",
    "1.2.4, false, true, true, false, false, false",
    "1.2.2, false, true, true, false, false, false",
    "1.3.3, false, true, false, true, false, false",
    "1.1.3, false, true, false, true, false, true",
    "2.2.3, false, false, true, true, false, false",
    "0.2.3, false, false, true, true, true, false",
    "2.3.4, false, false, false, false, false, false",
    "0.1.2, false, false, false, false, true, true"
  })
  void compareVersions(
      String other,
      boolean equals,
      boolean breakingEq,
      boolean featureEq,
      boolean fixEq,
      boolean greaterBreaking,
      boolean greaterFeature) {
    Semver versionB = new Semver(other);
    assertThat(versionA.equals(versionB)).isEqualTo(equals);
    assertThat(versionA.equalsInBreakingSection(versionB)).isEqualTo(breakingEq);
    assertThat(versionA.equalsInFeatureSection(versionB)).isEqualTo(featureEq);
    assertThat(versionA.equalsInFixSection(versionB)).isEqualTo(fixEq);
    assertThat(versionA.greaterInBreakingSectionThan(versionB)).isEqualTo(greaterBreaking);
    assertThat(versionA.greaterInFeatureSectionThan(versionB)).isEqualTo(greaterFeature);
  }

  @ParameterizedTest
  @CsvSource({"3-beta.1.0, 3", "' 7.0.0', 7", "+2.0.0, 2", "-1.0.0, -1"})
  void parsesPartsLikeParseInt(String version, int major) {
    assertThat(new Semver(version).versionParts().getFirst()).isEqualTo(major);
  }

  @ParameterizedTest
  @ValueSource(strings = {"~1.2.3", "x.2.3"})
  void nonNumericPartsAreNeverEqualNorGreater(String version) {
    Semver nan = new Semver(version);
    Semver one = new Semver("1.2.3");
    assertThat(nan.versionParts().getFirst()).isNull();
    assertThat(nan.equalsInBreakingSection(nan)).isFalse();
    assertThat(nan.greaterInBreakingSectionThan(one)).isFalse();
    assertThat(one.greaterInBreakingSectionThan(nan)).isFalse();
  }
}
