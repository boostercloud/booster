package com.boostercloud.framework.core.services.graphql;

import com.boostercloud.framework.types.Logger;
import com.boostercloud.metadata.ClassMetadata;
import com.boostercloud.metadata.PropertyMetadata;
import com.boostercloud.metadata.TypeGroup;
import com.boostercloud.metadata.TypeMetadata;
import graphql.Scalars;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLUnionType;
import graphql.schema.TypeResolver;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Port of {@code services/graphql/graphql-type-informer.ts}: maps class and type metadata to GraphQL output and input
 * types, with a cache by name shared by the whole schema.
 *
 * <p>Mapping: {@code UUID} → {@code ID}, {@code Date} → the {@code Date} scalar (both by metadata name), String →
 * {@code String}, Number → {@code Float}, Boolean → {@code Boolean}, enums → enums whose internal values are the
 * constant names, arrays → lists, application classes → object types ({@code <Class>} / {@code <Class>Input}), sealed
 * types of application classes → unions (output only), futures → their result type, everything else → {@code JSON}.
 * Every type is non-null unless its metadata is nullable. As in TypeScript, the cache is keyed by name, so the first
 * creation of a name wins (list element nullability, the non-exposed properties of a nested class).
 *
 * <p>Java addition: a class that refers to itself (directly or through other classes) gets a type reference instead
 * of being expanded again; TypeScript recurses until the stack overflows.
 */
public class GraphQLTypeInformer {

  private final Logger logger;
  private final Map<String, GraphQLType> graphQLTypes = new LinkedHashMap<>();
  private final Map<String, TypeResolver> typeResolvers = new LinkedHashMap<>();
  private final Set<String> typesInProgress = new HashSet<>();

  /**
   * @param logger the logger
   */
  public GraphQLTypeInformer(Logger logger) {
    this.logger = logger;
  }

  /**
   * @return the logger
   */
  public Logger logger() {
    return logger;
  }

  /**
   * The type resolvers of the unions created so far (graphql-js keeps them in the union; graphql-java needs them in
   * the code registry): the member type is the simple class name of the value ({@code obj.constructor.name}).
   *
   * @return the resolvers by union name
   */
  public Map<String, TypeResolver> typeResolvers() {
    return Collections.unmodifiableMap(typeResolvers);
  }

  /**
   * {@code generateGraphQLTypeForClass(type, excludeProps, inputType)}: the object (or input object) type of a class,
   * never wrapped in non-null. On a cache hit {@code excludeProps} is ignored, as in TypeScript.
   *
   * @param type the class
   * @param excludeProps properties to leave out, or {@code null}
   * @param inputType whether an input type is generated
   * @return the type ({@code <Class>} or {@code <Class>Input})
   */
  public GraphQLType generateGraphQLTypeForClass(Class<?> type, List<String> excludeProps, boolean inputType) {
    logger.debug("Generate GraphQL " + direction(inputType) + " type for class " + type.getSimpleName());
    ClassMetadata metadata = ClassMetadata.of(type);
    return getOrCreateObjectType(metadata, inputType, excludeProps);
  }

  /**
   * {@code generateGraphQLTypeForClass(type, excludeProps)} (output type).
   *
   * @param type the class
   * @param excludeProps properties to leave out, or {@code null}
   * @return the output type
   */
  public GraphQLType generateGraphQLTypeForClass(Class<?> type, List<String> excludeProps) {
    return generateGraphQLTypeForClass(type, excludeProps, false);
  }

  /**
   * {@code getOrCreateGraphQLType(typeMetadata, inputType)}: the GraphQL type of a type, non-null unless the metadata
   * is nullable. A future ({@code typeName == "Promise"}) maps to its result type with the result's nullability.
   *
   * @param typeMetadata the type
   * @param inputType whether an input type is generated
   * @return the type
   */
  public GraphQLType getOrCreateGraphQLType(TypeMetadata typeMetadata, boolean inputType) {
    if ("Promise".equals(typeMetadata.typeName())) {
      return getOrCreateGraphQLType(typeMetadata.parameters().getFirst(), inputType);
    }
    String name = getGraphQLName(typeMetadata, inputType);

    // Check if GraphQL type already exists and reuse
    if (name != null && graphQLTypes.containsKey(name)) {
      logger.debug("Found existing GraphQL " + direction(inputType) + " type for name " + name);
      return withNullability(graphQLTypes.get(name), typeMetadata.isNullable());
    }
    if (name != null && typesInProgress.contains(name)) {
      return withNullability(GraphQLTypeReference.typeRef(name), typeMetadata.isNullable());
    }

    // Create new GraphQL type from metadata
    GraphQLType createdGraphQLType = createGraphQLType(typeMetadata, inputType);

    // Store created GraphQL type to reuse
    if (name != null) {
      graphQLTypes.put(name, createdGraphQLType);
    }
    return withNullability(createdGraphQLType, typeMetadata.isNullable());
  }

