package com.boostercloud.framework.provider.local.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Differential test of {@link JsRegExp} against V8: {@code src/test/resources/regexp/cases.json} holds 1500 random
 * patterns built from the tokens where JavaScript and Java differ (Annex B literals, escapes, classes, anchors,
 * quantifiers, groups, flags {@code i}, {@code m}, {@code s}), each with V8's {@code SyntaxError} message or its
 * {@code test} result on four random inputs ({@code generate.js} in the same folder, run with Node.js 24).
 */
class JsRegExpDifferentialTest {

  @TestFactory
  @SuppressWarnings("unchecked")
  Stream<DynamicTest> matchesLikeV8() throws IOException {
    Map<String, Object> file;
    try (InputStream input = getClass().getResourceAsStream("/regexp/cases.json")) {
      file = new ObjectMapper().readValue(input, Map.class);
    }
    List<DynamicTest> tests = new ArrayList<>();
    for (Map<String, Object> testCase : (List<Map<String, Object>>) file.get("cases")) {
      String source = (String) testCase.get("source");
      String flags = (String) testCase.get("flags");
      tests.add(DynamicTest.dynamicTest("/" + source + "/" + flags, () -> {
        if (testCase.containsKey("error")) {
          assertThatThrownBy(() -> new JsRegExp(source, flags)).isInstanceOf(JsRegExp.SyntaxError.class)
              .hasMessage((String) testCase.get("error"));
          return;
        }
        JsRegExp regExp = new JsRegExp(source, flags);
        for (List<Object> result : (List<List<Object>>) testCase.get("results")) {
          assertThat(regExp.test((String) result.get(0)))
              .as("/%s/%s.test(%s) [Java pattern: %s]", source, flags, result.get(0), regExp.pattern().pattern())
              .isEqualTo(result.get(1));
        }
      }));
    }
    return tests.stream();
  }
}
