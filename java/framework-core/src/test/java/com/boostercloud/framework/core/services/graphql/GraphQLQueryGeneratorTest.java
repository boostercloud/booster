package com.boostercloud.framework.core.services.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.EntityMetadata;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import graphql.Scalars;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** Port of {@code test/services/graphql/graphql-query-generator.test.ts}. */
class GraphQLQueryGeneratorTest {

  private final BoosterConfig simpleConfig = new BoosterConfig("test");
  private final GraphQLScalarType mockGraphQLType = Scalars.GraphQLString;
  private GraphQLTypeInformer mockTypeInformer;
  private ResolverBuilder mockByIdResolverBuilder;
  private ResolverBuilder mockQueryResolverBuilder;
  private ResolverBuilder mockFilterResolverBuilder;
  private GraphQLFieldResolver mockEventsResolver;
  private final Map<String, TargetTypeMetadata> queryHandlers = new LinkedHashMap<>();

  public record Widget() {}

  public record BooleanWidget(boolean _aflag) {}

  public record NumberWidget(double _anumber) {}

  public record StringWidget(String _astring) {}

  @BeforeEach
  void setUp() {
    mockTypeInformer = mock(GraphQLTypeInformer.class);
    mockByIdResolverBuilder = mock(ResolverBuilder.class);
    mockQueryResolverBuilder = mock(ResolverBuilder.class);
    mockFilterResolverBuilder = mock(ResolverBuilder.class);
    mockEventsResolver = mock(GraphQLFieldResolver.class);
    when(mockTypeInformer.getOrCreateGraphQLType(any(), anyBoolean())).thenReturn(mockGraphQLType);
    when(mockTypeInformer.generateGraphQLTypeForClass(any(), any())).thenReturn(mockGraphQLType);
    when(mockTypeInformer.generateGraphQLTypeForClass(any(), any(), anyBoolean())).thenReturn(mockGraphQLType);
  }

  private GraphQLQueryGenerator generatorFor(BoosterConfig config, List<Class<?>> readModels) {
    return new GraphQLQueryGenerator(config, readModels, queryHandlers, mockTypeInformer, mockByIdResolverBuilder,
        mockQueryResolverBuilder, mockFilterResolverBuilder, mockEventsResolver, null);
  }

  @Nested
  class WithOneTargetType {

    @Test
    void callsTheTypeInformerThriceWithTheReadModelClass() {
      generatorFor(simpleConfig, List.of(Widget.class)).generate();

      verify(mockTypeInformer, times(3)).generateGraphQLTypeForClass(Widget.class, null);
    }

    @Test
    void callsTheByIdResolverBuilderOnceAfterTheTypeInformer() {
      generatorFor(simpleConfig, List.of(Widget.class)).generate();

      InOrder order = inOrder(mockTypeInformer, mockByIdResolverBuilder);
      order.verify(mockTypeInformer).generateGraphQLTypeForClass(Widget.class, null);
      order.verify(mockByIdResolverBuilder).build(Widget.class);
      verify(mockByIdResolverBuilder, times(1)).build(any());
    }

    @Test
    void returnsTheExpectedResult() {
      GeneratedObjectType result = generatorFor(simpleConfig, List.of(Widget.class)).generate();

      assertThat(result.name()).isEqualTo("Query");
      assertThat(result.type().getDescription()).isNull();
      GraphQLFieldConfig byId = result.fields().get("Widget");
      assertThat(byId.type()).isSameAs(mockGraphQLType);
      assertThat(byId.resolve()).isNull();
      assertThat(byId.subscribe()).isNull();
      assertThat(byId.deprecationReason()).isNull();
      GraphQLFieldConfig plural = result.fields().get("Widgets");
      assertThat(Common.printType(plural.type())).isEqualTo("[String!]!");
      assertThat(plural.resolve()).isNull();
      assertThat(plural.subscribe()).isNull();
      assertThat(plural.deprecationReason()).isEqualTo("Method is deprecated. Use List* methods");
      assertThat(result.type().getFieldDefinition("Widgets").getDeprecationReason())
          .isEqualTo("Method is deprecated. Use List* methods");
    }

    @Test
    void callsTheTypeInformerThriceForAReadModelWithAScalarProperty() {
      generatorFor(simpleConfig, List.of(BooleanWidget.class)).generate();

      verify(mockTypeInformer, times(3)).generateGraphQLTypeForClass(BooleanWidget.class, null);
      verify(mockTypeInformer, never()).generateGraphQLTypeForClass(any(), any(), anyBoolean());
    }

