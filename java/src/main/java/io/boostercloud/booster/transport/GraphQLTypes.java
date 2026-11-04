package io.boostercloud.booster.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.GraphQLContext;
import graphql.Scalars;
import graphql.execution.CoercedVariables;
import graphql.language.*;
import graphql.schema.*;

import java.lang.reflect.*;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.TemporalAccessor;
import java.util.*;

/** Generates GraphQL input/output types from Java records without classpath scanning. */
final class GraphQLTypes {
    private final ObjectMapper mapper;
    private final Map<Type, GraphQLInputType> inputs = new HashMap<>();
    private final Map<Type, GraphQLOutputType> outputs = new HashMap<>();
    private final Map<String, Class<?>> owners = new HashMap<>();
    private final Map<String, GraphQLNamedType> definitions = new LinkedHashMap<>();
    private final GraphQLCodeRegistry.Builder code = GraphQLCodeRegistry.newCodeRegistry();

    GraphQLTypes(ObjectMapper mapper) { this.mapper = mapper; }

    GraphQLCodeRegistry.Builder codeRegistry() { return code; }
    Set<GraphQLType> definitions() { return new LinkedHashSet<>(definitions.values()); }

    final GraphQLScalarType json = GraphQLScalarType.newScalar().name("JSON")
            .description("A JSON value; input is still validated by the registered Java handler.")
            .coercing(new Coercing<Object, Object>() {
                @Override public Object serialize(Object value, GraphQLContext context, Locale locale) {
                    return value instanceof JsonNode ? mapper.convertValue(value, Object.class) : value;
                }
                @Override public Object parseValue(Object value, GraphQLContext context, Locale locale) { return value; }
                @Override public Object parseLiteral(Value<?> value, CoercedVariables variables,
                                                     GraphQLContext context, Locale locale) {
                    return literal(value, variables);
                }
            }).build();

    private static final GraphQLScalarType DATE = GraphQLScalarType.newScalar().name("Date")
            .description("ISO-8601 date-time, serialized with millisecond precision as in Booster TypeScript.")
            .coercing(new Coercing<String, String>() {
                private String checked(Object value) {
                    try {
                        java.time.Instant instant = value instanceof java.util.Date date ? date.toInstant()
                                : value instanceof java.time.Instant time ? time : java.time.OffsetDateTime.parse(value.toString()).toInstant();
                        return new java.time.format.DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(instant);
                    } catch (RuntimeException failure) { throw new CoercingParseValueException("Expected an ISO-8601 date-time"); }
                }
                @Override public String serialize(Object value, GraphQLContext context, Locale locale) {
                    try { return checked(value); } catch (RuntimeException failure) { throw new CoercingSerializeException("Expected an ISO-8601 date-time"); }
                }
                @Override public String parseValue(Object value, GraphQLContext context, Locale locale) {
                    if (!(value instanceof String)) throw new CoercingParseValueException("Expected an ISO-8601 date-time string");
                    return checked(value);
                }
                @Override public String parseLiteral(Value<?> value, CoercedVariables variables, GraphQLContext context, Locale locale) {
                    if (!(value instanceof StringValue text)) throw new CoercingParseLiteralException("Expected an ISO-8601 date-time string");
                    try { return checked(text.getValue()); }
                    catch (RuntimeException failure) { throw new CoercingParseLiteralException("Expected an ISO-8601 date-time string"); }
                }
            }).build();

    GraphQLInputType input(Type type) {
        if (inputs.containsKey(type)) return inputs.get(type);
        GraphQLType scalar = scalar(type);
        if (scalar != null) return (GraphQLInputType) scalar;
        if (type instanceof ParameterizedType parameterized) {
            if (parameterized.getRawType() == Optional.class) return input(parameterized.getActualTypeArguments()[0]);
            if (parameterized.getRawType() instanceof Class<?> raw && Collection.class.isAssignableFrom(raw))
                return GraphQLList.list(listInputElement(parameterized.getActualTypeArguments()[0]));
            return json;
        }
        if (!(type instanceof Class<?> raw)) return json;
        if (raw.isArray()) return GraphQLList.list(GraphQLNonNull.nonNull(input(raw.getComponentType())));
        if (raw.isEnum()) return enumType(raw);
        if (!raw.isRecord() || raw.getRecordComponents().length == 0) return json;
        String name = name(raw) + "Input";
        inputs.put(type, GraphQLTypeReference.typeRef(name));
        GraphQLInputObjectType.Builder builder = GraphQLInputObjectType.newInputObject().name(name);
        for (RecordComponent field : raw.getRecordComponents()) {
            if (field.isAnnotationPresent(GraphQLHidden.class)) continue;
            GraphQLInputType fieldType = input(field.getGenericType());
            if (!field.isAnnotationPresent(GraphQLNullable.class) && field.getType() != Optional.class) fieldType = GraphQLNonNull.nonNull(fieldType);
            builder.field(GraphQLInputObjectField.newInputObjectField().name(field.getName()).type(fieldType));
        }
        GraphQLInputObjectType result = builder.build();
        definitions.put(name, result);
        inputs.put(type, result);
        return result;
    }

