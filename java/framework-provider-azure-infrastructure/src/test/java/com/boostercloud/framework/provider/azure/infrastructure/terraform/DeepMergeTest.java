package com.boostercloud.framework.provider.azure.infrastructure.terraform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeepMergeTest {

  @Test
  void merges_objects_and_replaces_other_values() {
    Map<String, Object> target = new LinkedHashMap<>(Map.of("a", new LinkedHashMap<>(Map.of("x", 1, "y", 2)),
        "list", new ArrayList<>(List.of(1, 2))));
    Map<String, Object> source = Map.of("a", Map.of("y", 3), "list", List.of(9), "b", "new");
    DeepMerge.deepMerge(target, source);
    assertThat(target).isEqualTo(Map.of("a", Map.of("x", 1, "y", 3), "list", List.of(9), "b", "new"));
  }

  @Test
  void indexes_lists_with_numeric_keys_like_add_override() {
    Map<String, Object> pathMap = new LinkedHashMap<>(Map.of("name", "main", "path_rule", List.of("old")));
    Map<String, Object> target = new LinkedHashMap<>();
    target.put("url_path_map", new ArrayList<>(List.of(pathMap)));
    Map<String, Object> overrides = new LinkedHashMap<>();
    DeepMerge.addOverride(overrides, "url_path_map.0.path_rule", List.of(Map.of("name", "rule")));
    assertThat(overrides).isEqualTo(Map.of("url_path_map", Map.of("0", Map.of("path_rule",
        List.of(Map.of("name", "rule"))))));
    DeepMerge.deepMerge(target, overrides);
    assertThat(target).isEqualTo(Map.of("url_path_map", List.of(Map.of("name", "main", "path_rule",
        List.of(Map.of("name", "rule"))))));
  }

  @Test
  void add_override_replaces_non_object_intermediate_values() {
    Map<String, Object> overrides = new LinkedHashMap<>();
    DeepMerge.addOverride(overrides, "a", "scalar");
    DeepMerge.addOverride(overrides, "a.b", 1);
    assertThat(overrides).isEqualTo(Map.of("a", Map.of("b", 1)));
  }

  @Test
  void deletes_objects_left_empty() {
    Map<String, Object> target = new LinkedHashMap<>();
    DeepMerge.deepMerge(target, Map.of("empty", Map.of()));
    assertThat(target).isEmpty();
  }
}
