package com.boostercloud.framework.provider.azure.helpers;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests of the JavaScript semantics helpers. */
class JsValuesTest {

  record Sample(String a, Integer b) {}

  @Test
  void truthiness() {
    assertThat(JsValues.truthy(null)).isFalse();
    assertThat(JsValues.truthy(0)).isFalse();
    assertThat(JsValues.truthy(Double.NaN)).isFalse();
    assertThat(JsValues.truthy("")).isFalse();
    assertThat(JsValues.truthy(false)).isFalse();
    assertThat(JsValues.truthy(obj())).isTrue();
    assertThat(JsValues.truthy(List.of())).isTrue();
    assertThat(JsValues.truthy("0")).isTrue();
  }

  @Test
  void parseIntParsesTheIntegerPrefix() {
    assertThat(JsValues.parseInt("12abc")).isEqualTo(12);
    assertThat(JsValues.parseInt("  -3")).isEqualTo(-3);
    assertThat(JsValues.parseInt("abc")).isNaN();
    assertThat(JsValues.parseInt(5)).isEqualTo(5);
    assertThat(JsValues.parseInt(1.9)).isEqualTo(1);
    assertThat(JsValues.parseInt(null)).isNaN();
  }

  @Test
  void stringConversionsFollowJavaScript() {
    assertThat(JsValues.jsString(obj("a", 1))).isEqualTo("[object Object]");
    assertThat(JsValues.jsString(List.of(1, "b", obj()))).isEqualTo("1,b,[object Object]");
    assertThat(JsValues.jsString(2.0)).isEqualTo("2");
    assertThat(JsValues.jsString(new Sample("x", 1))).isEqualTo("[object Object]");
    assertThat(JsValues.stringify(null)).isEqualTo("undefined");
    assertThat(JsValues.stringify(obj("a", null))).isEqualTo("{\"a\":null}");
  }

  @Test
  void structuralPropertyAccessWorksOnMapsAndRecords() {
    assertThat(JsValues.has(obj("a", null), "a")).isTrue();
    assertThat(JsValues.has(new Sample(null, 1), "a")).isTrue();
    assertThat(JsValues.has(new Sample(null, 1), "c")).isFalse();
    assertThat(JsValues.get(new Sample("x", 1), "b")).isEqualTo(1);
    assertThat(JsValues.get("text", "length")).isNull();
  }

  @Test
  void numberConversionFollowsNumberOfString() {
    assertThat(JsNumbers.number("")).isZero();
    assertThat(JsNumbers.number(" 5 ")).isEqualTo(5);
    assertThat(JsNumbers.number("1e3")).isEqualTo(1000);
    assertThat(JsNumbers.number("0x10")).isEqualTo(16);
    assertThat(JsNumbers.number("5d")).isNaN();
    assertThat(JsNumbers.number("abc")).isNaN();
    assertThat(JsNumbers.number("-Infinity")).isNegative().isInfinite();
  }
}
