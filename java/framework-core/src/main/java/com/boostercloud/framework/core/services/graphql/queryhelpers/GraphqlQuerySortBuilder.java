package com.boostercloud.framework.core.services.graphql.queryhelpers;

import com.boostercloud.framework.core.services.graphql.Common;
import com.boostercloud.framework.core.services.graphql.GraphQLTypeInformer;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.metadata.ClassMetadata;
import com.boostercloud.metadata.PropertyMetadata;
import com.boostercloud.metadata.TypeGroup;
import com.boostercloud.metadata.TypeMetadata;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLTypeReference;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Port of {@code services/graphql/query-helpers/graphql-query-sort-builder.ts}: the per-property sort input types.
 * Every property sorts with {@code enum orderProperty { ASC DESC }}, except properties whose type is an application
 * class (not generic) with properties, which get a nested {@code <Class>PropertySortBy} input whose fields follow the
 * same rule.
 */
public class GraphqlQuerySortBuilder {

  private final GraphQLTypeInformer typeInformer;
  private final BoosterConfig config;
  private final Map<String, GraphQLInputObjectType> generatedSortByByTypeName = new LinkedHashMap<>();
  private final GraphQLEnumType orderType = Common.buildGraphqlSimpleEnumFor("orderProperty", List.of("ASC", "DESC"));
  private final Set<String> sortsInProgress = new HashSet<>();

  /**
   * @param typeInformer the type informer
   * @param config the configuration
   */
  public GraphqlQuerySortBuilder(GraphQLTypeInformer typeInformer, BoosterConfig config) {
    this.typeInformer = typeInformer;
    this.config = config;
  }

  /**
   * {@code generateSortArguments(type, excludeProps)}: one sort per property that is not excluded and not a calculated
   * field.
   *
   * @param type the class
   * @param excludeProps properties to leave out, or {@code null}
   * @return the sort type of each property, by property name, in property order
   */
  public Map<String, GraphQLInputType> generateSortArguments(Class<?> type, List<String> excludeProps) {
    ClassMetadata metadata = ClassMetadata.of(type);
    Map<String, GraphQLInputType> args = new LinkedHashMap<>();
    Common.nonExcludedFields(metadata.fields(), excludeProps).stream()
        .filter(field -> !field.typeInfo().isGetAccessor())
        .forEach(prop -> args.put(prop.name(), generateSortFor(prop)));
    return args;
  }

  private GraphQLInputType generateSortFor(PropertyMetadata prop) {
    TypeMetadata typeInfo = prop.typeInfo();
    String sortByName = typeInfo.name() + "PropertySortBy";
    sortByName = sortByName.substring(0, 1).toUpperCase() + sortByName.substring(1).replace("[]", "");

    if (generatedSortByByTypeName.containsKey(sortByName)) {
      return generatedSortByByTypeName.get(sortByName);
    }
    if (sortsInProgress.contains(sortByName)) {
      return GraphQLTypeReference.typeRef(sortByName);
    }
    // TypeScript: `!type || typeof type === 'object'` (enums are objects at run time)
    if (typeInfo.type() == null || typeInfo.type().isEnum()) {
      return orderType;
    }
    if (typeInfo.typeGroup() == TypeGroup.ARRAY) {
      return orderType;
    }
    if (typeInfo.name().equals("UUID") || typeInfo.name().equals("Date")) {
      return orderType;
    }

    if (typeInfo.typeGroup() == TypeGroup.CLASS) {
      if (Common.isExternalType(typeInfo)) {
        return orderType;
      }
      ClassMetadata metadata = ClassMetadata.of(typeInfo.type());
      if (metadata.fields().isEmpty()) {
        return orderType;
      }
      // As in TypeScript, the non-exposed properties are looked up by the property name
      List<String> excludeProps = config.nonExposedGraphQLMetadataKey.get(prop.name());
      typeInformer.generateGraphQLTypeForClass(typeInfo.type(), excludeProps, true);

      Map<String, GraphQLInputType> nestedProperties = new LinkedHashMap<>();
      sortsInProgress.add(sortByName);
      try {
        for (PropertyMetadata nestedProp : metadata.fields()) {
          nestedProperties.put(nestedProp.name(), generateSortFor(nestedProp));
        }
      } finally {
        sortsInProgress.remove(sortByName);
      }
      GraphQLInputObjectType sortBy = Common.inputObjectType(sortByName, nestedProperties);
      generatedSortByByTypeName.put(sortByName, sortBy);
      return sortBy;
    }
    return orderType;
  }
}