    @Test
    void hasTheExpectedArgumentsForABooleanProperty() {
      GeneratedObjectType result = generatorFor(simpleConfig, List.of(BooleanWidget.class)).generate();

      assertThat(Common.printType(result.fields().get("BooleanWidget").args().get("id"))).isEqualTo("ID!");
      GraphQLInputObjectType filterType = propertyFilter(result, "BooleanWidgets", "_aflag");
      assertThat(filterType.getName()).isEqualTo("BooleanPropertyFilter");
      assertThat(filterType.getFields()).extracting(GraphQLInputObjectField::getName)
          .containsExactly("eq", "ne", "isDefined");
      assertThat(filterType.getFields()).allSatisfy(field -> {
        assertThat(Common.printType(field.getType())).isEqualTo("Boolean");
        assertThat(field.getDescription()).isNull();
        assertThat(field.hasSetDefaultValue()).isFalse();
      });
      assertSortBy(result, "BooleanWidget");
    }

    @Test
    void hasTheExpectedArgumentsForANumberProperty() {
      GeneratedObjectType result = generatorFor(simpleConfig, List.of(NumberWidget.class)).generate();

      assertThat(Common.printType(result.fields().get("NumberWidget").args().get("id"))).isEqualTo("ID!");
      GraphQLInputObjectType filterType = propertyFilter(result, "NumberWidgets", "_anumber");
      assertThat(filterType.getName()).isEqualTo("NumberPropertyFilter");
      assertThat(filterType.getFields()).extracting(GraphQLInputObjectField::getName)
          .containsExactly("eq", "ne", "lte", "lt", "gte", "gt", "in", "isDefined");
      assertThat(filterType.getFields()).allSatisfy(field -> assertThat(Common.printType(field.getType()))
          .isEqualTo(switch (field.getName()) {
            case "in" -> "[Float]";
            case "isDefined" -> "Boolean";
            default -> "Float";
          }));
      assertSortBy(result, "NumberWidget");
    }

    @Test
    void hasTheExpectedArgumentsForAStringProperty() {
      GeneratedObjectType result = generatorFor(simpleConfig, List.of(StringWidget.class)).generate();

      assertThat(Common.printType(result.fields().get("StringWidget").args().get("id"))).isEqualTo("ID!");
      GraphQLInputObjectType filterType = propertyFilter(result, "StringWidgets", "_astring");
      assertThat(filterType.getName()).isEqualTo("StringPropertyFilter");
      assertThat(filterType.getFields()).extracting(GraphQLInputObjectField::getName)
          .containsExactly("eq", "ne", "lte", "lt", "gte", "gt", "in", "beginsWith", "contains", "regex", "iRegex",
              "isDefined");
      assertThat(filterType.getFields()).allSatisfy(field -> assertThat(Common.printType(field.getType()))
          .isEqualTo(switch (field.getName()) {
            case "in" -> "[String]";
            case "isDefined" -> "Boolean";
            default -> "String";
          }));
      assertSortBy(result, "StringWidget");
    }

    private GraphQLInputObjectType propertyFilter(GeneratedObjectType result, String queryName, String property) {
      GraphQLInputObjectType filter = (GraphQLInputObjectType) result.fields().get(queryName).args().get("filter");
      return (GraphQLInputObjectType) filter.getField(property).getType();
    }

    private void assertSortBy(GeneratedObjectType result, String readModelName) {
      GraphQLInputObjectType sortBy = (GraphQLInputObjectType) result.fields()
          .get("List" + readModelName + "s").args().get("sortBy");
      assertThat(sortBy.getName()).isEqualTo(readModelName + "SortBy");
      assertThat(sortBy.getFields()).isNotEmpty().allSatisfy(field -> {
        assertThat(Common.printType(field.getType())).isEqualTo("orderProperty");
        assertThat(((GraphQLEnumType) field.getType()).getValues()).extracting(GraphQLEnumValueDefinition::getName)
            .containsExactly("ASC", "DESC");
      });
    }
  }

  @Nested
  class WithAConfigWithEventsAndEntities {

    private final List<String> entityNames = List.of("TestEntity1", "TestEntity2", "TestEntity3");
    private final List<String> eventTypeNames = List.of("EventType1", "EventType2", "EventType3");
    private GeneratedObjectType result;

    @BeforeEach
    void generate() {
      BoosterConfig configWithEventsAndEntities = new BoosterConfig("test");
      entityNames.forEach(name -> configWithEventsAndEntities.entities.put(name, new EntityMetadata(Object.class,
          null)));
      eventTypeNames.forEach(name -> configWithEventsAndEntities.reducers.put(name, new ReducerMetadata(Object.class,
          "reduce")));
      result = generatorFor(configWithEventsAndEntities, List.of(Widget.class)).generate();
    }

    @Test
    void returnsTheExpectedQueries() {
      assertThat(result.name()).isEqualTo("Query");
      assertThat(result.fields().keySet())
          .containsExactly("Widget", "Widgets", "ListWidgets", "eventsByEntity", "eventsByType");
    }