  /**
   * {@code getOrCreateGraphQLType(typeMetadata)} (output type).
   *
   * @param typeMetadata the type
   * @return the type
   */
  public GraphQLType getOrCreateGraphQLType(TypeMetadata typeMetadata) {
    return getOrCreateGraphQLType(typeMetadata, false);
  }

  private static GraphQLType withNullability(GraphQLType type, boolean nullable) {
    return nullable ? type : GraphQLNonNull.nonNull(type);
  }

  private static String direction(boolean inputType) {
    return inputType ? "input" : "output";
  }

  private String getGraphQLName(TypeMetadata typeMetadata, boolean inputType) {
    if (typeMetadata.name().equals("UUID") || typeMetadata.name().equals("Date")) {
      // UUID is a class but should result in a scalar which doesn't need a separate input type
      // Date is matched by name too (it has no `typeName`)
      return typeMetadata.name();
    }
    if (typeMetadata.typeGroup() == TypeGroup.ARRAY || typeMetadata.typeGroup() == TypeGroup.READONLY_ARRAY) {
      return getGraphQLName(typeMetadata.parameters().getFirst(), inputType) + "List" + (inputType ? "Input" : "");
    }
    if (typeMetadata.typeName() != null && typeMetadata.typeGroup() == TypeGroup.CLASS) {
      return typeMetadata.typeName() + (inputType ? "Input" : "");
    }
    if (typeMetadata.typeGroup() == TypeGroup.UNION) {
      return typeMetadata.name();
    }
    return typeMetadata.typeName() == null || typeMetadata.typeName().isEmpty() ? null : typeMetadata.typeName();
  }

  private GraphQLType createGraphQLType(TypeMetadata typeMetadata, boolean inputType) {
    logger.debug("Creating GraphQL " + direction(inputType) + " type for type " + typeMetadata.name());
    String name = typeMetadata.name();
    TypeGroup typeGroup = typeMetadata.typeGroup();

    if (name.equals("Date")) {
      return Common.dateScalar();
    }
    if (name.equals("UUID")) {
      return Scalars.GraphQLID;
    }
    if (typeGroup == TypeGroup.STRING) {
      return Scalars.GraphQLString;
    }
    if (typeGroup == TypeGroup.NUMBER) {
      return Scalars.GraphQLFloat;
    }
    if (typeGroup == TypeGroup.BOOLEAN) {
      return Scalars.GraphQLBoolean;
    }
    if (typeGroup == TypeGroup.ENUM) {
      return createEnumType(typeMetadata);
    }
    if (typeGroup == TypeGroup.ARRAY || typeGroup == TypeGroup.READONLY_ARRAY) {
      return createArrayType(typeMetadata, inputType);
    }
    if (typeGroup == TypeGroup.CLASS && typeMetadata.type() != null && !Common.isExternalType(typeMetadata)) {
      ClassMetadata metadata = ClassMetadata.of(typeMetadata.type());
      return createObjectType(metadata, inputType, null);
    }
    if (typeGroup == TypeGroup.UNION && !Common.isExternalType(typeMetadata) && !inputType
        && validateUnionClasses(typeMetadata.parameters())) {
      List<GraphQLType> graphQLUnionClasses = getUnionClasses(typeMetadata);
      GraphQLUnionType.Builder union = GraphQLUnionType.newUnionType().name(typeMetadata.name());
      for (GraphQLType member : graphQLUnionClasses) {
        if (member instanceof GraphQLObjectType objectType) {
          union.possibleType(objectType);
        } else {
          union.possibleType((GraphQLTypeReference) member);
        }
      }
      typeResolvers.put(typeMetadata.name(), environment -> {
        Object value = environment.getObject();
        return value == null ? null : environment.getSchema().getObjectType(value.getClass().getSimpleName());
      });
      return union.build();
    }
    return Common.jsonScalar();
  }

