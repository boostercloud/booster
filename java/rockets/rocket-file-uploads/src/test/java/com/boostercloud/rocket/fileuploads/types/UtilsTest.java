package com.boostercloud.rocket.fileuploads.types;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code rocket-file-uploads-types/test/utils.test.ts} ({@code context('checkDirectory')}). */
@DisplayName("checkDirectory")
class UtilsTest {

  private static boolean isValidDirectory(String source, String... allowed) {
    return Utils.isValidDirectory(source, List.of(allowed));
  }

  @Test
  void invalid_empty_source() {
    assertThat(isValidDirectory("")).isFalse();
  }

  @Nested
  class with_exact_match {
    @Test
    void valid_exact_root() {
      assertThat(isValidDirectory("exact", "exact")).isTrue();
    }

    @Test
    void invalid_extra_slash() {
      assertThat(isValidDirectory("/exact", "exact")).isFalse();
    }

    @Test
    void valid_exact_root_with_ending_slash() {
      assertThat(isValidDirectory("exact/", "exact")).isTrue();
    }

    @Test
    void valid_exact_root_subdirectory() {
      assertThat(isValidDirectory("exact/other", "exact/other")).isTrue();
    }

    @Test
    void valid_exact_root_subdirectory_with_ending_slash() {
      assertThat(isValidDirectory("exact/other/", "exact/other")).isTrue();
    }
  }

  @Nested
  class with_starts {
    @Nested
    class only_one_star {
      @Test
      void valid_any_root() {
        assertThat(isValidDirectory("exact", "*")).isTrue();
      }

      @Test
      void valid_any_root_with_ending_slash() {
        assertThat(isValidDirectory("exact/", "*")).isTrue();
      }

      @Test
      void invalid_for_root_with_subdirectories() {
        assertThat(isValidDirectory("exact/other", "*")).isFalse();
      }
    }

    @Nested
    class one_star_and_one_subdirectory {
      @Test
      void valid_any_root_and_the_exact_subdirectory() {
        assertThat(isValidDirectory("exact/test", "*/test")).isTrue();
      }

      @Test
      void invalid_any_root_and_the_wrong_subdirectory() {
        assertThat(isValidDirectory("exact/fail", "*/test")).isFalse();
      }
    }

    @Nested
    class one_start_in_the_last_subdirectory {
      @Test
      void invalid_wrong_root() {
        assertThat(isValidDirectory("fail/pass", "test/*")).isFalse();
      }

      @Test
      void valid_the_exact_root_and_any_subdirectory() {
        assertThat(isValidDirectory("test/pass", "test/*")).isTrue();
      }

      @Test
      void invalid_the_exact_root_and_more_subdirectories_than_expected() {
        assertThat(isValidDirectory("test/pass/fail", "test/*")).isFalse();
      }
    }
  }

  @Nested
  class with_GlobStart {
    @Nested
    class only_one_GlobStart {
      @Test
      void valid_any_root() {
        assertThat(isValidDirectory("exact", "**")).isTrue();
      }
    }

    @Nested
    class only_one_GlobStart_at_the_end {
      @Test
      void valid_the_exact_root_directory_and_any_number_of_subdirectories() {
        assertThat(isValidDirectory("exact", "exact/**")).isFalse();
        assertThat(isValidDirectory("other", "exact/**")).isFalse();
        assertThat(isValidDirectory("exact/", "exact/**")).isTrue();
        assertThat(isValidDirectory("exact/1", "exact/**")).isTrue();
        assertThat(isValidDirectory("exact/1/2", "exact/**")).isTrue();
        assertThat(isValidDirectory("exact/1/2/", "exact/**")).isTrue();
      }
    }

    @Nested
    class only_one_GlobStart_in_the_middle {
      @Test
      void valid_the_exact_root_directory_and_any_number_of_subdirectories_ending_in_the_expected_one() {
        assertThat(isValidDirectory("fail", "main/**/other")).isFalse();
        assertThat(isValidDirectory("fail/other", "main/**/other")).isFalse();
        assertThat(isValidDirectory("main/other", "main/**/other")).isTrue();
        assertThat(isValidDirectory("main/other/", "main/**/other")).isTrue();
        assertThat(isValidDirectory("main/1/other", "main/**/other")).isTrue();
        assertThat(isValidDirectory("main/1/2/other", "main/**/other")).isTrue();
        assertThat(isValidDirectory("main/1/2/3/other", "main/**/other")).isTrue();
        assertThat(isValidDirectory("main/1/2/3/other/", "main/**/other")).isTrue();
        assertThat(isValidDirectory("main/1/2/3/other/1", "main/**/other")).isFalse();
      }
    }

    @Nested
    class more_than_one_GlobStart {
      @Test
      void valid_only_if_subdirectories_match() {
        assertThat(isValidDirectory("fail/sub", "deep/**/sub/**")).isFalse();
        assertThat(isValidDirectory("deep/fail", "deep/**/sub/**")).isFalse();
        assertThat(isValidDirectory("deep/sub", "deep/**/sub/**")).isFalse();
        assertThat(isValidDirectory("deep/sub/", "deep/**/sub/**")).isTrue();
        assertThat(isValidDirectory("deep/1/sub/1", "deep/**/sub/**")).isTrue();
        assertThat(isValidDirectory("deep/1/sub/1/", "deep/**/sub/**")).isTrue();
        assertThat(isValidDirectory("deep/1/fail/1", "deep/**/sub/**")).isFalse();
        assertThat(isValidDirectory("fail/1/sub/1", "deep/**/sub/**")).isFalse();
        assertThat(isValidDirectory("deep/1/2/3/sub/1/2/3", "deep/**/sub/**")).isTrue();
        assertThat(isValidDirectory("deep/1/2/3/sub/1/2/3/", "deep/**/sub/**")).isTrue();
      }
    }
  }

  @Test
  void any_matching_pattern_is_enough() {
    assertThat(isValidDirectory("client2", "client1", "client2")).isTrue();
    assertThat(isValidDirectory("client3", "client1", "client2")).isFalse();
  }
}
