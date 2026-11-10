package com.boostercloud.cli.services.generator.target;

import com.boostercloud.cli.common.CliException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses the raw command-line values of the generators ({@code services/generator/target/parsing.ts}).
 *
 * <p>TypeScript parsers return promises; here they are plain functions that throw a {@link CliException} with the
 * same messages.
 */
public final class Parsing {
  private Parsing() {}

  /**
   * @param name the resource name
   * @return {@code {name}}
   */
  public static HasName parseName(String name) {
    return new HasName(name);
  }

  /**
   * @param event the event name
   * @return {@code {event}}
   */
  public static HasEvent parseEvent(String event) {
    return new HasEvent(event);
  }

  /**
   * Parses {@code <field name>:<field type>} values. The first malformed field is reported before duplicated
   * names, which are reported all together (every repeated occurrence, in order).
   *
   * @param fields the raw fields
   * @return the parsed fields
   * @throws CliException {@code Error parsing field <field>. Fields must be in the form of <field name>:<field
   *     type>} or {@code Error parsing field <names>. Fields cannot be duplicated}
   */
  public static HasFields parseFields(List<String> fields) {
    List<Field> parsedFields = new ArrayList<>();
    for (String rawField : fields) {
      parsedFields.add(parseField(rawField));
    }
    Set<String> uniqueFieldNames = new LinkedHashSet<>();
    parsedFields.forEach(field -> uniqueFieldNames.add(field.name()));
    List<String> duplicates = new ArrayList<>();
    for (Field field : parsedFields) {
      if (!uniqueFieldNames.remove(field.name())) {
        duplicates.add(field.name());
      }
    }
    if (!duplicates.isEmpty()) {
      throw fieldDuplicatedError(String.join(", ", duplicates));
    }
    return new HasFields(parsedFields);
  }

  /**
   * Parses {@code <entity name>:<entity id>} values.
   *
   * @param fields the raw projections
   * @return the parsed projections
   * @throws CliException for the first malformed projection
   */
  public static HasProjections parseProjections(List<String> fields) {
    List<Projection> projections = new ArrayList<>();
    for (String field : fields) {
      projections.add(parseProjection(field));
    }
    return new HasProjections(projections);
  }

  /**
   * Parses one {@code <entity name>:<entity id>} value.
   *
   * @param field the raw projection
   * @return the parsed projection
   * @throws CliException when it is malformed
   */
  public static HasProjection parseProjectionField(String field) {
    return new HasProjection(parseProjection(field));
  }

  /**
   * @param rawEvents the event names
   * @return {@code {events: [{eventName}...]}}
   */
  public static HasReaction parseReaction(List<String> rawEvents) {
    return new HasReaction(rawEvents.stream().map(ReactionEvent::new).toList());
  }

  /**
   * Joins the results of several parsers into the view used to render a template: a left-to-right shallow merge
   * of their properties, where records become maps (as plain JavaScript objects) recursively.
   *
   * <p>In TypeScript every parser is an already running promise and the left-most rejection wins; here the
   * arguments are evaluated left to right, so the left-most failing parser throws first.
   *
   * @param parsed the parser results (records)
   * @return the merged view
   */
  public static Map<String, Object> joinParsers(Object... parsed) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Object value : parsed) {
      Object view = toView(value);
      if (view instanceof Map<?, ?> map) {
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
      }
    }
    return result;
  }

  /**
   * Converts records (and collections of records) into maps and lists, keeping other values.
   *
   * @param value any value
   * @return the plain view of the value
   */
  public static Object toView(Object value) {
    if (value instanceof Record rec) {
      Map<String, Object> map = new LinkedHashMap<>();
      for (RecordComponent component : rec.getClass().getRecordComponents()) {
        try {
          var accessor = component.getAccessor();
          accessor.setAccessible(true);
          map.put(component.getName(), toView(accessor.invoke(rec)));
        } catch (ReflectiveOperationException e) {
          throw new IllegalStateException("Cannot read " + component.getName() + " of " + rec, e);
        }
      }
      return map;
    }
    if (value instanceof Collection<?> collection) {
      List<Object> list = new ArrayList<>();
      collection.forEach(item -> list.add(toView(item)));
      return list;
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>();
      map.forEach((k, v) -> copy.put(String.valueOf(k), toView(v)));
      return copy;
    }
    return value;
  }

  private static Field parseField(String rawField) {
    String[] splitInput = rawField.split(":", -1);
    if (splitInput.length != 2 || splitInput[0].isEmpty() || splitInput[1].isEmpty()) {
      throw fieldParsingError(rawField);
    }
    return new Field(splitInput[0], splitInput[1]);
  }

  private static Projection parseProjection(String rawProjection) {
    String[] splitInput = rawProjection.split(":", -1);
    if (splitInput.length != 2 || splitInput[0].isEmpty() || splitInput[1].isEmpty()) {
      throw projectionParsingError(rawProjection);
    }
    return new Projection(splitInput[0], splitInput[1]);
  }

  private static CliException fieldParsingError(String field) {
    return new CliException(
        "Error parsing field " + field + ". Fields must be in the form of <field name>:<field type>");
  }

  private static CliException fieldDuplicatedError(String field) {
    return new CliException("Error parsing field " + field + ". Fields cannot be duplicated");
  }

  private static CliException projectionParsingError(String projection) {
    return new CliException(
        "Error parsing projection "
            + projection
            + ". Projections must be in the form of <entity name>:<entity id>");
  }
}
