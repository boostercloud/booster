package com.boostercloud.framework.core.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.errors.NotFoundError;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** JavaScript truthiness and string conversion ({@link Js}). */
class JsTest {

  @Test
  void truthiness() {
    for (Object falsy : Arrays.asList(null, false, 0, 0L, 0.0, Double.NaN, "", UUID.of(""))) {
      assertThat(Js.isTruthy(falsy)).as(String.valueOf(falsy)).isFalse();
    }
    for (Object truthy : List.of(true, 1, -1.5, "a", UUID.of("a"), List.of(), Map.of(), new Object())) {
      assertThat(Js.isTruthy(truthy)).as(String.valueOf(truthy)).isTrue();
    }
  }

  @Test
  void stringConversion() {
    assertThat(Js.toString(null)).isEqualTo("null");
    assertThat(Js.toString(123)).isEqualTo("123");
    assertThat(Js.toString(123.0)).isEqualTo("123");
    assertThat(Js.toString(1.5)).isEqualTo("1.5");
    assertThat(Js.toString(List.of(1, "a"))).isEqualTo("1,a");
    assertThat(Js.toString(Map.of("a", 1))).isEqualTo("[object Object]");
  }

  @Test
  void errorText() {
    assertThat(Js.errorToString(new IllegalStateException("test"))).isEqualTo("Error: test");
    assertThat(Js.errorToString(new IllegalArgumentException(""))).isEqualTo("Error");
    assertThat(Js.errorToString(new NotFoundError("missing"))).isEqualTo("NotFoundError: missing");
    assertThat(Js.errorToString(null)).isEqualTo("undefined");
  }
}
