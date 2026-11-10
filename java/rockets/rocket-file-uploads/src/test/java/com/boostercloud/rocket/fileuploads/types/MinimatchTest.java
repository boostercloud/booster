package com.boostercloud.rocket.fileuploads.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** The minimatch 3.1.5 behaviours listed in the TypeScript notes (§3.4), verified there with Node. */
class MinimatchTest {

  @ParameterizedTest(name = "minimatch({0}, {1}) = {2}")
  @CsvSource(delimiterString = " ~ ", quoteCharacter = '"', value = {
      "<empty> ~ ** ~ true",
      "<empty> ~ * ~ false",
      "<empty> ~ <empty> ~ true",
      ".hidden ~ * ~ false",
      "a/.hidden ~ a/* ~ false",
      "Client1 ~ client1 ~ false",
      "exact// ~ exact ~ true",
      "./exact ~ exact ~ false",
      "a ~ {a,b} ~ true",
      "ab ~ a? ~ true",
      "a ~ !b ~ true",
      "b ~ !b ~ false",
      "a ~ a/ ~ false",
      "a/ ~ a/ ~ true",
      "x/y ~ ** ~ true",
      "x/y/ ~ ** ~ true",
      "folder ~ [a-f]older ~ true",
      "a ~ #a ~ false",
      "a ~ <sp>a<sp> ~ true",
      "a.js ~ +(a|b).js ~ true",
      "c.js ~ +(a|b).js ~ false",
      "x ~ !!x ~ true"})
  void behaves_like_minimatch(String path, String pattern, boolean expected) {
    assertThat(Minimatch.minimatch(unquote(path), unquote(pattern))).isEqualTo(expected);
  }

  @Test
  void invalid_patterns_fail_like_minimatch() {
    assertThatThrownBy(() -> Minimatch.minimatch("a", null)).hasMessage("invalid pattern");
    assertThatThrownBy(() -> Minimatch.minimatch("a", "a".repeat(Minimatch.MAX_PATTERN_LENGTH + 1)))
        .hasMessage("pattern is too long");
  }

  @Test
  void a_compiled_pattern_can_be_reused() {
    Minimatch minimatch = new Minimatch("client/**");
    assertThat(minimatch.match("client/a/b")).isTrue();
    assertThat(minimatch.match("other/a")).isFalse();
  }

  @Test
  void javascript_trim_removes_bom_and_line_separators() {
    assertThat(Minimatch.jsTrim("﻿  a  \n")).isEqualTo("a");
  }

  private static String unquote(String value) {
    return value.replace("<empty>", "").replace("<sp>", "  ");
  }
}