    GraphQLOutputType output(Type type) {
        if (outputs.containsKey(type)) return outputs.get(type);
        GraphQLType scalar = scalar(type);
        if (scalar != null) return (GraphQLOutputType) scalar;
        if (type instanceof ParameterizedType parameterized) {
            if (parameterized.getRawType() == Optional.class) return output(parameterized.getActualTypeArguments()[0]);
            if (parameterized.getRawType() instanceof Class<?> raw && Collection.class.isAssignableFrom(raw))
                return GraphQLList.list(listOutputElement(parameterized.getActualTypeArguments()[0]));
            return json;
        }
        if (!(type instanceof Class<?> raw)) return json;
        if (raw.isArray()) return GraphQLList.list(GraphQLNonNull.nonNull(output(raw.getComponentType())));
        if (raw.isEnum()) return enumType(raw);
        if (raw.isSealed() && Arrays.stream(raw.getPermittedSubclasses()).allMatch(Class::isRecord)) return union(raw);
        if (!raw.isRecord() || raw.getRecordComponents().length == 0) return json;
        String name = name(raw);
        outputs.put(type, GraphQLTypeReference.typeRef(name));
        GraphQLObjectType.Builder builder = GraphQLObjectType.newObject().name(name);
        for (RecordComponent field : raw.getRecordComponents()) {
            if (field.isAnnotationPresent(GraphQLHidden.class)) continue;
            GraphQLOutputType fieldType = output(field.getGenericType());
            if (!field.isAnnotationPresent(GraphQLNullable.class) && field.getType() != Optional.class) fieldType = GraphQLNonNull.nonNull(fieldType);
            builder.field(GraphQLFieldDefinition.newFieldDefinition().name(field.getName()).type(fieldType));
            code.dataFetcher(FieldCoordinates.coordinates(name, field.getName()), (DataFetcher<Object>) environment -> {
                Object source = environment.getSource();
                if (source == null) return null;
                Object value;
                if (source instanceof JsonNode node) value = mapper.convertValue(node.get(field.getName()), Object.class);
                else if (source instanceof Map<?, ?> map) value = map.get(field.getName());
                else value = field.getAccessor().invoke(source);
                return value instanceof Optional<?> optional ? optional.orElse(null) : value;
            });
        }
        GraphQLObjectType result = builder.build();
        definitions.put(name, result);
        outputs.put(type, result);
        return result;
    }

    private GraphQLOutputType union(Class<?> raw) {
        String unionName = name(raw);
        outputs.put(raw, GraphQLTypeReference.typeRef(unionName));
        var builder = GraphQLUnionType.newUnionType().name(unionName);
        Set<String> members = new HashSet<>();
        for (Class<?> member : raw.getPermittedSubclasses()) {
            GraphQLOutputType type = output(member); members.add(name(member));
            if (type instanceof GraphQLObjectType object) builder.possibleType(object);
            else if (type instanceof GraphQLTypeReference reference) builder.possibleType(reference);
            else throw new IllegalArgumentException("GraphQL union member must have record fields");
        }
        code.typeResolver(unionName, environment -> {
            Object source = environment.getObject(); String selected;
            if (source instanceof Map<?,?> map) selected = Objects.toString(map.get("__typename"), "");
            else if (source instanceof JsonNode node) selected = node.path("__typename").asText();
            else selected = source == null ? "" : source.getClass().getSimpleName();
            if (!members.contains(selected)) throw new IllegalArgumentException("Union values require a declared __typename discriminator");
            return environment.getSchema().getObjectType(selected);
        });
        GraphQLUnionType result = builder.build(); definitions.put(unionName, result); outputs.put(raw, result); return result;
    }

    private GraphQLInputType listInputElement(Type type) {
        GraphQLInputType element = input(type);
        return type instanceof ParameterizedType parameterized && parameterized.getRawType() == Optional.class
                ? element : GraphQLNonNull.nonNull(element);
    }
    private GraphQLOutputType listOutputElement(Type type) {
        GraphQLOutputType element = output(type);
        return type instanceof ParameterizedType parameterized && parameterized.getRawType() == Optional.class
                ? element : GraphQLNonNull.nonNull(element);
    }

