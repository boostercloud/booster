package com.boostercloud.framework.core.services.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.types.Logger;
import com.boostercloud.metadata.TypeGroup;
import com.boostercloud.metadata.TypeMetadata;
import graphql.Scalars;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnionType;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Port of {@code test/services/graphql/graphql-type-informer.test.ts}, plus the rules the TypeScript tests leave out. */
class GraphQLTypeInformerTest {

  private static final List<String> NON_EXPOSED = List.of("ignoredParameter");

  private GraphQLTypeInformer sut;

  @BeforeEach
  void setUp() {
    sut = new GraphQLTypeInformer(Logger.NOOP);
  }

  interface Parameters {
    String value();
  }

  public record TestClass(
      String someProperty,
      List<List<Parameters>> someParameters,
      List<Parameters> otherParameter,
      CompletableFuture<String> somePromiseParameter,
      CompletableFuture<Double> somePromiseParameter2,
      Optional<CompletableFuture<Optional<Double>>> somePromiseParameter3,
      int ignoredParameter) {}

  private GraphQLObjectType testClassType() {
    return (GraphQLObjectType) sut.generateGraphQLTypeForClass(TestClass.class, NON_EXPOSED);
  }

  private static String fieldType(GraphQLObjectType type, String field) {
    return Common.printType(type.getFieldDefinition(field).getType());
  }

  private static TypeMetadata metadata(String name, TypeGroup typeGroup, TypeMetadata... parameters) {
    return new TypeMetadata(name, typeGroup, null, List.of(parameters), false, false, null, null, null);
  }

  @Nested
  class GenerateGraphQLTypeForClass {

    @Test
    void returnsTheExpectedGraphQLType() {
      assertThat(Common.printType(sut.generateGraphQLTypeForClass(TestClass.class, NON_EXPOSED))).isEqualTo("TestClass");
    }

    @Test
    void processesNestedLists() {
      GraphQLObjectType result = testClassType();

      assertThat(fieldType(result, "someParameters")).isEqualTo("[[JSON!]!]!");
      assertThat(GraphQLTypeUtil.unwrapAll(result.getFieldDefinition("someParameters").getType()))
          .isSameAs(Common.jsonScalar());
      assertThat(fieldType(result, "otherParameter")).isEqualTo("[JSON!]!");
    }

    @Test
    void processesPromises() {
      GraphQLObjectType result = testClassType();

      assertThat(fieldType(result, "somePromiseParameter")).isEqualTo("String!");
      assertThat(fieldType(result, "somePromiseParameter2")).isEqualTo("Float!");
      assertThat(fieldType(result, "somePromiseParameter3")).isEqualTo("Float");
    }

    @Test
    void ignoresNonExposedProperties() {
      assertThat(testClassType().getFieldDefinition("ignoredParameter")).isNull();
    }

    @Test
    void neverWrapsTheTypeInNonNull() {
      assertThat(sut.generateGraphQLTypeForClass(TestClass.class, null, true)).isInstanceOf(GraphQLInputObjectType.class);
      assertThat(sut.generateGraphQLTypeForClass(TestClass.class, null, false)).isInstanceOf(GraphQLObjectType.class);
    }

    @Test
    void namesInputTypesWithTheInputSuffix() {
      GraphQLInputObjectType input = (GraphQLInputObjectType) sut.generateGraphQLTypeForClass(Order.class, null, true);

      assertThat(input.getName()).isEqualTo("OrderInput");
      assertThat(Common.printType(input.getField("address").getType())).isEqualTo("AddressInput");
      assertThat(Common.printType(input.getField("lines").getType())).isEqualTo("[AddressInput!]!");
    }

    @Test
    void ignoresTheExcludedPropertiesOnACacheHit() {
      GraphQLType first = sut.generateGraphQLTypeForClass(TestClass.class, List.of());

      assertThat(sut.generateGraphQLTypeForClass(TestClass.class, NON_EXPOSED)).isSameAs(first);
      assertThat(((GraphQLObjectType) first).getFieldDefinition("ignoredParameter")).isNotNull();
    }

    @Test
    void honoursTheExcludedPropertiesOnlyForTheGivenClass() {
      GraphQLObjectType order = (GraphQLObjectType) sut.generateGraphQLTypeForClass(Order.class, List.of("street"));

      assertThat(order.getFieldDefinitions()).extracting(field -> field.getName())
          .containsExactly("address", "lines", "vehicle", "duration");
      assertThat(((GraphQLObjectType) GraphQLTypeUtil.unwrapAll(order.getFieldDefinition("address").getType()))
          .getFieldDefinition("street")).isNotNull();
    }

    @Test
    void exposesExternalClassesAsJson() {
      GraphQLObjectType order = (GraphQLObjectType) sut.generateGraphQLTypeForClass(Order.class, null);

      assertThat(fieldType(order, "duration")).isEqualTo("JSON");
    }

    @Test
    void createsUnionsOfApplicationClassesForOutputs() {
      GraphQLObjectType order = (GraphQLObjectType) sut.generateGraphQLTypeForClass(Order.class, null);

      GraphQLUnionType vehicle = (GraphQLUnionType) GraphQLTypeUtil.unwrapAll(
          order.getFieldDefinition("vehicle").getType());
      assertThat(vehicle.getName()).isEqualTo("Vehicle");
      assertThat(vehicle.getTypes()).extracting(type -> type.getName()).containsExactly("Car", "Bike");
      assertThat(sut.typeResolvers()).containsOnlyKeys("Vehicle");
    }

    @Test
    void usesJsonForUnionsInInputs() {
      GraphQLInputObjectType order = (GraphQLInputObjectType) sut.generateGraphQLTypeForClass(Order.class, null, true);

      assertThat(Common.printType(order.getField("vehicle").getType())).isEqualTo("JSON!");
    }

