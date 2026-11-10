package com.boostercloud.framework.provider.azure.infrastructure.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTextTest {

  @Test
  void writes_like_json_stringify_with_two_spaces() {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("version", "2.0");
    value.put("list", List.of(1, 2.5, true));
    value.put("empty", Map.of());
    value.put("none", List.of());
    value.put("nothing", null);
    assertThat(JsonText.stringify(value, 2)).isEqualTo("""
        {
          "version": "2.0",
          "list": [
            1,
            2.5,
            true
          ],
          "empty": {},
          "none": [],
          "nothing": null
        }""");
  }

  @Test
  void writes_compact_json_and_javascript_numbers() {
    assertThat(JsonText.stringify(Map.of("a", 1000.0))).isEqualTo("{\"a\":1000}");
    assertThat(JsonText.stringify(List.of(Double.NaN, 1e21, 0.1))).isEqualTo("[null,1e+21,0.1]");
  }

  @Test
  void escapes_like_javascript() {
    assertThat(JsonText.quote("a\"b\\c\nd\u0001é/\uD800")).isEqualTo("\"a\\\"b\\\\c\\nd\\u0001é/\\ud800\"");
  }

  @Test
  void sorts_keys_recursively_when_asked() {
    Map<String, Object> inner = new LinkedHashMap<>();
    inner.put("b", 1);
    inner.put("a", 2);
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("z", List.of(inner));
    value.put("//", "first");
    assertThat(JsonText.stringifySorted(value, 0)).isEqualTo("{\"//\":\"first\",\"z\":[{\"a\":2,\"b\":1}]}");
  }

  record Sample(String name, int count) {}

  @Test
  void converts_other_objects_to_raw_values() {
    assertThat(JsonText.stringify(new Sample("x", 2))).isEqualTo("{\"name\":\"x\",\"count\":2}");
  }
}
