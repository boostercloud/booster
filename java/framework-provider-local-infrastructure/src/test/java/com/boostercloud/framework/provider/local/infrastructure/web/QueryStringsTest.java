package com.boostercloud.framework.provider.local.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code qs} 6 defaults reproduced by {@link QueryStrings}. */
class QueryStringsTest {

  @Test
  void flatPairs() {
    assertThat(QueryStrings.parse("a=1&b=two%20words&c=x+y")).isEqualTo(Map.of("a", "1", "b", "two words", "c", "x y"));
    assertThat(QueryStrings.parse("flag&=ignored&&")).isEqualTo(Map.of("flag", ""));
    assertThat(QueryStrings.parse("")).isEmpty();
    assertThat(QueryStrings.parse(null)).isEmpty();
  }

  @Test
  void repeatedKeysBecomeArrays() {
    assertThat(QueryStrings.parse("a=1&a=2&a=3")).isEqualTo(Map.of("a", List.of("1", "2", "3")));
  }

  @Test
  void bracketsNest() {
    assertThat(QueryStrings.parse("a[b][c]=1&a[d]=2")).isEqualTo(Map.of("a", Map.of("b", Map.of("c", "1"), "d", "2")));
    assertThat(QueryStrings.parse("a[]=1&a[]=2")).isEqualTo(Map.of("a", List.of("1", "2")));
    assertThat(QueryStrings.parse("a[1]=b&a[0]=a")).isEqualTo(Map.of("a", List.of("a", "b")));
    assertThat(QueryStrings.parse("a[21]=x")).isEqualTo(Map.of("a", Map.of("21", "x")));
  }

  @Test
  void depthIsLimitedToFiveLevels() {
    assertThat(QueryStrings.parse("a[b][c][d][e][f][g]=1")).isEqualTo(
        Map.of("a", Map.of("b", Map.of("c", Map.of("d", Map.of("e", Map.of("f", Map.of("[g]", "1"))))))));
  }

  @Test
  void malformedEscapesAreKept() {
    assertThat(QueryStrings.parse("a=%E0%A4%A&b=%zz")).isEqualTo(Map.of("a", "%E0%A4%A", "b", "%zz"));
  }

  @Test
  void parameterCount() {
    assertThat(QueryStrings.parameterCount("a=1&b=2", 1000)).isEqualTo(1);
    assertThat(QueryStrings.parameterCount("a&".repeat(1000), 1000)).isEqualTo(-1);
  }
}
