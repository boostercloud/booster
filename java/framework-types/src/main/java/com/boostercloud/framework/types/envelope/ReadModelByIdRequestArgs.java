package com.boostercloud.framework.types.envelope;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Arguments of the GraphQL by-id read model query: {@code id} plus, for sequenced read models, one argument named
 * after the sequence-key property. Port of {@code ReadModelByIdRequestArgs}
 * ({@code { id: string, [sequenceKey: string]: string | undefined }}).
 *
 * @param id the read model id
 * @param otherArguments the other arguments by name (the sequence key, at most one)
 */
public record ReadModelByIdRequestArgs(String id, Map<String, String> otherArguments) {

  /** Canonical constructor. */
  public ReadModelByIdRequestArgs {
    Objects.requireNonNull(id, "id");
    otherArguments = otherArguments == null ? Map.of() : Map.copyOf(otherArguments);
  }

  /**
   * @param raw the raw GraphQL arguments
   * @return the arguments
   */
  public static ReadModelByIdRequestArgs fromMap(Map<String, ?> raw) {
    Map<String, String> others = new LinkedHashMap<>();
    raw.forEach((key, value) -> {
      if (!key.equals("id") && value != null) {
        others.put(key, value.toString());
      }
    });
    Object id = raw.get("id");
    return new ReadModelByIdRequestArgs(id == null ? null : id.toString(), others);
  }

  /**
   * @param name an argument name (the sequence-key property name)
   * @return its value, or {@code null}
   */
  public String get(String name) {
    return otherArguments.get(name);
  }
}
