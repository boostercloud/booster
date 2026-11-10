package com.boostercloud.framework.provider.azure.library;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.types.concepts.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code test/library/partition-keys.test.ts}. */
class PartitionKeysTest {

  @Nested
  class PartitionKeyForEvent {
    @Test
    void shouldReturnTheCorrectPartitionKeyForAnEvent() {
      assertThat(PartitionKeys.partitionKeyForEvent("User", "123")).isEqualTo("User-123-event");
      assertThat(PartitionKeys.partitionKeyForEvent("User", UUID.of("123"))).isEqualTo("User-123-event");
    }
  }

  @Nested
  class PartitionKeyForSnapshot {
    @Test
    void shouldReturnTheCorrectPartitionKeyForAnEntitySnapshot() {
      assertThat(PartitionKeys.partitionKeyForSnapshot("User", "123")).isEqualTo("User-123-snapshot");
    }
  }

  @Test
  void absentValuesRenderLikeUndefinedInATemplateLiteral() {
    assertThat(PartitionKeys.partitionKeyForEvent(null, null)).isEqualTo("undefined-undefined-event");
  }
}
