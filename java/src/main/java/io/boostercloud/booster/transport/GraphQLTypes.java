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

    private static final GraphQLScalarType LONG = GraphQLScalarType.newScalar().name("Long")
            .description("A signed 64-bit integer.")
            .coercing(new Coercing<Long, Long>() {
                private Long checked(Object value) {
                    try { return new BigDecimal(value.toString()).longValueExact(); }
                    catch (RuntimeException exception) { throw new CoercingParseValueException("Expected a 64-bit integer"); }
                }
                @Override public Long serialize(Object value, GraphQLContext context, Locale locale) {
                    try { return checked(value); }
                    catch (RuntimeException exception) { throw new CoercingSerializeException("Expected a 64-bit integer"); }
                }
                @Override public Long parseValue(Object value, GraphQLContext context, Locale locale) { return checked(value); }
                @Override public Long parseLiteral(Value<?> value, CoercedVariables variables,
                                                   GraphQLContext context, Locale locale) {
                    if (!(value instanceof IntValue number)) throw new CoercingParseLiteralException("Expected an integer");
                    try { return number.getValue().longValueExact(); }
                    catch (ArithmeticException exception) { throw new CoercingParseLiteralException("Integer outside 64-bit range"); }
                }
            }).build();

    GraphQLInputType input(Type type) {
        if (inputs.containsKey(type)) return inputs.get(type);
        GraphQLType scalar = scalar(type);
        if (scalar != null) return (GraphQLInputType) scalar;
        if (type instanceof ParameterizedType parameterized) {
            if (parameterized.getRawType() == Optional.class) return input(parameterized.getActualTypeArguments()[0]);
            if (parameterized.getRawType() instanceof Class<?> raw && Collection.class.isAssignableFrom(raw))
                return GraphQLList.list(input(parameterized.getActualTypeArguments()[0]));
            return json;
        }
        if (!(type instanceof Class<?> raw)) return json;
        if (raw.isArray()) return GraphQLList.list(input(raw.getComponentType()));
        if (raw.isEnum()) return enumType(raw);
        if (!raw.isRecord() || raw.getRecordComponents().length == 0) return json;
        String name = name(raw) + "Input";
        inputs.put(type, GraphQLTypeReference.typeRef(name));
        GraphQLInputObjectType.Builder builder = GraphQLInputObjectType.newInputObject().name(name);
        for (RecordComponent field : raw.getRecordComponents()) {
            GraphQLInputType fieldType = input(field.getGenericType());
            if (field.getType().isPrimitive()) fieldType = GraphQLNonNull.nonNull(fieldType);
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
                return GraphQLList.list(output(parameterized.getActualTypeArguments()[0]));
            return json;
        }
        if (!(type instanceof Class<?> raw)) return json;
        if (raw.isArray()) return GraphQLList.list(output(raw.getComponentType()));
        if (raw.isEnum()) return enumType(raw);
        if (!raw.isRecord() || raw.getRecordComponents().length == 0) return json;
        String name = name(raw);
        outputs.put(type, GraphQLTypeReference.typeRef(name));
        GraphQLObjectType.Builder builder = GraphQLObjectType.newObject().name(name);
        for (RecordComponent field : raw.getRecordComponents()) {
            GraphQLOutputType fieldType = output(field.getGenericType());
            if (field.getType().isPrimitive()) fieldType = GraphQLNonNull.nonNull(fieldType);
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

    private GraphQLType scalar(Type type) {
        if (!(type instanceof Class<?> raw)) return null;
        if (raw == String.class || raw == char.class || raw == Character.class || TemporalAccessor.class.isAssignableFrom(raw))
            return Scalars.GraphQLString;
        if (raw == UUID.class) return Scalars.GraphQLID;
        if (raw == boolean.class || raw == Boolean.class) return Scalars.GraphQLBoolean;
        if (raw == byte.class || raw == Byte.class || raw == short.class || raw == Short.class || raw == int.class || raw == Integer.class)
            return Scalars.GraphQLInt;
        if (raw == long.class || raw == Long.class || raw == BigInteger.class) return LONG;
        if (raw == float.class || raw == Float.class || raw == double.class || raw == Double.class || raw == BigDecimal.class)
            return Scalars.GraphQLFloat;
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
