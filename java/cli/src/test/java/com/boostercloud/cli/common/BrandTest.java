package com.boostercloud.cli.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Brand colours and the colour-level detection (chalk / supports-color in TypeScript). */
class BrandTest {
  @AfterEach
  void resetColors() {
    ColorSupport.setLevel(0);
  }

  @Test
  void leavesTextUnchangedWithoutColorSupport() {
    ColorSupport.setLevel(0);
    assertThat(Brand.dangerize("text")).isEqualTo("text");
    assertThat(Brand.mellancholize("text")).isEqualTo("text");
    assertThat(Brand.canarize("text")).isEqualTo("text");
    assertThat(Brand.energize("text")).isEqualTo("text");
  }

  @Test
  void usesTrueColorsAtLevelThree() {
    ColorSupport.setLevel(3);
    assertThat(Brand.dangerize("x")).isEqualTo("\u001b[38;2;250;110;89mx\u001b[39m");
    assertThat(Brand.mellancholize("x")).isEqualTo("\u001b[38;2;71;151;216mx\u001b[39m");
    assertThat(Brand.canarize("x")).isEqualTo("\u001b[38;2;255;210;67mx\u001b[39m");
    assertThat(Brand.energize("x")).isEqualTo("\u001b[38;2;255;141;64mx\u001b[39m");
  }

  @Test
  void downgradesTo256AndBasicColors() {
    ColorSupport.setLevel(2);
    assertThat(Brand.dangerize("x")).isEqualTo("\u001b[38;5;" + ColorSupport.ansi256(250, 110, 89) + "mx\u001b[39m");
    ColorSupport.setLevel(1);
    assertThat(Brand.dangerize("x")).isEqualTo("\u001b[" + ColorSupport.ansi16(250, 110, 89) + "mx\u001b[39m");
  }

  @Test
  void convertsColorsLikeColorConvert() {
    assertThat(ColorSupport.ansi256(0, 0, 0)).isEqualTo(16);
    assertThat(ColorSupport.ansi256(255, 255, 255)).isEqualTo(231);
    assertThat(ColorSupport.ansi256(255, 0, 0)).isEqualTo(196);
    assertThat(ColorSupport.ansi16(255, 0, 0)).isEqualTo(91);
    assertThat(ColorSupport.ansi16(0, 0, 0)).isEqualTo(30);
  }

  @Test
  void colorsEveryLine() {
    ColorSupport.setLevel(1);
    assertThat(ColorSupport.red("a\nb")).isEqualTo("\u001b[31ma\u001b[39m\n\u001b[31mb\u001b[39m");
  }

  @Test
  void detectsTheColorLevel() {
    assertThat(ColorSupport.detect(Map.of(), false, "Mac OS X")).isZero();
    assertThat(ColorSupport.detect(Map.of("TERM", "xterm"), true, "Linux")).isEqualTo(1);
    assertThat(ColorSupport.detect(Map.of("TERM", "xterm-256color"), true, "Linux")).isEqualTo(2);
    assertThat(ColorSupport.detect(Map.of("COLORTERM", "truecolor"), true, "Linux")).isEqualTo(3);
    assertThat(ColorSupport.detect(Map.of("TERM", "dumb"), true, "Linux")).isZero();
    assertThat(ColorSupport.detect(Map.of("NO_COLOR", "1", "TERM", "xterm"), true, "Linux")).isZero();
    assertThat(ColorSupport.detect(Map.of("FORCE_COLOR", "0", "TERM", "xterm"), true, "Linux")).isZero();
    assertThat(ColorSupport.detect(Map.of("FORCE_COLOR", "1"), false, "Linux")).isEqualTo(1);
    assertThat(ColorSupport.detect(Map.of("FORCE_COLOR", "3"), false, "Linux")).isEqualTo(3);
  }
}