  private static boolean validateUnionClasses(List<TypeMetadata> params) {
    return params.stream().allMatch(typeMetadata -> typeMetadata.typeGroup() == TypeGroup.CLASS
        && typeMetadata.type() != null && !Common.isExternalType(typeMetadata));
  }

  private List<GraphQLType> getUnionClasses(TypeMetadata typeMetadata) {
    return typeMetadata.parameters().stream().map(param -> {
      if (param.typeGroup() == TypeGroup.CLASS && param.type() != null && !Common.isExternalType(typeMetadata)) {
        ClassMetadata metadata = ClassMetadata.of(param.type());
        return getOrCreateObjectTypeForUnion(metadata);
      }
      throw new IllegalStateException("Union type " + typeMetadata.name() + " can only contain classes");
    }).toList();
  }

  private GraphQLType createEnumType(TypeMetadata typeMetadata) {
    GraphQLEnumType.Builder builder = GraphQLEnumType.newEnum().name(typeMetadata.name());
    for (TypeMetadata constant : typeMetadata.parameters()) {
      // graphql-js defaults the internal value of `{ [name]: {} }` to the name
      builder.value(constant.name(), constant.name());
    }
    return builder.build();
  }

  private GraphQLType createArrayType(TypeMetadata typeMetadata, boolean inputType) {
    TypeMetadata param = typeMetadata.parameters().getFirst();
    GraphQLType graphQLPropType = getOrCreateGraphQLType(param, inputType);
    return GraphQLList.list(graphQLPropType);
  }

  private GraphQLType getOrCreateObjectType(ClassMetadata classMetadata, boolean inputType, List<String> excludeProps) {
    String typeName = classMetadata.name() + (inputType ? "Input" : "");
    if (graphQLTypes.containsKey(typeName)) {
      return graphQLTypes.get(typeName);
    }
    if (typesInProgress.contains(typeName)) {
      return GraphQLTypeReference.typeRef(typeName);
    }
    GraphQLType createdGraphQLType = createObjectType(classMetadata, inputType, excludeProps);
    graphQLTypes.put(typeName, createdGraphQLType);
    return createdGraphQLType;
  }

  private GraphQLType createObjectType(ClassMetadata classMetadata, boolean inputType, List<String> excludeProps) {
    List<PropertyMetadata> finalFields = Common.nonExcludedFields(classMetadata.fields(), excludeProps);
    if (inputType) {
      String typeName = classMetadata.name() + "Input";
      typesInProgress.add(typeName);
      try {
        GraphQLInputObjectType.Builder builder = GraphQLInputObjectType.newInputObject().name(typeName);
        for (PropertyMetadata prop : finalFields) {
          logger.debug("Get or create GraphQL input type for property " + prop.name());
          builder.field(GraphQLInputObjectField.newInputObjectField()
              .name(prop.name())
              .type(Common.asInputType(getOrCreateGraphQLType(prop.typeInfo(), true)))
              .build());
        }
        return builder.build();
      } finally {
        typesInProgress.remove(typeName);
      }
    }
    return createOutputObjectType(classMetadata, excludeProps);
  }

  private GraphQLType getOrCreateObjectTypeForUnion(ClassMetadata classMetadata) {
    String typeName = classMetadata.name();
    if (graphQLTypes.containsKey(typeName)) {
      return graphQLTypes.get(typeName);
    }
    if (typesInProgress.contains(typeName)) {
      return GraphQLTypeReference.typeRef(typeName);
    }
    GraphQLType createdGraphQLType = createOutputObjectType(classMetadata, null);
    graphQLTypes.put(typeName, createdGraphQLType);
    return createdGraphQLType;
  }

  private GraphQLNamedOutputType createOutputObjectType(ClassMetadata classMetadata, List<String> excludeProps) {
    List<PropertyMetadata> finalFields = Common.nonExcludedFields(classMetadata.fields(), excludeProps);
    String typeName = classMetadata.name();
    typesInProgress.add(typeName);
    try {
      GraphQLObjectType.Builder builder = GraphQLObjectType.newObject().name(typeName);
      for (PropertyMetadata prop : finalFields) {
        logger.debug("Get or create GraphQL output type for property " + prop.name());
        builder.field(field -> field
            .name(prop.name())
            .type(Common.asOutputType(getOrCreateGraphQLType(prop.typeInfo(), false))));
      }
      return builder.build();
    } finally {
      typesInProgress.remove(typeName);
    }
  }
}
