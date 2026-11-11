package com.boostercloud.framework.provider.local.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * Differential test against the real NeDB: {@code src/test/resources/nedb/differential/cases.json} holds 750 random
 * queries (with sorts, skips, limits and projections) over 30 random collections, 240 random modifier updates and 6
 * data files written by NeDB (inserts, replacements, updates, removals, unique and TTL indexes), together with what
 * {@code @seald-io/nedb} 4.0.2 produced for them ({@code generate.js} in the same folder). The Java store must return
 * the same documents, in the same order, serialized to the same text, fail with the same messages, load the same
 * state from the files and compact them to the same bytes.
 *
 * <p>The only accepted difference: where NeDB fails with a JavaScript {@code TypeError} ({@code Cannot read properties
 * of null}) from inside its code, Java fails with its own exception.
 */
class NedbDifferentialTest {

  private static final String TYPE_ERROR = "Cannot read properties of";

  @SuppressWarnings("unchecked")
  private static Map<String, Object> cases() throws IOException {
    try (InputStream input = NedbDifferentialTest.class.getResourceAsStream("/nedb/differential/cases.json")) {
      return new ObjectMapper().readValue(input, Map.class);
    }
  }

  /** Revives {@code {"$$date": n}} as a date and {@code {"$$regex": [source, flags]}} as a regular expression. */
  @SuppressWarnings("unchecked")
  static Object decode(Object value) {
    if (value instanceof Map<?, ?> map) {
      if (map.size() == 1 && map.get("$$regex") instanceof List<?> regex) {
        return new JsRegExp((String) regex.get(0), (String) regex.get(1));
      }
      if (map.size() == 1 && map.get("$$date") instanceof Number millis) {
        return new NedbDate(millis.doubleValue());
      }
      Map<String, Object> decoded = new LinkedHashMap<>();
      map.forEach((key, element) -> decoded.put((String) key, decode(element)));
      return decoded;
    }
    if (value instanceof List<?> list) {
      List<Object> decoded = new ArrayList<>();
      list.forEach(element -> decoded.add(decode(element)));
      return decoded;
    }
    return value;
  }

  @TestFactory
  @SuppressWarnings("unchecked")
  Stream<DynamicTest> queriesAndCursorsMatchNedb() throws IOException {
    List<Map<String, Object>> datasets = (List<Map<String, Object>>) cases().get("datasets");
    List<DynamicTest> tests = new ArrayList<>();
    for (int d = 0; d < datasets.size(); d++) {
      Map<String, Object> dataset = datasets.get(d);
      List<Map<String, Object>> documents = new ArrayList<>();
      for (String line : (List<String>) dataset.get("documents")) {
        documents.add((Map<String, Object>) Serialization.deserialize(line));
      }
      List<Map<String, Object>> cases = (List<Map<String, Object>>) dataset.get("cases");
      for (int c = 0; c < cases.size(); c++) {
        Map<String, Object> testCase = cases.get(c);
        String name = "dataset " + d + ", query " + c + ": " + testCase.get("query") + " " + testCase.get("options");
        tests.add(DynamicTest.dynamicTest(name, () -> {
          Datastore datastore = new Datastore(DatastoreOptions.inMemory());
          datastore.insert(documents);
          Map<String, Object> query = (Map<String, Object>) decode(testCase.get("query"));
          Map<String, Object> options = (Map<String, Object>) decode(testCase.get("options"));
          List<String> result = new ArrayList<>();
          Integer count = null;
          String error = null;
          try {
            Cursor cursor = datastore.find(query, (Map<String, Object>) options.get("projection"));
            if (options.get("sort") != null) {
              cursor = cursor.sort((Map<String, Object>) options.get("sort"));
            }
            if (options.get("skip") != null) {
              cursor = cursor.skip((Number) options.get("skip"));
            }
            if (options.get("limit") != null) {
              cursor = cursor.limit((Number) options.get("limit"));
            }
            for (Map<String, Object> document : cursor.exec()) {
              result.add(Serialization.serialize(document));
            }
            count = datastore.count(query);
          } catch (RuntimeException e) {
            error = e.getMessage();
          }
          assertOutcome(testCase, error);
          if (testCase.containsKey("result")) {
            assertThat(result).containsExactlyElementsOf((List<String>) testCase.get("result"));
            assertThat(count).isEqualTo(testCase.get("count"));
          }
        }));
      }
    }
    return tests.stream();
  }

  @TestFactory
  @SuppressWarnings("unchecked")
  Stream<DynamicTest> modifiersMatchNedb() throws IOException {
    List<Map<String, Object>> datasets = (List<Map<String, Object>>) cases().get("datasets");
    List<DynamicTest> tests = new ArrayList<>();
    for (int d = 0; d < datasets.size(); d++) {
      List<Map<String, Object>> modifications = (List<Map<String, Object>>) datasets.get(d).get("modifications");
      for (int m = 0; m < modifications.size(); m++) {
        Map<String, Object> testCase = modifications.get(m);
        String name = "dataset " + d + ", update " + m + ": " + testCase.get("update") + " on "
            + testCase.get("document");
        tests.add(DynamicTest.dynamicTest(name, () -> {
          Map<String, Object> document = (Map<String, Object>) decode(testCase.get("document"));
          Map<String, Object> update = (Map<String, Object>) decode(testCase.get("update"));
          String result = null;
          String error = null;
          try {
            result = Serialization.serialize(Model.modify(document, update));
          } catch (RuntimeException e) {
            error = e.getMessage();
          }
          assertOutcome(testCase, error);
          if (testCase.containsKey("result")) {
            assertThat(result).isEqualTo(testCase.get("result"));
          }
        }));
      }
    }
    return tests.stream();
  }

  /** Data files written by NeDB load to the same documents and indexes, and are compacted to the same bytes. */
  @TestFactory
  @SuppressWarnings("unchecked")
  Stream<DynamicTest> dataFilesWrittenByNedbLoadAndCompactTheSame(@TempDir Path directory) throws IOException {
    List<Map<String, Object>> files = (List<Map<String, Object>>) cases().get("files");
    List<DynamicTest> tests = new ArrayList<>();
    for (int f = 0; f < files.size(); f++) {
      Map<String, Object> file = files.get(f);
      Path path = directory.resolve("data-" + f + ".json");
      tests.add(DynamicTest.dynamicTest("data file " + f, () -> {
        Files.writeString(path, (String) file.get("raw"), StandardCharsets.UTF_8);
        try (Datastore datastore = new Datastore(DatastoreOptions.of(path))) {
          datastore.loadDatabase();
          List<String> state = new ArrayList<>();
          for (Map<String, Object> document : datastore.getAllData()) {
            state.add(Serialization.serialize(document));
          }
          assertThat(state).containsExactlyElementsOf((List<String>) file.get("state"));
          assertThat(datastore.indexNames()).containsExactlyElementsOf((List<String>) file.get("indexes"));
        }
        assertThat(Files.readString(path, StandardCharsets.UTF_8)).isEqualTo(file.get("compacted"));
      }));
    }
    return tests.stream();
  }

  private static void assertOutcome(Map<String, Object> testCase, String error) {
    Object expected = testCase.get("error");
    if (expected == null) {
      assertThat(error).as("unexpected error").isNull();
    } else if (((String) expected).startsWith(TYPE_ERROR)) {
      assertThat(error).as("NeDB failed with a TypeError: %s", expected).isNotNull();
    } else {
      assertThat(error).isEqualTo(expected);
    }
  }
}
