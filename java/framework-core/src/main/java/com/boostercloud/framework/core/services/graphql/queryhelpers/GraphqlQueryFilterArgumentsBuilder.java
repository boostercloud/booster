package com.boostercloud.framework.core.services.graphql.queryhelpers;

import com.boostercloud.framework.core.services.graphql.Common;
import com.boostercloud.framework.core.services.graphql.GraphQLTypeInformer;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.metadata.ClassMetadata;
import com.boostercloud.metadata.PropertyMetadata;
import com.boostercloud.metadata.TypeGroup;
import com.boostercloud.metadata.TypeMetadata;
import graphql.Scalars;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLTypeUtil;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Port of {@code services/graphql/query-helpers/graphql-query-filter-arguments-builder.ts}: the per-property filter
 * input types ({@code StringPropertyFilter}, {@code UUIDPropertyFilter}, {@code <Class>PropertyFilter},
 * {@code <T>ArrayPropertyFilter}...), cached by name in the map shared by every query and subscription of the schema.
 *
 * <p>The filter of a property is named after its metadata name with every character that is not {@code _} or an
 * ASCII letter replaced by {@code _} and the first letter upper-cased ({@code string} → {@code StringPropertyFilter},
 * {@code Page<Cart>} → {@code Page_Cart_PropertyFilter}). Unions, maps, interfaces, functions, external classes and
 * classes without properties are filtered as {@code JSON}.
 */
public class GraphqlQueryFilterArgumentsBuilder {

  private final GraphQLTypeInformer typeInformer;
  private final Map<String, GraphQLInputObjectType> generatedFiltersByTypeName;
  private final BoosterConfig config;
  private final Set<String> filtersInProgress = new HashSet<>();

  /**
   * @param typeInformer the type informer
   * @param generatedFiltersByTypeName shared cache of filter input types ({@code null} = a new one)
   * @param config the configuration
   */
  public GraphqlQueryFilterArgumentsBuilder(GraphQLTypeInformer typeInformer,
      Map<String, GraphQLInputObjectType> generatedFiltersByTypeName, BoosterConfig config) {
    this.typeInformer = typeInformer;
    this.generatedFiltersByTypeName = generatedFiltersByTypeName == null ? new LinkedHashMap<>()
        : generatedFiltersByTypeName;
    this.config = config;
  }

  /**
   * {@code generateFilterArguments(type, excludeProps)}: one filter per property of the class that is not excluded
   * and not a calculated field.
   *
   * @param type the class
   * @param excludeProps properties to leave out, or {@code null}
   * @return the filter type of each property, by property name, in property order
   */
  public Map<String, GraphQLInputType> generateFilterArguments(Class<?> type, List<String> excludeProps) {
    ClassMetadata metadata = ClassMetadata.of(type);
    Map<String, GraphQLInputType> args = new LinkedHashMap<>();
    Common.nonExcludedFields(metadata.fields(), excludeProps).stream()
        .filter(field -> !field.typeInfo().isGetAccessor())
        .forEach(prop -> args.put(prop.name(), generateFilterFor(prop)));
    return args;
  }

  /**
   * The filter name of a type name: {@code <name><suffix>} with every character that is not {@code _} or an ASCII
   * letter replaced by {@code _} and the first character upper-cased.
   *
   * @param typeName the metadata name
   * @param suffix {@code PropertyFilter} or {@code ArrayPropertyFilter}
   * @return the filter name
   */
  static String filterName(String typeName, String suffix) {
    String filterName = (typeName + suffix).replaceAll("[^_a-zA-Z]", "_");
    return filterName.substring(0, 1).toUpperCase() + filterName.substring(1);
  }

  private GraphQLInputType generateFilterFor(PropertyMetadata prop) {
    TypeMetadata typeInfo = prop.typeInfo();
    String filterName = filterName(typeInfo.name(), "PropertyFilter");

    if (generatedFiltersByTypeName.containsKey(filterName)) {
      return generatedFiltersByTypeName.get(filterName);
    }
    if (filtersInProgress.contains(filterName)) {
      return GraphQLTypeReference.typeRef(filterName);
    }
    if (typeInfo.typeGroup() == TypeGroup.ARRAY) {
      return generateArrayFilterFor(prop);
    }
    Map<String, GraphQLInputType> fields;

    if (typeInfo.name().equals("UUID") || typeInfo.name().equals("Date")) {
      fields = generateFilterInputTypes(typeInfo);
    } else if (typeInfo.type() != null
        && (typeInfo.typeGroup() == TypeGroup.CLASS || typeInfo.typeGroup() == TypeGroup.OBJECT)) {
      if (Common.isExternalType(typeInfo)) {
        return Common.jsonScalar();
      }
      ClassMetadata metadata = ClassMetadata.of(typeInfo.type());
      if (metadata.fields().isEmpty()) {
        return Common.jsonScalar();
      }
      // As in TypeScript, the non-exposed properties are looked up by the property name
      List<String> excludeProps = config.nonExposedGraphQLMetadataKey.get(prop.name());
      typeInformer.generateGraphQLTypeForClass(typeInfo.type(), excludeProps, true);

      filtersInProgress.add(filterName);
      try {
        fields = new LinkedHashMap<>();
        for (PropertyMetadata nestedProp : metadata.fields()) {
          fields.put(nestedProp.name(), generateFilterFor(nestedProp));
        }
      } finally {
        filtersInProgress.remove(filterName);
      }
      GraphQLTypeReference self = GraphQLTypeReference.typeRef(filterName);
      fields.put("and", GraphQLList.list(self));
      fields.put("or", GraphQLList.list(self));
      fields.put("not", self);
      fields.put("isDefined", Scalars.GraphQLBoolean);
    } else if (typeInfo.type() != null && typeInfo.type() != Object.class) {
      fields = generateFilterInputTypes(typeInfo);
    } else {
      return Common.jsonScalar();
    }
    GraphQLInputObjectType filter = Common.inputObjectType(filterName, fields);
    generatedFiltersByTypeName.put(filterName, filter);
    return filter;
  }

