package com.boostercloud.framework.types.envelope;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;

/**
 * What an event search looks for. Port of the union {@code EventParametersFilterByEntity |
 * EventParametersFilterByType}, discriminated by the presence of {@code entity} or {@code type} (Jackson deduces the
 * subtype from the properties present).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
  @JsonSubTypes.Type(EventParametersFilterByEntity.class),
  @JsonSubTypes.Type(EventParametersFilterByType.class)
})
public sealed interface EventSearchParameters extends EventTimeParameterFilter
    permits EventParametersFilterByEntity, EventParametersFilterByType {

  /** The kind of search, decided from a raw parameters map like core does ({@code 'entity' in parameters}). */
  enum Kind {
    /** The map has {@code entity} and not {@code type}. */
    BY_ENTITY,
    /** The map has {@code type} and not {@code entity}. */
    BY_TYPE,
    /** The map has neither. */
    NONE,
    /** The map has both. */
    BOTH
  }

  /**
   * @param raw a raw parameters map (e.g. GraphQL arguments)
   * @return which search it describes; key presence counts, even with a {@code null} value
   */
  static Kind kindOf(Map<String, ?> raw) {
    boolean entity = raw.containsKey("entity");
    boolean type = raw.containsKey("type");
    if (entity && type) {
      return Kind.BOTH;
    }
    if (entity) {
      return Kind.BY_ENTITY;
    }
    return type ? Kind.BY_TYPE : Kind.NONE;
  }

  /**
   * Builds the parameters from a raw map with exactly one of {@code entity} / {@code type}.
   *
   * @param raw the raw parameters
   * @return the typed parameters
   * @throws IllegalArgumentException when the map has both or none of the discriminating keys (core reports these
   *     cases with its own {@code InvalidParameterError} messages, so check {@link #kindOf(Map)} first)
   */
  static EventSearchParameters fromMap(Map<String, ?> raw) {
    Integer limit = raw.get("limit") instanceof Number number ? number.intValue() : null;
    String from = raw.get("from") == null ? null : raw.get("from").toString();
    String to = raw.get("to") == null ? null : raw.get("to").toString();
    return switch (kindOf(raw)) {
      case BY_ENTITY -> new EventParametersFilterByEntity(
          String.valueOf(raw.get("entity")),
          raw.get("entityID") == null ? null : raw.get("entityID").toString(),
          from, to, limit);
      case BY_TYPE -> new EventParametersFilterByType(String.valueOf(raw.get("type")), from, to, limit);
      case NONE, BOTH -> throw new IllegalArgumentException(
          "Invalid event search parameters: exactly one of \"entity\" or \"type\" is required");
    };
  }
}