    GraphQLInputType filter(Class<?> type, String filterName, boolean defined) {
        if (definitions.containsKey(filterName)) return GraphQLTypeReference.typeRef(filterName);
        var builder = GraphQLInputObjectType.newInputObject().name(filterName);
        // Put a reference first so recursive records terminate.
        definitions.put(filterName, GraphQLTypeReference.typeRef(filterName));
        if (type.isRecord()) for (RecordComponent field : type.getRecordComponents()) {
            if (field.isAnnotationPresent(GraphQLHidden.class)) continue;
            builder.field(GraphQLInputObjectField.newInputObjectField().name(field.getName()).type(propertyFilter(field.getGenericType())));
        }
        builder.field(GraphQLInputObjectField.newInputObjectField().name("and").type(GraphQLList.list(GraphQLTypeReference.typeRef(filterName))))
                .field(GraphQLInputObjectField.newInputObjectField().name("or").type(GraphQLList.list(GraphQLTypeReference.typeRef(filterName))))
                .field(GraphQLInputObjectField.newInputObjectField().name("not").type(GraphQLTypeReference.typeRef(filterName)));
        if (defined) builder.field(GraphQLInputObjectField.newInputObjectField().name("isDefined").type(Scalars.GraphQLBoolean));
        var result = builder.build(); definitions.put(filterName, result); return result;
    }

    private GraphQLInputType propertyFilter(Type type) {
        if (type instanceof ParameterizedType parameterized) {
            if (parameterized.getRawType() == Optional.class) return propertyFilter(parameterized.getActualTypeArguments()[0]);
            if (parameterized.getRawType() instanceof Class<?> raw && Collection.class.isAssignableFrom(raw))
                return arrayFilter(parameterized.getActualTypeArguments()[0]);
            return json;
        }
        if (!(type instanceof Class<?> raw)) return json;
        if (raw.isArray()) return arrayFilter(raw.getComponentType());
        if (raw.isRecord()) return filter(raw, name(raw) + "PropertyFilter", true);
        GraphQLInputType scalar = (GraphQLInputType) scalar(raw);
        if (raw.isEnum()) scalar = enumType(raw);
        if (scalar == null || scalar == json) return json;
        String category = raw.isEnum() ? name(raw) : scalar == Scalars.GraphQLFloat ? "Number"
                : scalar == Scalars.GraphQLBoolean ? "Boolean" : scalar == Scalars.GraphQLID ? "UUID" : scalar == DATE ? "Date" : "String";
        String filterName = category + "PropertyFilter";
        if (definitions.containsKey(filterName)) return GraphQLTypeReference.typeRef(filterName);
        var builder = GraphQLInputObjectType.newInputObject().name(filterName);
        for (String operator : List.of("eq", "ne")) builder.field(GraphQLInputObjectField.newInputObjectField().name(operator).type(scalar));
        builder.field(GraphQLInputObjectField.newInputObjectField().name("isDefined").type(Scalars.GraphQLBoolean));
        if (!raw.isEnum() && scalar != Scalars.GraphQLBoolean) {
            builder.field(GraphQLInputObjectField.newInputObjectField().name("in").type(GraphQLList.list(scalar)));
            if (scalar != Scalars.GraphQLID) for (String operator : List.of("lt", "lte", "gt", "gte"))
                builder.field(GraphQLInputObjectField.newInputObjectField().name(operator).type(scalar));
            if (scalar == Scalars.GraphQLString || scalar == Scalars.GraphQLID) for (String operator : List.of("beginsWith", "contains"))
                builder.field(GraphQLInputObjectField.newInputObjectField().name(operator).type(Scalars.GraphQLString));
            if (scalar == Scalars.GraphQLString) for (String operator : List.of("regex", "iRegex"))
                builder.field(GraphQLInputObjectField.newInputObjectField().name(operator).type(Scalars.GraphQLString));
        }
        var result = builder.build(); definitions.put(filterName, result); return result;
    }