  private GraphQLInputObjectType generateArrayFilterFor(PropertyMetadata property) {
    String filterName = filterName(property.typeInfo().parameters().getFirst().name(), "ArrayPropertyFilter");

    if (!generatedFiltersByTypeName.containsKey(filterName)) {
      Map<String, GraphQLInputType> propFilters = new LinkedHashMap<>();
      for (TypeMetadata param : property.typeInfo().parameters()) {
        GraphQLScalarType graphqlType = switch (param.typeGroup()) {
          case BOOLEAN -> Scalars.GraphQLBoolean;
          case STRING -> Scalars.GraphQLString;
          case NUMBER -> Scalars.GraphQLFloat;
          default -> param.type() == UUID.class ? Scalars.GraphQLID : Common.jsonScalar();
        };
        propFilters.put("includes", graphqlType);
      }
      propFilters.put("isDefined", Scalars.GraphQLBoolean);
      generatedFiltersByTypeName.put(filterName, Common.inputObjectType(filterName, propFilters));
    }
    return generatedFiltersByTypeName.get(filterName);
  }

  private Map<String, GraphQLInputType> generateFilterInputTypes(TypeMetadata typeMetadata) {
    String name = typeMetadata.name();
    TypeGroup typeGroup = typeMetadata.typeGroup();
    Map<String, GraphQLInputType> fields = new LinkedHashMap<>();
    if (typeGroup == TypeGroup.BOOLEAN) {
      fields.put("eq", Scalars.GraphQLBoolean);
      fields.put("ne", Scalars.GraphQLBoolean);
      fields.put("isDefined", Scalars.GraphQLBoolean);
      return fields;
    }
    if (typeGroup == TypeGroup.NUMBER) {
      for (String operation : List.of("eq", "ne", "lte", "lt", "gte", "gt")) {
        fields.put(operation, Scalars.GraphQLFloat);
      }
      fields.put("in", GraphQLList.list(Scalars.GraphQLFloat));
      fields.put("isDefined", Scalars.GraphQLBoolean);
      return fields;
    }
    if (typeGroup == TypeGroup.STRING) {
      for (String operation : List.of("eq", "ne", "lte", "lt", "gte", "gt")) {
        fields.put(operation, Scalars.GraphQLString);
      }
      fields.put("in", GraphQLList.list(Scalars.GraphQLString));
      fields.put("beginsWith", Scalars.GraphQLString);
      fields.put("contains", Scalars.GraphQLString);
      fields.put("regex", Scalars.GraphQLString);
      fields.put("iRegex", Scalars.GraphQLString);
      fields.put("isDefined", Scalars.GraphQLBoolean);
      return fields;
    }
    // use `name`, `typeGroup` === 'Class'
    if (name.equals("UUID")) {
      fields.put("eq", Scalars.GraphQLID);
      fields.put("ne", Scalars.GraphQLID);
      fields.put("in", GraphQLList.list(Scalars.GraphQLID));
      fields.put("beginsWith", Scalars.GraphQLString);
      fields.put("contains", Scalars.GraphQLString);
      fields.put("isDefined", Scalars.GraphQLBoolean);
      return fields;
    }
    // use `name`, `typeGroup` === 'Interface'.
    if (name.equals("Date")) {
      for (String operation : List.of("eq", "ne", "lte", "lt", "gte", "gt")) {
        fields.put(operation, Common.dateScalar());
      }
      fields.put("in", GraphQLList.list(Common.dateScalar()));
      fields.put("isDefined", Scalars.GraphQLBoolean);
      return fields;
    }
    if (typeGroup == TypeGroup.ENUM) {
      // Java port: the nullable enum type (TypeScript keeps the non-null wrapper of a non-nullable property, which
      // makes both `eq` and `ne` mandatory)
      GraphQLInputType enumType = Common.asInputType(
          GraphQLTypeUtil.unwrapNonNull(typeInformer.getOrCreateGraphQLType(typeMetadata, true)));
      fields.put("eq", enumType);
      fields.put("ne", enumType);
      fields.put("isDefined", Scalars.GraphQLBoolean);
      return fields;
    }
    throw new IllegalStateException("Type " + name + " is not supported in search filters");
  }
}