    @Test
    void refersToClassesThatContainThemselves() {
      GraphQLObjectType category = (GraphQLObjectType) sut.generateGraphQLTypeForClass(Category.class, null);

      assertThat(category.getFieldDefinition("parent").getType()).isInstanceOf(GraphQLTypeReference.class);
      assertThat(Common.printType(category.getFieldDefinition("children").getType())).isEqualTo("[Category!]!");
    }

    @Test
    void fixesTheElementNullabilityOfAListByItsFirstCreation() {
      GraphQLObjectType first = (GraphQLObjectType) sut.generateGraphQLTypeForClass(NullableTags.class, null);
      GraphQLObjectType second = (GraphQLObjectType) sut.generateGraphQLTypeForClass(Tags.class, null);

      assertThat(fieldType(first, "tags")).isEqualTo("[String]");
      assertThat(fieldType(second, "tags")).isEqualTo("[String]!");
    }
  }

  @Nested
  class GetOrCreateGraphQLType {

    @Test
    void mapsUUIDToANonNullID() {
      GraphQLType result = sut.getOrCreateGraphQLType(metadata("UUID", TypeGroup.OTHER));

      assertThat(result).isInstanceOf(GraphQLNonNull.class);
      assertThat(GraphQLTypeUtil.unwrapNonNull(result)).isSameAs(Scalars.GraphQLID);
    }

    @Test
    void mapsDateToANonNullDateScalar() {
      GraphQLType result = sut.getOrCreateGraphQLType(metadata("Date", TypeGroup.OTHER));

      assertThat(GraphQLTypeUtil.unwrapNonNull(result)).isSameAs(Common.dateScalar());
      assertThat(Common.printType(result)).isEqualTo("Date!");
    }

    @Test
    void mapsStringsNumbersAndBooleans() {
      assertThat(GraphQLTypeUtil.unwrapNonNull(sut.getOrCreateGraphQLType(metadata("string", TypeGroup.STRING))))
          .isSameAs(Scalars.GraphQLString);
      assertThat(GraphQLTypeUtil.unwrapNonNull(sut.getOrCreateGraphQLType(metadata("number", TypeGroup.NUMBER))))
          .isSameAs(Scalars.GraphQLFloat);
      assertThat(GraphQLTypeUtil.unwrapNonNull(sut.getOrCreateGraphQLType(metadata("boolean", TypeGroup.BOOLEAN))))
          .isSameAs(Scalars.GraphQLBoolean);
    }

    @Test
    void mapsEnumsWithTheirNamesAsValues() {
      GraphQLType result = sut.getOrCreateGraphQLType(metadata("enum", TypeGroup.ENUM,
          metadata("key", TypeGroup.BOOLEAN)));

      assertThat(result).isInstanceOf(GraphQLNonNull.class);
      GraphQLEnumType enumType = (GraphQLEnumType) GraphQLTypeUtil.unwrapNonNull(result);
      assertThat(enumType.getName()).isEqualTo("enum");
      assertThat(enumType.getValues()).extracting(GraphQLEnumValueDefinition::getName).containsExactly("key");
      assertThat(enumType.getValues()).extracting(GraphQLEnumValueDefinition::getValue).containsExactly("key");
    }

    @Test
    void mapsArrays() {
      GraphQLType result = sut.getOrCreateGraphQLType(metadata("MyArray[]", TypeGroup.ARRAY,
          metadata("boolean", TypeGroup.BOOLEAN)));

      assertThat(Common.printType(result)).isEqualTo("[Boolean!]!");
    }

    @Test
    void mapsObjectsToJson() {
      GraphQLType result = sut.getOrCreateGraphQLType(metadata("MyObject", TypeGroup.OBJECT));

      assertThat(GraphQLTypeUtil.unwrapNonNull(result)).isSameAs(Common.jsonScalar());
    }

    @ParameterizedTest
    @EnumSource(value = TypeGroup.class, names = {"FUNCTION", "INTERFACE", "INTERSECTION", "TYPE", "OTHER"})
    void mapsEveryOtherGroupToJson(TypeGroup typeGroup) {
      GraphQLType result = sut.getOrCreateGraphQLType(metadata("MyObject" + typeGroup, typeGroup));

      assertThat(Common.printType(result)).isEqualTo("JSON!");
    }

    @Test
    void keepsNullableTypesNullable() {
      TypeMetadata nullable = metadata("string", TypeGroup.STRING).withNullable(true);

      assertThat(sut.getOrCreateGraphQLType(nullable)).isSameAs(Scalars.GraphQLString);
    }

    @Test
    void reusesTypesByName() {
      TypeMetadata order = TypeMetadata.of(Order.class);

      GraphQLType first = GraphQLTypeUtil.unwrapNonNull(sut.getOrCreateGraphQLType(order, false));
      GraphQLType second = GraphQLTypeUtil.unwrapNonNull(sut.getOrCreateGraphQLType(order, false));

      assertThat(second).isSameAs(first);
      assertThat(sut.generateGraphQLTypeForClass(Order.class, null)).isSameAs(first);
    }
  }

  public record Address(String street) {}

  public sealed interface Vehicle permits Car, Bike {}

  public record Car(int wheels) implements Vehicle {}

  public record Bike(String brand) implements Vehicle {}

  public record Order(Optional<Address> address, List<Address> lines, Vehicle vehicle, Optional<Duration> duration) {}

  public record Category(Optional<Category> parent, List<Category> children) {}

  public record NullableTags(@Nullable List<String> tags) {}

  public record Tags(List<String> tags) {}

  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
  @interface Nullable {}
}
