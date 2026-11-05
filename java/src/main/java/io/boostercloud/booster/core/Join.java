package io.boostercloud.booster.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.function.Function;

/** Selects stable read-model keys from entity fields or a filter over existing read models. */
@FunctionalInterface
public interface Join<S,R> {
  Collection<String> keys(S entity, Map<String,R> models);
  static <S,R> Join<S,R> key(Function<S,String> key) {
    Objects.requireNonNull(key);
    return (entity, models) -> { var id = key.apply(entity); return id == null ? List.of() : List.of(id); };
  }
  static <S,R> Join<S,R> keys(Function<S,? extends Collection<String>> keys) {
    Objects.requireNonNull(keys);
    return (entity, models) -> { var result = keys.apply(entity); return result == null ? List.of() : List.copyOf(result); };
  }
  static <S,R> Join<S,R> filter(Function<S,JsonNode> filter) {
    Objects.requireNonNull(filter);
    return (entity, models) -> {
      var expression = filter.apply(entity);
      if (expression == null || expression.isNull()) return List.of();
      Filters.matches(Json.MAPPER.createObjectNode(), expression);
      return models.entrySet().stream().filter(entry -> Filters.matches(Json.MAPPER.valueToTree(entry.getValue()), expression))
          .map(Map.Entry::getKey).sorted().toList();
    };
  }
  /** Collision-free tuple key for read models with composite/sequence identities. */
  static String composite(Object... values) {
    if (values == null || values.length == 0 || Arrays.stream(values).anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Composite keys require non-null components");
    return Base64.getUrlEncoder().withoutPadding().encodeToString(Booster.canonical(normalizeNumbers(Json.MAPPER.valueToTree(values)))
        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
  private static JsonNode normalizeNumbers(JsonNode value) {
    if (value.isNumber()) {
      var number = value.decimalValue().stripTrailingZeros();
      if (number.scale() < 0) number = number.setScale(0);
      return com.fasterxml.jackson.databind.node.DecimalNode.valueOf(number);
    }
    if (value.isArray()) {
      var result = Json.MAPPER.createArrayNode(); value.forEach(item -> result.add(normalizeNumbers(item))); return result;
    }
    if (value.isObject()) {
      var result = Json.MAPPER.createObjectNode(); value.fields().forEachRemaining(entry -> result.set(entry.getKey(), normalizeNumbers(entry.getValue()))); return result;
    }
    return value;
  }
}
