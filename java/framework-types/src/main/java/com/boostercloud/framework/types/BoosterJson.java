package com.boostercloud.framework.types;

import com.boostercloud.framework.types.errors.BoosterError;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * The single shared Jackson {@link ObjectMapper} of Booster, and helpers around it. Every module uses
 * {@link #mapper()} so persisted documents and wire payloads have one shape, the shape the TypeScript implementation
 * produces with {@code JSON.stringify}:
 *
 * <ul>
 *   <li>absent values ({@code null} properties and empty {@code Optional}s) are omitted, like {@code undefined};
 *   <li>unknown properties are ignored when reading;
 *   <li>objects are serialized from their fields (any visibility, {@code transient} excluded), not from getters,
 *       like {@code JSON.stringify} serializes own properties; records use their components;
 *   <li>{@link Instant}, {@link Date}, {@link OffsetDateTime} and {@link ZonedDateTime} are written as
 *       {@code 2024-01-31T10:20:30.123Z} (see {@link Dates}); instants are read from ISO strings (any precision or
 *       offset) or epoch milliseconds;
 *   <li>{@code double}/{@code float} are written like JavaScript numbers: {@code 2.0} becomes {@code 2}, non-finite
 *       values become {@code null};
 *   <li>a {@link BoosterError} is written as {@code {"data": ..., "code": ...}} (TypeScript's enumerable properties)
 *       and any other {@link Throwable} as {@code {"message": ...}}.
 * </ul>
 *
 * <p>"Raw values" are untyped JSON trees: {@code Map<String, Object>} (insertion ordered), {@code List<Object>},
 * {@code String}, {@code Number}, {@code Boolean} and {@code null}. {@link #toRaw(Object)} turns any object into one.
 */
public final class BoosterJson {

  private static final ObjectMapper MAPPER = createMapper();

  private BoosterJson() {}

  /**
   * @return the shared, fully configured mapper (thread-safe; do not reconfigure it)
   */
  public static ObjectMapper mapper() {
    return MAPPER;
  }

  /**
   * Creates a new mapper with the Booster configuration, for the rare code that needs its own instance.
   *
   * @return a new mapper
   */
  public static ObjectMapper createMapper() {
    SimpleModule booster = new SimpleModule("booster");
    booster.addSerializer(Instant.class, new InstantSerializer());
    booster.addSerializer(Date.class, new DateSerializer());
    booster.addSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer());
    booster.addSerializer(ZonedDateTime.class, new ZonedDateTimeSerializer());
    booster.addDeserializer(Instant.class, new InstantDeserializer());
    booster.addSerializer(Double.class, new JsNumberSerializer());
    booster.addSerializer(double.class, new JsNumberSerializer());
    booster.addSerializer(Float.class, new JsFloatSerializer());
    booster.addSerializer(float.class, new JsFloatSerializer());
    booster.addSerializer(BoosterError.class, new BoosterErrorSerializer());
    booster.addSerializer(Throwable.class, new ThrowableSerializer());
    return JsonMapper.builder()
        .addModule(new Jdk8Module())
        .addModule(new JavaTimeModule())
        .addModule(booster)
        .defaultPropertyInclusion(
            JsonInclude.Value.construct(JsonInclude.Include.NON_ABSENT, JsonInclude.Include.USE_DEFAULTS))
        .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        .visibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
        .visibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        .build();
  }

  /**
   * {@code JSON.stringify(value)}.
   *
   * @param value any value
   * @return its JSON text
   */
  public static String toJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException("Could not serialize value to JSON: " + e.getOriginalMessage(), e);
    }
  }

  /**
   * @param value any value
   * @return its JSON text, indented
   */
  public static String toPrettyJson(Object value) {
    try {
      return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException("Could not serialize value to JSON: " + e.getOriginalMessage(), e);
    }
  }

  /**
   * {@code JSON.parse(json)}: reads JSON into a raw value.
   *
   * @param json JSON text
   * @return a raw value (map, list, string, number, boolean or null)
   */
  public static Object parse(String json) {
    return fromJson(json, Object.class);
  }

  /**
   * @param json JSON text of an object
   * @return the raw object
   */
  public static Map<String, Object> parseObject(String json) {
    return fromJson(json, new TypeReference<>() {});
  }

  /**
   * @param json JSON text
   * @param type the target class
   * @param <T> the target type
   * @return the parsed value
   */
  public static <T> T fromJson(String json, Class<T> type) {
    try {
      return MAPPER.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException("Could not parse JSON: " + e.getOriginalMessage(), e);
    }
  }

  /**
   * @param json JSON text
   * @param type the target type
   * @param <T> the target type
   * @return the parsed value
   */
  public static <T> T fromJson(String json, TypeReference<T> type) {
    try {
      return MAPPER.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException("Could not parse JSON: " + e.getOriginalMessage(), e);
    }
  }

  /**
   * Converts any object (instances, records, envelopes) into a raw value, as a JSON round trip would.
   *
   * @param value any value
   * @return its raw representation
   */
  public static Object toRaw(Object value) {
    if (value == null || value instanceof String || value instanceof Boolean) {
      return value;
    }
    try {
      // A real JSON round trip, so numbers come back as JSON.parse would give them (2.0 -> 2, Long 3 -> Integer 3)
      return MAPPER.readValue(MAPPER.writeValueAsBytes(value), Object.class);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not convert value to a raw JSON value: " + e.getMessage(), e);
    }
  }

  /**
   * Converts an object into a raw JSON object.
   *
   * @param value an object (instance, record, map)
   * @return its raw representation as an ordered map
   * @throws IllegalArgumentException when the value does not serialize to a JSON object
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> toRawMap(Object value) {
    Object raw = toRaw(value);
    if (raw instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    throw new IllegalArgumentException("Expected a JSON object but got " + (raw == null ? "null" : raw.getClass().getName()));
  }

  /**
   * @param raw a raw list or any value that serializes to a JSON array
   * @return the raw list
   */
  @SuppressWarnings("unchecked")
  public static List<Object> toRawList(Object raw) {
    Object converted = toRaw(raw);
    if (converted instanceof List<?> list) {
      return (List<Object>) list;
    }
    throw new IllegalArgumentException("Expected a JSON array but got "
        + (converted == null ? "null" : converted.getClass().getName()));
  }

  /**
   * Converts a value (typically a raw value) into the given class with Jackson.
   *
   * @param value the value
   * @param type the target class
   * @param <T> the target type
   * @return the converted value
   */
  public static <T> T convert(Object value, Class<T> type) {
    return MAPPER.convertValue(value, type);
  }

  /**
   * Converts a value into the given reflective type with Jackson.
   *
   * @param value the value
   * @param type the target type (may be generic)
   * @param <T> the target type
   * @return the converted value
   */
  public static <T> T convert(Object value, Type type) {
    JavaType javaType = MAPPER.getTypeFactory().constructType(type);
    return MAPPER.convertValue(value, javaType);
  }

  /**
   * @param value the value
   * @param type the target type
   * @param <T> the target type
   * @return the converted value
   */
  public static <T> T convert(Object value, TypeReference<T> type) {
    return MAPPER.convertValue(value, type);
  }

  /**
   * Formats a number like JavaScript's {@code Number.prototype.toString}: integral values without a fraction
   * ({@code 2}), plain notation between {@code 1e-6} and {@code 1e21}, exponent notation ({@code 1e+21},
   * {@code 1.5e-7}) outside, and {@code NaN}/{@code Infinity}.
   *
   * @param value a number
   * @return its JavaScript text
   */
  public static String formatNumber(double value) {
    if (Double.isNaN(value)) {
      return "NaN";
    }
    if (Double.isInfinite(value)) {
      return value > 0 ? "Infinity" : "-Infinity";
    }
    double abs = Math.abs(value);
    if (value == Math.rint(value) && abs < 1e21) {
      return new BigDecimal(value).toBigInteger().toString();
    }
    String java = Double.toString(value);
    if (abs >= 1e-6 && abs < 1e21) {
      return new BigDecimal(java).toPlainString();
    }
    int exponentIndex = java.indexOf('E');
    if (exponentIndex < 0) {
      return java;
    }
    String mantissa = java.substring(0, exponentIndex);
    if (mantissa.endsWith(".0")) {
      mantissa = mantissa.substring(0, mantissa.length() - 2);
    }
    String exponent = java.substring(exponentIndex + 1);
    return mantissa + "e" + (exponent.startsWith("-") ? exponent : "+" + exponent);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Serializers
  // ---------------------------------------------------------------------------------------------------------------

  private static final class InstantSerializer extends JsonSerializer<Instant> {
    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      gen.writeString(Dates.toIso(value));
    }
  }

  private static final class DateSerializer extends JsonSerializer<Date> {
    @Override
    public void serialize(Date value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      gen.writeString(Dates.toIso(value.getTime()));
    }
  }

  private static final class OffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime> {
    @Override
    public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      gen.writeString(Dates.toIso(value.toInstant()));
    }
  }

  private static final class ZonedDateTimeSerializer extends JsonSerializer<ZonedDateTime> {
    @Override
    public void serialize(ZonedDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      gen.writeString(Dates.toIso(value.withZoneSameInstant(ZoneOffset.UTC).toInstant()));
    }
  }

  private static final class InstantDeserializer extends JsonDeserializer<Instant> {
    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
      JsonToken token = parser.currentToken();
      if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
        return Instant.ofEpochMilli(parser.getValueAsLong());
      }
      if (token == JsonToken.VALUE_STRING) {
        String text = parser.getText();
        if (text.isBlank()) {
          return null;
        }
        try {
          return Dates.parse(text);
        } catch (IllegalArgumentException e) {
          return (Instant) context.handleWeirdStringValue(Instant.class, text, e.getMessage());
        }
      }
      return (Instant) context.handleUnexpectedToken(Instant.class, parser);
    }
  }

  private static final class JsNumberSerializer extends JsonSerializer<Double> {
    @Override
    public void serialize(Double value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      writeJsNumber(value, gen);
    }
  }

  private static final class JsFloatSerializer extends JsonSerializer<Float> {
    @Override
    public void serialize(Float value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      // Go through the decimal text so 0.1f is written as 0.1 and not 0.10000000149011612
      writeJsNumber(Double.parseDouble(Float.toString(value)), gen);
    }
  }

  private static void writeJsNumber(double value, JsonGenerator gen) throws IOException {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      gen.writeNull();
    } else if (value == Math.rint(value) && Math.abs(value) < 1e21) {
      if (Math.abs(value) < Long.MAX_VALUE) {
        gen.writeNumber((long) value);
      } else {
        gen.writeNumber(new BigDecimal(value).toBigInteger());
      }
    } else {
      gen.writeNumber(value);
    }
  }

  private static final class BoosterErrorSerializer extends JsonSerializer<BoosterError> {
    @Override
    public void serialize(BoosterError value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      gen.writeStartObject();
      if (value.data() != null) {
        gen.writeFieldName("data");
        serializers.defaultSerializeValue(value.data(), gen);
      }
      gen.writeStringField("code", value.code());
      gen.writeEndObject();
    }
  }

  private static final class ThrowableSerializer extends JsonSerializer<Throwable> {
    @Override
    public void serialize(Throwable value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      gen.writeStartObject();
      if (value.getMessage() != null) {
        gen.writeStringField("message", value.getMessage());
      }
      gen.writeEndObject();
    }
  }
}
