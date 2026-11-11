package com.boostercloud.rocket.fileuploads.types;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.types.BoosterJson;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link Minimatch} against the JavaScript package: {@code differential/minimatch-3.1.5-corpus.jsonl} was produced by
 * {@code differential/generate.js} with minimatch 3.1.5 on Node (fixed cases plus random patterns of glob syntax and
 * random paths). Every case must give the same answer.
 */
class MinimatchDifferentialTest {

  static List<Map<String, Object>> corpus(String name) throws IOException {
    List<Map<String, Object>> cases = new ArrayList<>();
    try (InputStream in = MinimatchDifferentialTest.class.getResourceAsStream("/differential/" + name)) {
      for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
        if (!line.isBlank()) {
          cases.add(BoosterJson.parseObject(line));
        }
      }
    }
    return cases;
  }

  @Test
  void matches_like_minimatch_3_1_5() throws IOException {
    List<Map<String, Object>> cases = corpus("minimatch-3.1.5-corpus.jsonl");
    List<String> mismatches = new ArrayList<>();
    for (Map<String, Object> testCase : cases) {
      String pattern = (String) testCase.get("p");
      String path = (String) testCase.get("f");
      Object expected = testCase.get("r");
      Object actual;
      try {
        actual = Minimatch.minimatch(path, pattern);
      } catch (RuntimeException e) {
        actual = "error:" + e.getMessage();
      }
      if (!expected.equals(actual)) {
        mismatches.add(BoosterJson.toJson(testCase) + " -> " + actual);
      }
    }
    assertThat(cases).hasSizeGreaterThan(8000);
    assertThat(mismatches).isEmpty();
  }
}
