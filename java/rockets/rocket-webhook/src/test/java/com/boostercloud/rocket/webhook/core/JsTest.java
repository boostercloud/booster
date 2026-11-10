package com.boostercloud.rocket.webhook.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The JavaScript truthiness and interpolation rules the rocket's branches depend on. */
class JsTest {

  @Test
  void falsy_values_are_the_javascript_ones() {
    for (Object falsy : new Object[] {null, false, 0, 0L, 0.0, -0.0, 0f, Double.NaN, ""}) {
      assertThat(Js.isTruthy(falsy)).as("%s", falsy).isFalse();
    }
    for (Object truthy : new Object[] {true, 1, -1, 0.5, "0", "false", " ", Map.of(), List.of(), new Object()}) {
      assertThat(Js.isTruthy(truthy)).as("%s", truthy).isTrue();
    }
  }

  @Test
  void interpolation_prints_null_as_undefined_and_numbers_like_javascript() {
    assertThat(Js.string(null)).isEqualTo("undefined");
    assertThat(Js.string(2.0)).isEqualTo("2");
    assertThat(Js.string(1.5f)).isEqualTo("1.5");
    assertThat(Js.string(7)).isEqualTo("7");
    assertThat(Js.string("GET")).isEqualTo("GET");
  }
}