    @Test
    void searchesEventsByEntity() {
      GraphQLFieldConfig eventsByEntity = result.fields().get("eventsByEntity");

      assertThat(eventsByEntity.args().keySet()).containsExactly("entity", "entityID", "from", "to", "limit");
      GraphQLEnumType entityEnumType = (GraphQLEnumType) ((GraphQLNonNull) eventsByEntity.args().get("entity"))
          .getWrappedType();
      assertThat(entityEnumType.getName()).isEqualTo("EntityType");
      assertThat(entityEnumType.getValues()).extracting(GraphQLEnumValueDefinition::getValue)
          .containsExactlyElementsOf(entityNames);
      assertThat(Common.printType(eventsByEntity.args().get("entityID"))).isEqualTo("ID");
      assertThat(Common.printType(eventsByEntity.args().get("from"))).isEqualTo("String");
      assertThat(Common.printType(eventsByEntity.args().get("limit"))).isEqualTo("Int");
      assertThat(eventsByEntity.resolve()).isSameAs(mockEventsResolver);
      assertEventSearchQueryReturnType(eventsByEntity.type());
    }

    @Test
    void searchesEventsByType() {
      GraphQLFieldConfig eventsByType = result.fields().get("eventsByType");

      assertThat(eventsByType.args().keySet()).containsExactly("type", "from", "to", "limit");
      GraphQLEnumType eventTypeEnumType = (GraphQLEnumType) ((GraphQLNonNull) eventsByType.args().get("type"))
          .getWrappedType();
      assertThat(eventTypeEnumType.getName()).isEqualTo("EventType");
      assertThat(eventTypeEnumType.getValues()).extracting(GraphQLEnumValueDefinition::getValue)
          .containsExactlyElementsOf(eventTypeNames);
      assertEventSearchQueryReturnType(eventsByType.type());
    }

    private void assertEventSearchQueryReturnType(GraphQLOutputType queryReturnType) {
      assertThat(queryReturnType).isInstanceOf(GraphQLList.class);
      GraphQLObjectType returnElementType = (GraphQLObjectType) ((GraphQLList) queryReturnType).getWrappedType();
      assertThat(returnElementType.getName()).isEqualTo("EventQueryResponse");
      assertThat(returnElementType.getFieldDefinitions()).extracting(field -> field.getName())
          .containsExactlyInAnyOrder("type", "entity", "requestID", "entityID", "user", "createdAt", "value",
              "deletedAt");
      GraphQLObjectType userType = (GraphQLObjectType) returnElementType.getFieldDefinition("user").getType();
      assertThat(userType.getFieldDefinitions()).extracting(field -> field.getName())
          .containsExactly("id", "username", "roles");
    }
  }

  @Nested
  class WithoutTargetTypes {

    @Test
    void doesNotCallTheTypeInformer() {
      generatorFor(simpleConfig, List.of()).generate();

      verify(mockTypeInformer, never()).generateGraphQLTypeForClass(any(), any());
      verify(mockTypeInformer, never()).generateGraphQLTypeForClass(any(), any(), anyBoolean());
      verify(mockTypeInformer, never()).getOrCreateGraphQLType(any(), anyBoolean());
    }

    @Test
    void returnsOnlyTheEventQueries() {
      GeneratedObjectType result = generatorFor(simpleConfig, List.of()).generate();

      assertThat(result.name()).isEqualTo("Query");
      assertThat(result.fields().keySet()).containsExactly("eventsByEntity", "eventsByType");
    }
  }

  @Nested
  class WithUserQueries {

    @Test
    void addsTheQueriesLastAndLetsThemReplaceGeneratedOnes() {
      queryHandlers.put("Widgets", TargetTypeMetadata.of(queryMetadata(GraphQLTestApp.GetStock.class)));
      queryHandlers.put("GetStock", TargetTypeMetadata.of(queryMetadata(GraphQLTestApp.GetStock.class)));
      GraphQLFieldResolver queryResolver = mock(GraphQLFieldResolver.class);
      when(mockQueryResolverBuilder.build(GraphQLTestApp.GetStock.class)).thenReturn(queryResolver);

      GeneratedObjectType result = generatorFor(simpleConfig, List.of(Widget.class)).generate();

      assertThat(result.fields().keySet())
          .containsExactly("Widget", "Widgets", "ListWidgets", "eventsByEntity", "eventsByType", "GetStock");
      assertThat(result.fields().get("Widgets").resolve()).isSameAs(queryResolver);
      assertThat(result.fields().get("Widgets").deprecationReason()).isNull();
    }

    private com.boostercloud.framework.types.concepts.QueryMetadata queryMetadata(Class<?> type) {
      com.boostercloud.metadata.ClassMetadata metadata = com.boostercloud.metadata.ClassMetadata.of(type);
      return new com.boostercloud.framework.types.concepts.QueryMetadata(type, metadata.fields(), metadata.methods(),
          null, List.of());
    }
  }
}
