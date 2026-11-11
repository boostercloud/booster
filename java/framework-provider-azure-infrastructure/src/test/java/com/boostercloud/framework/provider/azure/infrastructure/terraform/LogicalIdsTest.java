package com.boostercloud.framework.provider.azure.infrastructure.terraform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class LogicalIdsTest {

  @Test
  void keeps_the_id_of_a_direct_child_of_the_stack() {
    assertThat(LogicalIds.makeUniqueId(List.of("mystoreproductionrg"), true)).isEqualTo("mystoreproductionrg");
    assertThat(LogicalIds.makeUniqueId(List.of("my-store_x.y"), true)).isEqualTo("my-store_xy");
    assertThat(LogicalIds.makeUniqueId(List.of("my-store_x.y"), false)).isEqualTo("mystorexy");
  }

  @Test
  void hashes_nested_paths() throws Exception {
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
        .digest("azureFeature/httpURL".getBytes(StandardCharsets.UTF_8))).substring(0, 8).toUpperCase(Locale.ROOT);
    assertThat(LogicalIds.makeUniqueId(List.of("azureFeature", "httpURL"), true))
        .isEqualTo("azureFeaturehttpURL_" + hash)
        .isEqualTo("azureFeaturehttpURL_8AD6F458");
  }

  @Test
  void skips_default_and_hides_resource_from_the_human_part() {
    assertThat(LogicalIds.makeUniqueId(List.of("a", "Default"), true)).isEqualTo("a");
    String id = LogicalIds.makeUniqueId(List.of("bucket", "Resource"), true);
    assertThat(id).startsWith("bucket_").hasSize("bucket_".length() + 8);
    assertThat(id).isEqualTo("bucket_" + LogicalIds.pathHash(List.of("bucket", "Resource")));
  }

  @Test
  void removes_duplicated_suffixes() {
    assertThat(LogicalIds.makeUniqueId(List.of("stackbucket", "bucket"), true))
        .startsWith("stackbucket_");
  }

  @Test
  void hashes_ids_longer_than_255_characters() {
    String longId = "a".repeat(300);
    String id = LogicalIds.makeUniqueId(List.of(longId), true);
    assertThat(id).isEqualTo("a".repeat(240) + "_" + LogicalIds.pathHash(List.of(longId)));
  }

  @Test
  void rejects_empty_components() {
    assertThatThrownBy(() -> LogicalIds.makeUniqueId(List.of("Default"), true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unable to calculate a unique id for an empty set of components");
  }
}
