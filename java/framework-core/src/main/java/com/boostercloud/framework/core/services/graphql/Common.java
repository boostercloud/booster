package com.boostercloud.framework.core.services.graphql;

import com.boostercloud.framework.core.internal.Js;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.metadata.PropertyMetadata;
import com.boostercloud.metadata.TypeMetadata;
import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.EnumValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.language.VariableReference;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Port of the functions and constants of {@code services/graphql/common.ts} ({@code DateScalar},
 * {@code isExternalType}, {@code buildGraphqlSimpleEnumFor}, {@code nonExcludedFields}), plus the {@code JSON}
 * scalar of {@code graphql-scalars} ({@code GraphQLJSON}) and the lookup of the {@link GraphQLResolverContext} of a
 * resolver. The types of that file are {@link GraphQLResolverContext}, {@link TargetTypeMetadata},
 * {@link ResolverBuilder} and {@link GraphQLFieldResolver}.
 */
public final class Common {

  /** Description of the {@code Date} scalar (as in TypeScript). */
  public static final String DATE_SCALAR_DESCRIPTION = "Date custom scalar type";

  /** Description of the {@code JSON} scalar (as in {@code graphql-scalars}). */
  public static final String JSON_SCALAR_DESCRIPTION = "The `JSON` scalar type represents JSON values as specified by "
      + "[ECMA-404](http://www.ecma-international.org/publications/files/ECMA-ST/ECMA-404_1st_edition_october_2009.pdf).";

  private static final GraphQLScalarType DATE_SCALAR = GraphQLScalarType.newScalar()
      .name("Date")
      .description(DATE_SCALAR_DESCRIPTION)
      .coercing(new DateCoercing())
      .build();

  private static final GraphQLScalarType JSON_SCALAR = GraphQLScalarType.newScalar()
      .name("JSON")
      .description(JSON_SCALAR_DESCRIPTION)
      .coercing(new JsonCoercing())
      .build();

  private Common() {}

  /**
   * {@code DateScalar}: the {@code Date} scalar.
   *
   * <ul>
   *   <li>serialization: {@link Instant}, {@link Date}, {@link OffsetDateTime}, {@link ZonedDateTime} (and date
   *       strings, see the README) are written as ISO-8601 UTC with milliseconds ({@code 2024-01-30T10:15:30.000Z},
   *       JavaScript {@code Date#toJSON});</li>
   *   <li>input (variables and literals): a non-string fails with {@code Invalid date, not a string: <value>}, an
   *       unparseable string with {@code Invalid date: <value>}; the parsed value is the normalised ISO-8601 string, so
   *       filters and command inputs stay raw values (the read side binds it to the declared date type);</li>
   *   <li>a literal that is not a string is parsed as {@code null}, as in TypeScript.</li>
   * </ul>
   *
   * @return the scalar (one shared instance)
   */
  public static GraphQLScalarType dateScalar() {
    return DATE_SCALAR;
  }

  /**
   * {@code GraphQLJSON} ({@code graphql-scalars}): any JSON value. Output values are written as raw JSON values
   * ({@link BoosterJson#toRaw(Object)} for objects); variables are taken as they are; literals become raw values
   * (objects, lists, strings, numbers, booleans, {@code null}, enum names as strings, variables resolved).
   *
   * @return the scalar (one shared instance)
   */
  public static GraphQLScalarType jsonScalar() {
    return JSON_SCALAR;
  }

  /**
   * {@code isExternalType(typeMetadata)}: whether the type is not part of the application (JDK, Booster itself,
   * Jackson, graphql-java, or a package registered in {@code ExternalTypes}), so it is exposed as {@code JSON}
   * instead of being expanded. TypeScript: {@code importPath} present and not relative.
   *
   * @param typeMetadata a type
   * @return whether it is external
   */
  public static boolean isExternalType(TypeMetadata typeMetadata) {
    return typeMetadata.isExternal();
  }

  /**
   * {@code buildGraphqlSimpleEnumFor(enumName, values)}: an enum whose internal values are the value names.
   *
   * @param enumName the enum name
   * @param values its values, in order
   * @return the enum type
   */
  public static GraphQLEnumType buildGraphqlSimpleEnumFor(String enumName, List<String> values) {
    GraphQLEnumType.Builder builder = GraphQLEnumType.newEnum().name(enumName);
    for (String value : values) {
      builder.value(value, value);
    }
    return builder.build();
  }

  /**
   * {@code nonExcludedFields(fields, excludeProps)}.
   *
   * @param fields the properties
   * @param excludeProps names to drop, or {@code null} (keep everything)
   * @return the remaining properties, in order
   */
  public static List<PropertyMetadata> nonExcludedFields(List<PropertyMetadata> fields, List<String> excludeProps) {
    if (excludeProps == null) {
      return fields;
    }
    return fields.stream().filter(field -> !excludeProps.contains(field.name())).toList();
  }

