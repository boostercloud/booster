package com.boostercloud.rocket.fileuploads.types;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.types.BoosterJson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link BraceExpansion} against the JavaScript package: {@code differential/brace-expansion-1.1.16-corpus.jsonl} was
 * produced by {@code differential/generate.js} with brace-expansion 1.1.16 on Node.
 */
class BraceExpansionDifferentialTest {

  @Test
  void expands_like_brace_expansion_1_1_16() throws IOException {
    List<Map<String, Object>> cases = MinimatchDifferentialTest.corpus("brace-expansion-1.1.16-corpus.jsonl");
    List<String> mismatches = new ArrayList<>();
    for (Map<String, Object> testCase : cases) {
      String source = (String) testCase.get("s");
      Object expected = testCase.get("r");
      Object actual;
      try {
        actual = BraceExpansion.expand(source);
      } catch (RuntimeException e) {
        actual = "error:" + e.getMessage();
      }
      if (!expected.equals(actual)) {
        mismatches.add(BoosterJson.toJson(testCase) + " -> " + BoosterJson.toJson(actual));
      }
    }
    assertThat(cases).hasSizeGreaterThan(1500);
    assertThat(mismatches).isEmpty();
  }

  @Test
  void documented_examples() {
    assertThat(BraceExpansion.expand("a{b,c}d")).containsExactly("abd", "acd");
    assertThat(BraceExpansion.expand("a{b,}c")).containsExactly("abc", "ac");
    assertThat(BraceExpansion.expand("a{0..3}d")).containsExactly("a0d", "a1d", "a2d", "a3d");
    assertThat(BraceExpansion.expand("a{b,c{d,e}f}g")).containsExactly("abg", "acdfg", "acefg");
    assertThat(BraceExpansion.expand("a{2..}b")).containsExactly("a{2..}b");
    assertThat(BraceExpansion.expand("a{b}c")).containsExactly("a{b}c");
    assertThat(BraceExpansion.expand("")).isEmpty();
  }
}
