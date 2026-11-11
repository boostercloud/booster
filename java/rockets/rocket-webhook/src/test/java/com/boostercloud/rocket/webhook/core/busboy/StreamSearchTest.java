package com.boostercloud.rocket.webhook.core.busboy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** {@link StreamSearch}: matches and the data between them do not depend on how the haystack is chunked. */
class StreamSearchTest {

  /** Collects the callback into "data|MATCH|data..." segments. */
  private static final class Collector implements StreamSearch.Callback {
    final List<String> segments = new ArrayList<>();
    private final ByteArrayOutputStream current = new ByteArrayOutputStream();

    @Override
    public void onData(boolean isMatch, byte[] data, int start, int end, boolean isDataSafe) {
      if (data != null) {
        current.write(data, start, end - start);
      }
      if (isMatch) {
        segments.add(current.toString(StandardCharsets.ISO_8859_1));
        current.reset();
      }
    }

    List<String> finish() {
      List<String> all = new ArrayList<>(segments);
      all.add(current.toString(StandardCharsets.ISO_8859_1));
      return all;
    }
  }

  private static List<String> search(String needle, String haystack, int[] cuts) {
    Collector collector = new Collector();
    StreamSearch search = new StreamSearch(needle.getBytes(StandardCharsets.ISO_8859_1), collector);
    byte[] bytes = haystack.getBytes(StandardCharsets.ISO_8859_1);
    int from = 0;
    for (int cut : cuts) {
      search.push(Arrays.copyOfRange(bytes, from, cut), 0);
      from = cut;
    }
    search.push(Arrays.copyOfRange(bytes, from, bytes.length), 0);
    search.destroy();
    return collector.finish();
  }

  private static List<String> expected(String needle, String haystack) {
    return List.of(haystack.split(java.util.regex.Pattern.quote(needle), -1));
  }

  @Test
  void finds_every_occurrence_in_one_chunk() {
    assertThat(search("\r\n--b", "a\r\n--bc\r\n--b\r\n--", new int[0]))
        .containsExactly("a", "c", "\r\n--");
  }

  @Test
  void finds_occurrences_split_across_chunks() {
    String haystack = "xx\r\n--boundaryyy\r\n--boundary--";
    for (int cut = 1; cut < haystack.length(); cut++) {
      assertThat(search("\r\n--boundary", haystack, new int[] {cut})).as("cut at %d", cut)
          .containsExactly("xx", "yy", "--");
    }
  }

  @Test
  void a_trailing_needle_prefix_is_flushed_by_destroy() {
    assertThat(search("\r\n--boundary", "data\r\n--bou", new int[] {5})).containsExactly("data\r\n--bou");
  }

  @Test
  void counts_matches_and_resets() {
    Collector collector = new Collector();
    StreamSearch search = new StreamSearch("ab", collector);
    search.push("xabyab".getBytes(StandardCharsets.US_ASCII), 0);
    assertThat(search.matches()).isEqualTo(2);
    search.reset();
    assertThat(search.matches()).isZero();
  }

  @Test
  void stops_at_max_matches() {
    Collector collector = new Collector();
    StreamSearch search = new StreamSearch("ab", collector);
    search.maxMatches(1);
    int stopped = search.push("xabyab".getBytes(StandardCharsets.US_ASCII), 0);
    assertThat(stopped).isEqualTo(3);
    assertThat(collector.segments).containsExactly("x");
  }

  @Test
  void requires_a_callback() {
    assertThatThrownBy(() -> new StreamSearch("x", null))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("Missing match callback");
  }

  @Test
  void random_haystacks_and_chunkings_give_the_same_segments() {
    Random random = new Random(42);
    String alphabet = "\r\n-ab";
    for (int round = 0; round < 3000; round++) {
      String needle = "\r\n--" + (random.nextBoolean() ? "ab" : "a");
      StringBuilder haystack = new StringBuilder();
      int length = random.nextInt(60);
      for (int i = 0; i < length; i++) {
        if (random.nextInt(8) == 0) {
          haystack.append(needle);
        } else {
          haystack.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
      }
      String text = haystack.toString();
      int cutCount = text.isEmpty() ? 0 : random.nextInt(Math.min(6, text.length()) + 1);
      int[] cuts = random.ints(cutCount, 0, text.length() + 1).sorted().toArray();
      assertThat(search(needle, text, cuts)).as("needle %s, haystack %s, cuts %s", needle.replace("\r\n", "CRLF"),
          text.replace("\r", "\\r").replace("\n", "\\n"), Arrays.toString(cuts)).isEqualTo(expected(needle, text));
    }
  }

  @Test
  void compares_bytes_unsigned() {
    String needle = "\u00ff\u00fe";
    assertThat(search(needle, "a\u00ff\u00feb\u00ff", new int[] {2})).containsExactly("a", "b\u00ff");
  }
}
