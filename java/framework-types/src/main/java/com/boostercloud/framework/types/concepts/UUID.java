package com.boostercloud.framework.types.concepts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;

/**
 * Booster's identifier type, the port of {@code class UUID extends String}. It is a plain string wrapper: <b>any
 * string is a valid id</b> ({@code "1234"}, {@code "cart-42"}); only {@link #generate()} produces RFC 4122 version 4
 * values. It serializes as a JSON string and maps to the GraphQL {@code ID} scalar (the metadata name is
 * {@code UUID}). It is deliberately not {@link java.util.UUID}, which would reject non-UUID ids.
 *
 * @param value the id text
 */
public record UUID(@JsonValue String value) implements Comparable<UUID> {

  /** Canonical constructor; rejects {@code null}. */
  public UUID {
    Objects.requireNonNull(value, "A UUID value cannot be null");
  }

  /**
   * @param value any string
   * @return the id wrapping it
   */
  @JsonCreator
  public static UUID of(String value) {
    return new UUID(value);
  }

  /**
   * Null-tolerant factory.
   *
   * @param value any string or {@code null}
   * @return the id, or {@code null} when {@code value} is {@code null}
   */
  public static UUID ofNullable(String value) {
    return value == null ? null : new UUID(value);
  }

  /**
   * {@code UUID.generate()}: a random version 4 UUID in canonical lowercase form (same format as npm
   * {@code uuid.v4()}).
   *
   * @return a new random id
   */
  public static UUID generate() {
    return new UUID(java.util.UUID.randomUUID().toString());
  }

  @Override
  public int compareTo(UUID other) {
    return value.compareTo(other.value);
  }

  /**
   * @return the id text (so string concatenation and logs show the bare id, like in TypeScript)
   */
  @Override
  public String toString() {
    return value;
  }
}
