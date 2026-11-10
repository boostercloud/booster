package com.boostercloud.framework.core.services.graphql.queryhelpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.services.graphql.Common;
import com.boostercloud.framework.core.services.graphql.GraphQLFieldConfig;
import com.boostercloud.framework.core.services.graphql.GraphQLFieldResolver;
import com.boostercloud.framework.core.services.graphql.GraphQLTypeInformer;
import com.boostercloud.framework.core.services.graphql.TargetTypeMetadata;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.TimeKey;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.metadata.ClassMetadata;
import graphql.Scalars;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The filter, sort and handled-field builders ({@code query-helpers/*.ts}), which the TypeScript unit tests only cover
 * through the query generator (the integration tests {@code schema.filter/sort/command.integration.ts} pin them).
 */
class GraphQLQueryHelpersTest {

  public record BaseClass(String base) {}

  public record GenericClass<T>(T genericValue) {}

  public enum Size {
    SMALL,
    LARGE
  }

  public record SchemaReadModel(
      UUID id,
      String text,
      double number,
      boolean flag,
      Instant date,
      List<String> strings,
      List<UUID> ids,
      List<BaseClass> children,
      BaseClass child,
      GenericClass<String> generic,
      Map<String, Object> record,
      Size size,
      TimeKey timeKey,
      Object anything,
      Optional<java.time.Duration> external) {

    @com.boostercloud.framework.core.decorators.CalculatedField
    public String calculated() {
      return text;
    }
  }

  private BoosterConfig config;
  private GraphQLTypeInformer typeInformer;
  private Map<String, GraphQLInputObjectType> filters;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    typeInformer = new GraphQLTypeInformer(Logger.NOOP);
    filters = new LinkedHashMap<>();
  }

  private static String name(GraphQLInputType type) {
    return Common.printType(type);
  }

  @Nested
  class FilterArguments {

    private Map<String, GraphQLInputType> arguments() {
      return new GraphqlQueryFilterArgumentsBuilder(typeInformer, filters, config)
          .generateFilterArguments(SchemaReadModel.class, null);
    }

    @Test
    void generatesOneFilterPerStoredPropertyInOrder() {
      assertThat(arguments().keySet()).containsExactly("id", "text", "number", "flag", "date", "strings", "ids",
          "children", "child", "generic", "record", "size", "timeKey", "anything", "external");
    }

    @Test
    void namesTheFiltersAfterTheTypeNames() {
      Map<String, GraphQLInputType> arguments = arguments();

      assertThat(name(arguments.get("id"))).isEqualTo("UUIDPropertyFilter");
      assertThat(name(arguments.get("text"))).isEqualTo("StringPropertyFilter");
      assertThat(name(arguments.get("number"))).isEqualTo("NumberPropertyFilter");
      assertThat(name(arguments.get("flag"))).isEqualTo("BooleanPropertyFilter");
      assertThat(name(arguments.get("date"))).isEqualTo("DatePropertyFilter");
      assertThat(name(arguments.get("strings"))).isEqualTo("StringArrayPropertyFilter");
      assertThat(name(arguments.get("ids"))).isEqualTo("UUIDArrayPropertyFilter");
      assertThat(name(arguments.get("children"))).isEqualTo("BaseClassArrayPropertyFilter");
      assertThat(name(arguments.get("child"))).isEqualTo("BaseClassPropertyFilter");
      assertThat(name(arguments.get("generic"))).isEqualTo("GenericClass_string_PropertyFilter");
      assertThat(name(arguments.get("size"))).isEqualTo("SizePropertyFilter");
    }

    @Test
    void usesJsonForMapsExternalTypesAndUnknownValues() {
      Map<String, GraphQLInputType> arguments = arguments();

      assertThat(arguments.get("record")).isSameAs(Common.jsonScalar());
      assertThat(arguments.get("timeKey")).isSameAs(Common.jsonScalar());
      assertThat(arguments.get("anything")).isSameAs(Common.jsonScalar());
      assertThat(arguments.get("external")).isSameAs(Common.jsonScalar());
    }

    @Test
    void filtersArraysWithIncludes() {
      Map<String, GraphQLInputType> arguments = arguments();

      assertThat(fields(arguments.get("strings"))).isEqualTo(Map.of("includes", "String", "isDefined", "Boolean"));
      assertThat(fields(arguments.get("ids"))).isEqualTo(Map.of("includes", "ID", "isDefined", "Boolean"));
      assertThat(fields(arguments.get("children"))).isEqualTo(Map.of("includes", "JSON", "isDefined", "Boolean"));
    }

    @Test
    void filtersUuidsDatesAndEnums() {
      Map<String, GraphQLInputType> arguments = arguments();

      assertThat(fieldNames(arguments.get("id")))
          .containsExactly("eq", "ne", "in", "beginsWith", "contains", "isDefined");
      assertThat(fields(arguments.get("id")).get("in")).isEqualTo("[ID]");
      assertThat(fieldNames(arguments.get("date")))
          .containsExactly("eq", "ne", "lte", "lt", "gte", "gt", "in", "isDefined");
      assertThat(fields(arguments.get("date")).get("gt")).isEqualTo("Date");
      assertThat(fields(arguments.get("size"))).isEqualTo(Map.of("eq", "Size", "ne", "Size", "isDefined", "Boolean"));
    }

    @Test
    void nestsTheFiltersOfApplicationClasses() {
      Map<String, GraphQLInputType> arguments = arguments();

      assertThat(fields(arguments.get("child"))).containsExactly(
          Map.entry("base", "StringPropertyFilter"),
          Map.entry("and", "[BaseClassPropertyFilter]"),
          Map.entry("or", "[BaseClassPropertyFilter]"),
          Map.entry("not", "BaseClassPropertyFilter"),
          Map.entry("isDefined", "Boolean"));
      assertThat(fields(arguments.get("generic"))).containsEntry("genericValue", "JSON");
    }

    @Test
    void createsTheInputTypeOfNestedClassesAsASideEffect() {
      arguments();

      assertThat(Common.printType(typeInformer.generateGraphQLTypeForClass(BaseClass.class, null, true)))
          .isEqualTo("BaseClassInput");
    }

    @Test
    void sharesTheFiltersThroughTheCache() {
      Map<String, GraphQLInputType> first = arguments();
      Map<String, GraphQLInputType> second = new GraphqlQueryFilterArgumentsBuilder(typeInformer, filters, config)
          .generateFilterArguments(SchemaReadModel.class, List.of("text"));

      assertThat(second.get("child")).isSameAs(first.get("child"));
      assertThat(second).doesNotContainKey("text");
      assertThat(filters).containsKeys("StringPropertyFilter", "BaseClassPropertyFilter", "StringArrayPropertyFilter");
    }

    @Test
    void replacesEveryCharacterThatIsNotALetterOrUnderscore() {
      assertThat(GraphqlQueryFilterArgumentsBuilder.filterName("Page<Cart>", "PropertyFilter"))
          .isEqualTo("Page_Cart_PropertyFilter");
      assertThat(GraphqlQueryFilterArgumentsBuilder.filterName("Address2", "PropertyFilter"))
          .isEqualTo("Address_PropertyFilter");
      assertThat(GraphqlQueryFilterArgumentsBuilder.filterName("string[]", "PropertyFilter"))
          .isEqualTo("String__PropertyFilter");
    }

    @Test
    void wrapsTheArgumentsInTheFilterInputOfAQuery() {
      Map<String, GraphQLInputType> args = new GraphqlQueryFilterFieldsBuilder(typeInformer, filters, config)
          .generateFilterQueriesFields("SchemaReadModel", SchemaReadModel.class, null);

      GraphQLInputObjectType filter = (GraphQLInputObjectType) args.get("filter");
      assertThat(args.keySet()).containsExactly("filter");
      assertThat(filter.getName()).isEqualTo("SchemaReadModelFilter");
      assertThat(fieldNames(filter).subList(fieldNames(filter).size() - 4, fieldNames(filter).size()))
          .containsExactly("and", "or", "not", "isDefined");
      assertThat(fields(filter).get("and")).isEqualTo("[SchemaReadModelFilter]");
    }
  }

  @Nested
  class SortArguments {

    private Map<String, GraphQLInputType> arguments() {
      return new GraphqlQuerySortBuilder(typeInformer, config).generateSortArguments(SchemaReadModel.class, null);
    }

    @Test
    void sortsEveryStoredPropertyWithOrderPropertyExceptApplicationClasses() {
      Map<String, GraphQLInputType> arguments = arguments();

      assertThat(arguments.keySet()).doesNotContain("calculated");
      arguments.forEach((property, type) -> {
        if (property.equals("child")) {
          assertThat(name(type)).isEqualTo("BaseClassPropertySortBy");
        } else {
          assertThat(name(type)).as(property).isEqualTo("orderProperty");
        }
      });
      assertThat(((GraphQLEnumType) arguments.get("id")).getValues()).extracting(value -> value.getName())
          .containsExactly("ASC", "DESC");
      assertThat(fields(arguments.get("child"))).isEqualTo(Map.of("base", "orderProperty"));
    }

    @Test
    void usesOneOrderPropertyEnum() {
      Map<String, GraphQLInputType> arguments = arguments();

      assertThat(arguments.get("text")).isSameAs(arguments.get("number"));
    }

    @Test
    void reusesANestedSortByNameEvenForArrays() {
      Map<String, GraphQLInputType> arguments = new GraphqlQuerySortBuilder(typeInformer, config)
          .generateSortArguments(WithChildren.class, null);

      assertThat(name(arguments.get("child"))).isEqualTo("BaseClassPropertySortBy");
      assertThat(arguments.get("children")).isSameAs(arguments.get("child"));
    }

    public record WithChildren(BaseClass child, List<BaseClass> children) {}
  }

  @Nested
  class HandledFields {

    public record WithoutHandle(String value) {}

    public record AsyncHandle(String value) {
      public static CompletableFuture<Optional<Double>> handle(AsyncHandle query) {
        return CompletableFuture.completedFuture(Optional.empty());
      }
    }

    public record NoProperties() {
      public static boolean handle(NoProperties command) {
        return true;
      }
    }

    public record Secret(@com.boostercloud.framework.core.decorators.NonExposed String hidden, String shown) {
      public static String handle(Secret command) {
        return command.shown();
      }
    }

    private Map<String, TargetTypeMetadata> targets(Class<?>... types) {
      Map<String, TargetTypeMetadata> targets = new LinkedHashMap<>();
      for (Class<?> type : types) {
        ClassMetadata metadata = ClassMetadata.of(type);
        targets.put(type.getSimpleName(), new TargetTypeMetadata(type, metadata.fields(), metadata.methods()));
      }
      return targets;
    }

    private Map<String, GraphQLFieldConfig> fields(boolean allowVoidReturn, Class<?>... types) {
      GraphQLFieldResolver resolver = (parent, args, context, info) -> null;
      return new GraphQLHandledFieldsGenerator(targets(types), typeInformer, type -> resolver, config)
          .generateFields(allowVoidReturn);
    }

    @Test
    void returnsBooleanForCommandsWithoutHandle() {
      assertThat(Common.printType(fields(true, WithoutHandle.class).get("WithoutHandle").type()))
          .isEqualTo("Boolean!");
    }

    @Test
    void rejectsQueriesWithoutHandle() {
      assertThatThrownBy(() -> fields(false, WithoutHandle.class))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Unexpected void return type for Query");
    }

    @Test
    void unwrapsTheFutureOfAsyncHandlers() {
      assertThat(Common.printType(fields(false, AsyncHandle.class).get("AsyncHandle").type())).isEqualTo("Float");
    }

    @Test
    void addsTheInputArgumentOnlyWhenTheClassHasProperties() {
      Map<String, GraphQLFieldConfig> fields = fields(true, AsyncHandle.class, NoProperties.class);

      assertThat(Common.printType(fields.get("AsyncHandle").args().get("input"))).isEqualTo("AsyncHandleInput!");
      assertThat(fields.get("NoProperties").args()).isEmpty();
    }

    @Test
    void leavesTheNonExposedPropertiesOutOfTheInput() {
      config.nonExposedGraphQLMetadataKey.put("Secret", List.of("hidden"));

      GraphQLInputObjectType input = (GraphQLInputObjectType) ((graphql.schema.GraphQLNonNull) fields(true,
          Secret.class).get("Secret").args().get("input")).getWrappedType();

      assertThat(input.getFields()).extracting(GraphQLInputObjectField::getName).containsExactly("shown");
    }

    @Test
    void usesTheResolverOfTheClass() {
      GraphQLFieldResolver resolver = (parent, args, context, info) -> "resolved";

      Map<String, GraphQLFieldConfig> fields = new GraphQLHandledFieldsGenerator(targets(NoProperties.class),
          typeInformer, type -> resolver, config).generateFields();

      assertThat(fields.get("NoProperties").resolve()).isSameAs(resolver);
      assertThat(Common.printType(fields.get("NoProperties").type())).isEqualTo("Boolean!");
      assertThat(graphql.schema.GraphQLTypeUtil.unwrapNonNull(fields.get("NoProperties").type()))
          .isSameAs(Scalars.GraphQLBoolean);
    }
  }

  private static List<String> fieldNames(GraphQLInputType type) {
    return ((GraphQLInputObjectType) type).getFields().stream().map(GraphQLInputObjectField::getName).toList();
  }

  private static Map<String, String> fields(GraphQLInputType type) {
    Map<String, String> fields = new LinkedHashMap<>();
    for (GraphQLInputObjectField field : ((GraphQLInputObjectType) type).getFields()) {
      fields.put(field.getName(), Common.printType(field.getType()));
    }
    return fields;
  }
}