  /**
   * Finds the {@link GraphQLResolverContext} of a resolver call (graphql-js: the {@code contextValue}). Booster puts
   * it in the {@link GraphQLContext} under the key {@code GraphQLResolverContext.class}
   * ({@link #graphQLContextOf(GraphQLResolverContext)}); a resolver context given as local context, as any other
   * {@code GraphQLContext} entry or as the legacy context object is found too.
   *
   * @param environment the data fetching environment, or {@code null}
   * @return the context, or {@code null} when there is none
   */
  @SuppressWarnings("deprecation")
  public static GraphQLResolverContext resolverContext(DataFetchingEnvironment environment) {
    if (environment == null) {
      return null;
    }
    if (environment.getLocalContext() instanceof GraphQLResolverContext context) {
      return context;
    }
    GraphQLContext graphQLContext = environment.getGraphQlContext();
    if (graphQLContext != null) {
      if (graphQLContext.get(GraphQLResolverContext.class) instanceof GraphQLResolverContext context) {
        return context;
      }
      GraphQLResolverContext found = graphQLContext.stream()
          .map(Map.Entry::getValue)
          .filter(GraphQLResolverContext.class::isInstance)
          .map(GraphQLResolverContext.class::cast)
          .findFirst()
          .orElse(null);
      if (found != null) {
        return found;
      }
    }
    return environment.getContext() instanceof GraphQLResolverContext context ? context : null;
  }

  /**
   * The {@link GraphQLContext} entries that carry a resolver context (for {@code ExecutionInput.graphQLContext}).
   *
   * @param context the resolver context
   * @return a map with the context under the key {@code GraphQLResolverContext.class}
   */
  public static Map<Object, Object> graphQLContextOf(GraphQLResolverContext context) {
    Map<Object, Object> entries = new LinkedHashMap<>();
    entries.put(GraphQLResolverContext.class, context);
    return entries;
  }

  /**
   * @param type a type created for an output position
   * @return the type as an output type
   * @throws IllegalStateException when the type can only be used as an input type
   */
  public static GraphQLOutputType asOutputType(GraphQLType type) {
    if (type instanceof GraphQLOutputType output) {
      return output;
    }
    throw new IllegalStateException("The GraphQL type " + printType(type) + " cannot be used as an output type");
  }

  /**
   * @param type a type created for an input position
   * @return the type as an input type
   * @throws IllegalStateException when the type can only be used as an output type (in TypeScript, where nothing
   *     checks this at construction, the result is an invalid schema that fails every request)
   */
  public static GraphQLInputType asInputType(GraphQLType type) {
    if (type instanceof GraphQLInputType input) {
      return input;
    }
    throw new IllegalStateException("The GraphQL type " + printType(type) + " cannot be used as an input type");
  }

  /**
   * An input object type with the given fields (graphql-js {@code new GraphQLInputObjectType({ name, fields })}).
   *
   * @param name the type name
   * @param fields the type of each field, by field name, in order
   * @return the input object type
   */
  public static GraphQLInputObjectType inputObjectType(String name, Map<String, GraphQLInputType> fields) {
    GraphQLInputObjectType.Builder builder = GraphQLInputObjectType.newInputObject().name(name);
    fields.forEach((fieldName, fieldType) ->
        builder.field(GraphQLInputObjectField.newInputObjectField().name(fieldName).type(fieldType).build()));
    return builder.build();
  }

