package com.boostercloud.application.tester.graphql;

import com.boostercloud.framework.types.BoosterJson;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One entry of the {@code errors} list of a GraphQL response, kept exactly as the server sent it. Apollo Client
 * exposes these raw objects in {@code ApolloError.graphQLErrors}; Booster's tests compare them by deep equality
 * (for example {@code [{message: 'TokenExpiredError: jwt expired\n...', extensions: {code: 'BoosterTokenExpiredError'}}]},
 * which also asserts that there are no {@code locations}/{@code path} keys), so {@link #toMap()} returns the raw
 * entry and {@link #equals(Object)} compares it.
 */
public final class GraphQLError {

  private final Map<String, Object> raw;

  private GraphQLError(Map<String, Object> raw) {
    this.raw = Collections.unmodifiableMap(raw);
  }

  /**
   * @param raw a raw error entry; a non-object entry becomes {@code {"message": String(entry)}}
   * @return the error
   */
  @SuppressWarnings("unchecked")
  public static GraphQLError of(Object raw) {
    if (raw instanceof Map<?, ?> map) {
      return new GraphQLError(new LinkedHashMap<>((Map<String, Object>) map));
    }
    Map<String, Object> wrapped = new LinkedHashMap<>();
    wrapped.put("message", raw == null ? null : Js.string(raw));
    return new GraphQLError(wrapped);
  }

  /**
   * @param message the error message
   * @return an error {@code {"message": message}}
   */
  public static GraphQLError ofMessage(String message) {
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("message", message);
    return new GraphQLError(raw);
  }

  /**
   * @return the {@code message}, or {@code null} when absent or not a string
   */
  public String message() {
    return raw.get("message") instanceof String message ? message : null;
  }

  /**
   * @return the {@code path} (strings and numbers), or {@code null} when absent
   */
  @SuppressWarnings("unchecked")
  public List<Object> path() {
    return raw.get("path") instanceof List<?> path ? (List<Object>) path : null;
  }

  /**
   * @return the {@code locations} ({@code [{line, column}]}), or {@code null} when absent
   */
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> locations() {
    return raw.get("locations") instanceof List<?> locations ? (List<Map<String, Object>>) locations : null;
  }

  /**
   * @return the {@code extensions} (Booster puts the error {@code code} there), or {@code null} when absent
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> extensions() {
    return raw.get("extensions") instanceof Map<?, ?> extensions ? (Map<String, Object>) extensions : null;
  }

  /**
   * @return the {@code extensions.code} (e.g. {@code BoosterTokenExpiredError}), or {@code null}
   */
  public String code() {
    Map<String, Object> extensions = extensions();
    return extensions != null && extensions.get("code") instanceof String code ? code : null;
  }

  /**
   * @return the raw entry, unmodifiable, in the server's key order
   */
  public Map<String, Object> toMap() {
    return raw;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof GraphQLError error && raw.equals(error.raw);
  }

  @Override
  public int hashCode() {
    return Objects.hash(raw);
  }

  /**
   * @return the entry as JSON
   */
  @Override
  public String toString() {
    return BoosterJson.toJson(raw);
  }
}
