package com.boostercloud.application.tester.graphql;

import com.boostercloud.framework.types.BoosterJson;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of a GraphQL operation: Apollo's {@code ApolloQueryResult} / {@code FetchResult}. {@code data} is the
 * raw {@code data} object of the response ({@code Map}/{@code List}/{@code String}/{@code Number}/{@code Boolean}
 * trees, insertion ordered, see ARCHITECTURE §4), so {@code response.data.CartReadModel.id} is
 * {@code response.get("CartReadModel.id")}.
 *
 * <p>{@code errors} is empty unless the operation used {@link ErrorPolicy#ALL} (with the default policy a response
 * with errors makes the operation throw {@link GraphQLClientException}); subscription results never carry errors
 * either (they are delivered as errors to the observer).
 *
 * @param data the {@code data} object, {@code null} when the server sent none
 * @param errors the GraphQL errors kept by the error policy
 * @param extensions the {@code extensions} object of the response, empty when absent
 */
public record GraphQLResponse(Map<String, Object> data, List<GraphQLError> errors, Map<String, Object> extensions) {

  /** Canonical constructor; the collections are made unmodifiable, {@code null} lists/maps become empty. */
  public GraphQLResponse {
    data = data == null ? null : Collections.unmodifiableMap(data);
    errors = errors == null ? List.of() : List.copyOf(errors);
    extensions = extensions == null ? Map.of() : Collections.unmodifiableMap(extensions);
  }

  /**
   * Reads a raw execution result ({@code {data, errors, extensions}}).
   *
   * @param raw the parsed response body or websocket payload
   * @return the response; anything that is not an object yields an empty response
   */
  @SuppressWarnings("unchecked")
  public static GraphQLResponse fromResult(Object raw) {
    if (!(raw instanceof Map<?, ?> map)) {
      return new GraphQLResponse(null, List.of(), Map.of());
    }
    Map<String, Object> data = map.get("data") instanceof Map<?, ?> d ? (Map<String, Object>) d : null;
    List<GraphQLError> errors = new ArrayList<>();
    if (map.get("errors") instanceof List<?> list) {
      list.forEach(entry -> errors.add(GraphQLError.of(entry)));
    }
    Map<String, Object> extensions =
        map.get("extensions") instanceof Map<?, ?> e ? (Map<String, Object>) e : Map.of();
    return new GraphQLResponse(data, errors, extensions);
  }

  /**
   * @return whether {@code errors} is not empty (Apollo's {@code graphQLResultHasError})
   */
  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  /**
   * @return this response without errors ({@link ErrorPolicy#IGNORE})
   */
  public GraphQLResponse withoutErrors() {
    return new GraphQLResponse(data, List.of(), extensions);
  }

  /**
   * Navigates {@code data}: {@code get("CartReadModel.cartItems[0].quantity")}.
   *
   * @param path property names separated by dots, each optionally followed by {@code [index]} list accessors
   * @return the raw value, or {@code null} when any step is missing
   * @throws IllegalArgumentException when the path is malformed
   */
  public Object get(String path) {
    Object current = data;
    for (Object step : parsePath(path)) {
      if (step instanceof String key && current instanceof Map<?, ?> map) {
        current = map.get(key);
      } else if (step instanceof Integer index && current instanceof List<?> list) {
        current = index >= 0 && index < list.size() ? list.get(index) : null;
      } else {
        current = null;
      }
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  /**
   * @param path see {@link #get(String)}
   * @param type the target class (records, classes, {@code Map}, ...)
   * @param <T> the target type
   * @return the value converted with {@link BoosterJson#convert(Object, Class)}, or {@code null}
   */
  public <T> T get(String path, Class<T> type) {
    Object value = get(path);
    return value == null ? null : BoosterJson.convert(value, type);
  }

  /**
   * @param path see {@link #get(String)}
   * @param type the target type
   * @param <T> the target type
   * @return the value converted with {@link BoosterJson#convert(Object, TypeReference)}, or {@code null}
   */
  public <T> T get(String path, TypeReference<T> type) {
    Object value = get(path);
    return value == null ? null : BoosterJson.convert(value, type);
  }

  /**
   * @return the response as a raw execution result: {@code data} (when present), {@code errors} (when not empty),
   *     {@code extensions} (when not empty)
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<>();
    if (data != null) {
      result.put("data", data);
    }
    if (!errors.isEmpty()) {
      result.put("errors", errors.stream().map(GraphQLError::toMap).toList());
    }
    if (!extensions.isEmpty()) {
      result.put("extensions", extensions);
    }
    return Collections.unmodifiableMap(result);
  }

  private static List<Object> parsePath(String path) {
    if (path == null || path.isEmpty()) {
      throw new IllegalArgumentException("The path must not be empty");
    }
    List<Object> steps = new ArrayList<>();
    for (String segment : path.split("\\.", -1)) {
      int bracket = segment.indexOf('[');
      String name = bracket < 0 ? segment : segment.substring(0, bracket);
      if (!name.isEmpty()) {
        steps.add(name);
      } else if (bracket != 0) {
        throw new IllegalArgumentException("Malformed path: " + path);
      }
      String rest = bracket < 0 ? "" : segment.substring(bracket);
      while (!rest.isEmpty()) {
        int close = rest.indexOf(']');
        if (!rest.startsWith("[") || close < 0) {
          throw new IllegalArgumentException("Malformed path: " + path);
        }
        try {
          steps.add(Integer.parseInt(rest.substring(1, close)));
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("Malformed path: " + path, e);
        }
        rest = rest.substring(close + 1);
      }
    }
    return steps;
  }
}
