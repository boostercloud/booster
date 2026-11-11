package com.boostercloud.framework.provider.local.store;

import static com.boostercloud.framework.provider.local.Raw.list;
import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class SerializationTest {

  @Test
  void writesDocumentsLikeJsonStringify() {
    assertThat(Serialization.serialize(map("a", 1, "b", 2.0, "c", 2.5, "d", null, "e", list(1, Undefined.INSTANCE),
        "f", Undefined.INSTANCE, "g", true, "_id", "x")))
        .isEqualTo("{\"a\":1,\"b\":2,\"c\":2.5,\"d\":null,\"e\":[1,null],\"g\":true,\"_id\":\"x\"}");
  }

  @Test
  void writesDatesAsNedbDates() throws IOException {
    String line = Serialization.serialize(map("when", new NedbDate(1700000000123.0)));
    assertThat(line).isEqualTo("{\"when\":{\"$$date\":1700000000123}}");
    assertThat(Serialization.deserialize(line)).isEqualTo(map("when", new NedbDate(1700000000123.0)));
    assertThat(Serialization.toJson(map("when", new NedbDate(0)))).isEqualTo("{\"when\":\"1970-01-01T00:00:00.000Z\"}");
  }

  @Test
  void readsAnyObjectWithADateMarkerAsADate() throws IOException {
    assertThat(Serialization.deserialize("{\"d\":{\"$$date\":5,\"other\":1}}")).isEqualTo(map("d", new NedbDate(5)));
    assertThat(Serialization.deserialize("{\"d\":{\"$$date\":null}}")).isEqualTo(map("d", new NedbDate(0)));
    NedbDate invalid = (NedbDate) ((java.util.Map<?, ?>) Serialization.deserialize("{\"d\":{\"$$date\":{}}}")).get("d");
    assertThat(invalid.isValid()).isFalse();
    assertThat(Serialization.serialize(map("d", invalid))).isEqualTo("{\"d\":{\"$$date\":null}}");
  }

  @Test
  void escapesTheCharactersNedbSplitsLinesOn() throws IOException {
    String value = "a b c\u0085d😀e\nf\"g";
    String line = Serialization.serialize(map("s", value));
    assertThat(line).doesNotContain(" ", " ", "\u0085", "\n");
    assertThat(line).contains("\\u2028", "\\u2029", "\\u0085", "\\ud83d\\ude00", "\\n", "\\\"");
    assertThat(Serialization.deserialize(line)).isEqualTo(map("s", value));
  }

  @Test
  void loneSurrogatesSurviveARoundTrip() throws IOException {
    String value = "x\uD800y";
    assertThat(Serialization.deserialize(Serialization.serialize(map("s", value)))).isEqualTo(map("s", value));
  }

  @Test
  void readingIsStrictJson() {
    assertThatThrownBy(() -> Serialization.deserialize("{\"a\":1} trailing")).isInstanceOf(IOException.class);
    assertThatThrownBy(() -> Serialization.deserialize("{'a':1}")).isInstanceOf(IOException.class);
    assertThatThrownBy(() -> Serialization.deserialize("{\"a\":NaN}")).isInstanceOf(IOException.class);
  }

  @Test
  void readsNumbersAsJavaScriptWouldCompareThem() throws IOException {
    Object parsed = Serialization.deserialize("{\"i\":1,\"l\":12345678901234,\"d\":1.5}");
    assertThat(Model.areThingsEqual(parsed, map("i", 1.0, "l", 12345678901234.0, "d", 1.5))).isTrue();
  }
}