    private GraphQLInputType arrayFilter(Type item) {
        GraphQLInputType scalar = item instanceof Class<?> raw ? (GraphQLInputType) scalar(raw) : json;
        if (scalar == null) scalar = json;
        String category = scalar == Scalars.GraphQLFloat ? "Number" : scalar == Scalars.GraphQLBoolean ? "Boolean"
                : scalar == Scalars.GraphQLString ? "String" : scalar == Scalars.GraphQLID ? "UUID" : "JSON";
        String filterName = category + "ArrayPropertyFilter";
        if (definitions.containsKey(filterName)) return GraphQLTypeReference.typeRef(filterName);
        var result = GraphQLInputObjectType.newInputObject().name(filterName)
                .field(GraphQLInputObjectField.newInputObjectField().name("includes").type(scalar))
                .field(GraphQLInputObjectField.newInputObjectField().name("isDefined").type(Scalars.GraphQLBoolean)).build();
        definitions.put(filterName, result); return result;
    }

    GraphQLInputType sort(Class<?> type, String sortName) {
        if (definitions.containsKey(sortName)) return GraphQLTypeReference.typeRef(sortName);
        var order = GraphQLEnumType.newEnum().name("orderProperty").value("ASC").value("DESC").build();
        definitions.putIfAbsent("orderProperty", order);
        var builder = GraphQLInputObjectType.newInputObject().name(sortName);
        definitions.put(sortName, GraphQLTypeReference.typeRef(sortName));
        if (type.isRecord()) for (RecordComponent field : type.getRecordComponents()) {
            if (field.isAnnotationPresent(GraphQLHidden.class)) continue;
            Class<?> raw = field.getType();
            GraphQLInputType nested = raw.isRecord() ? sort(raw, name(raw) + "PropertySortBy")
                    : (GraphQLInputType) definitions.get("orderProperty");
            builder.field(GraphQLInputObjectField.newInputObjectField().name(field.getName()).type(nested));
        }
        var result = builder.build(); definitions.put(sortName, result); return result;
    }

    private GraphQLType scalar(Type type) {
        if (!(type instanceof Class<?> raw)) return null;
        if (raw == java.time.Instant.class || raw == java.time.OffsetDateTime.class || raw == java.time.ZonedDateTime.class
                || java.util.Date.class.isAssignableFrom(raw)) return DATE;
        if (raw == String.class || raw == char.class || raw == Character.class || TemporalAccessor.class.isAssignableFrom(raw))
            return Scalars.GraphQLString;
        if (raw == UUID.class) return Scalars.GraphQLID;
        if (raw == boolean.class || raw == Boolean.class) return Scalars.GraphQLBoolean;
        if (Number.class.isAssignableFrom(raw) || raw == byte.class || raw == short.class || raw == int.class
                || raw == long.class || raw == float.class || raw == double.class) return Scalars.GraphQLFloat;
        if (raw == void.class || raw == Void.class) return Scalars.GraphQLBoolean;
        if (raw == Object.class || Map.class.isAssignableFrom(raw) || JsonNode.class.isAssignableFrom(raw)) return json;
        return null;
    }

    private GraphQLEnumType enumType(Class<?> raw) {
        String name = name(raw);
        if (definitions.get(name) instanceof GraphQLEnumType existing) return existing;
        GraphQLEnumType.Builder builder = GraphQLEnumType.newEnum().name(name);
        for (Object constant : raw.getEnumConstants()) builder.value(((Enum<?>) constant).name(), ((Enum<?>) constant).name());
        GraphQLEnumType result = builder.build();
        definitions.put(name, result);
        return result;
    }

    private String name(Class<?> type) {
        String name = type.getSimpleName();
        if (!name.matches("[_A-Za-z][_0-9A-Za-z]*") || name.startsWith("__"))
            throw new IllegalArgumentException("Invalid GraphQL type name: " + name);
        Class<?> existing = owners.putIfAbsent(name, type);
        if (existing != null && existing != type)
            throw new IllegalArgumentException("GraphQL type name collision: " + existing.getName() + " and " + type.getName());
        return name;
    }

    private static Object literal(Value<?> value, CoercedVariables variables) {
        if (value instanceof NullValue) return null;
        if (value instanceof StringValue string) return string.getValue();
        if (value instanceof BooleanValue bool) return bool.isValue();
        if (value instanceof IntValue number) return number.getValue();
        if (value instanceof FloatValue number) return number.getValue();
        if (value instanceof EnumValue enumeration) return enumeration.getName();
        if (value instanceof VariableReference variable) return variables.get(variable.getName());
        if (value instanceof ArrayValue array) return array.getValues().stream().map(item -> literal(item, variables)).toList();
        if (value instanceof ObjectValue object) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (ObjectField field : object.getObjectFields()) result.put(field.getName(), literal(field.getValue(), variables));
            return result;
        }
        throw new CoercingParseLiteralException("Unsupported JSON literal");
    }
}