  /**
   * The TypeScript {@code String(type)} of a GraphQL type: {@code [String!]!}, {@code Cart}.
   *
   * @param type a type
   * @return its SDL notation
   */
  public static String printType(GraphQLType type) {
    return GraphQLTypeUtil.simplePrint(type);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Scalars
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * {@code parseDate(inputValue)}: a string parsed like JavaScript's {@code new Date(string)} (the formats of
   * {@link Dates#parse(String)}), returned as the normalised ISO-8601 string.
   *
   * @param inputValue the value
   * @return the ISO-8601 instant with milliseconds
   * @throws IllegalArgumentException {@code Invalid date, not a string: <v>} or {@code Invalid date: <v>}
   */
  public static String parseDate(Object inputValue) {
    if (!(inputValue instanceof String text)) {
      throw new IllegalArgumentException("Invalid date, not a string: " + Js.toString(inputValue));
    }
    try {
      return Dates.toIso(Dates.parse(text));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid date: " + text, e);
    }
  }

  /**
   * {@code (value as Date).toJSON()}.
   *
   * @param value a date value
   * @return its ISO-8601 text with milliseconds
   * @throws IllegalArgumentException when the value is not a date
   */
  public static String serializeDate(Object value) {
    return switch (value) {
      case Instant instant -> Dates.toIso(instant);
      case Date date -> Dates.toIso(date.toInstant());
      case OffsetDateTime dateTime -> Dates.toIso(dateTime.toInstant());
      case ZonedDateTime dateTime -> Dates.toIso(dateTime.toInstant());
      case String text -> Dates.toIso(Dates.parse(text));
      case null -> throw new IllegalArgumentException("Cannot serialize null as a Date");
      default -> throw new IllegalArgumentException("Cannot serialize " + Js.toString(value) + " as a Date");
    };
  }

  private static final class DateCoercing implements Coercing<String, String> {
    @Override
    public String serialize(Object dataFetcherResult, GraphQLContext graphQLContext, Locale locale) {
      try {
        return serializeDate(dataFetcherResult);
      } catch (RuntimeException e) {
        throw new CoercingSerializeException(e.getMessage(), e);
      }
    }

    @Override
    public String parseValue(Object input, GraphQLContext graphQLContext, Locale locale) {
      try {
        return parseDate(input);
      } catch (IllegalArgumentException e) {
        throw new CoercingParseValueException(e.getMessage(), e);
      }
    }

    @Override
    public String parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext graphQLContext,
        Locale locale) {
      if (input instanceof StringValue string) {
        try {
          return parseDate(string.getValue());
        } catch (IllegalArgumentException e) {
          throw new CoercingParseLiteralException(e.getMessage(), e);
        }
      }
      // This should never happen, it's not safe, but the safeguards at the edges should guarantee this.
      return null;
    }

    @Override
    public Value<?> valueToLiteral(Object input, GraphQLContext graphQLContext, Locale locale) {
      return StringValue.of(serializeDate(input));
    }
  }

  private static final class JsonCoercing implements Coercing<Object, Object> {
    @Override
    public Object serialize(Object dataFetcherResult, GraphQLContext graphQLContext, Locale locale) {
      return toRawJson(dataFetcherResult);
    }

    @Override
    public Object parseValue(Object input, GraphQLContext graphQLContext, Locale locale) {
      return input;
    }

    @Override
    public Object parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext graphQLContext,
        Locale locale) {
      return literalToRaw(input, variables);
    }

    @Override
    public Value<?> valueToLiteral(Object input, GraphQLContext graphQLContext, Locale locale) {
      return rawToLiteral(toRawJson(input));
    }
  }

  private static Object toRawJson(Object value) {
    return switch (value) {
      case null -> null;
      case String text -> text;
      case Boolean bool -> bool;
      case Number number -> number;
      default -> BoosterJson.toRaw(value);
    };
  }

  private static Object literalToRaw(Value<?> value, CoercedVariables variables) {
    return switch (value) {
      case NullValue ignored -> null;
      case StringValue string -> string.getValue();
      case BooleanValue bool -> bool.isValue();
      case IntValue integer -> BoosterJson.parse(integer.getValue().toString());
      case FloatValue decimal -> BoosterJson.parse(decimal.getValue().toString());
      case EnumValue enumValue -> enumValue.getName();
      case ArrayValue array -> {
        List<Object> items = new ArrayList<>();
        for (Value<?> item : array.getValues()) {
          items.add(literalToRaw(item, variables));
        }
        yield items;
      }
      case ObjectValue object -> {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (ObjectField field : object.getObjectFields()) {
          fields.put(field.getName(), literalToRaw(field.getValue(), variables));
        }
        yield fields;
      }
      case VariableReference reference -> variables == null ? null : variables.get(reference.getName());
      default -> throw new CoercingParseLiteralException("Unsupported JSON literal: " + value);
    };
  }

  @SuppressWarnings("rawtypes")
  private static Value<?> rawToLiteral(Object raw) {
    return switch (raw) {
      case null -> NullValue.of();
      case String text -> StringValue.of(text);
      case Boolean bool -> BooleanValue.of(bool);
      case Integer number -> IntValue.of(number);
      case Long number -> IntValue.newIntValue(BigInteger.valueOf(number)).build();
      case BigInteger number -> IntValue.newIntValue(number).build();
      case Number number -> FloatValue.newFloatValue(new BigDecimal(number.toString())).build();
      case List<?> list -> ArrayValue.newArrayValue()
          .values(list.stream().<Value>map(Common::rawToLiteral).toList())
          .build();
      case Map<?, ?> map -> {
        ObjectValue.Builder builder = ObjectValue.newObjectValue();
        map.forEach((key, item) -> builder.objectField(new ObjectField(String.valueOf(key), rawToLiteral(item))));
        yield builder.build();
      }
      default -> StringValue.of(raw.toString());
    };
  }
}
