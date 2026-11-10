package com.boostercloud.integration.functionality;

import static com.boostercloud.integration.functionality.StoredDocuments.at;
import static com.boostercloud.integration.functionality.StoredDocuments.object;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-unaware/functionality/schema.search.integration.ts}: the object type
 * {@code SchemaReadModel}, the application's fixture of the GraphQL type mapping (one field per exposed property, in
 * declaration order).
 *
 * <p>{@code For readonly array} expects TypeScript's {@code [String]}. Booster caches the list types by name and the
 * first property that creates {@code StringList} decides the nullability of its elements for every string list
 * (faithful to TypeScript); TypeScript registers the read models in import order ({@code CartReadModel.cartItemsIds?},
 * {@code [String]}, comes first), Java in class-path order ({@code CarOfferReadModel.purchasesIds}, {@code [String!]}).
 * That test is disabled with this reason and {@code For readonly array as a Java list} pins the Java type (see the
 * README, "Differences from TypeScript", and {@code ApplicationRegistrationTest}).
 */
@EndToEnd
@DisplayName("schema search")
class SchemaSearchFunctionalityIT {

  private static final String __TYPE = "__Type";
  private static final String SCALAR = "SCALAR";
  private static final String JSON_OBJECT = "JSON";
  private static final String NON_NULL = "NON_NULL";
  private static final String __FIELD = "__Field";
  private static final String ID = "ID";
  private static final String DATE = "Date";
  private static final String LIST = "LIST";
  private static final String STRING = "String";
  private static final String OBJECT_KIND = "OBJECT";

  private static final String READONLY_ARRAY_DIFFERENCE = "TypeScript's first StringList is [String] "
      + "(CartReadModel.cartItemsIds?, registered first in import order); Java registers "
      + "CarOfferReadModel.purchasesIds ([String!]) first, so every string list is [String!] "
      + "(pinned by 'For readonly array as a Java list')";

  private static final String SEARCH_QUERY = """
      query UniversalQuery {
        __type(name: "SchemaReadModel") {
          name
          __typename
          fields {
            __typename
            name
            type {
              __typename
              name
              kind
              ofType {
                __typename
                name
                kind
              }
            }
          }
        }
      }
      """;

  private final ApplicationUnderTest applicationUnderTest;
  private GraphQLClient client;

  SchemaSearchFunctionalityIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    client = applicationUnderTest.graphql().client();
  }

  @Nested
  @DisplayName("should return the expected schema for each search items")
  class ShouldReturnTheExpectedSchemaForEachSearchItems {
    private List<Map<String, Object>> fields;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void beforeEach() {
      GraphQLResponse queryResult = client.query(SEARCH_QUERY);

      assertThat(queryResult.get("__type.__typename")).isEqualTo(__TYPE);
      assertThat(queryResult.get("__type.name")).isEqualTo("SchemaReadModel");
      fields = (List<Map<String, Object>>) queryResult.get("__type.fields");
    }

    @Test
    @Order(1)
    @DisplayName("NonExposed properties are not included")
    void nonExposedPropertiesAreNotIncluded() {
      List<Object> fieldsNames = fields.stream().map(field -> field.get("name")).toList();
      // chai's `have.members`: the same elements in any order
      assertThat(fieldsNames).containsExactlyInAnyOrder(
          "readOnlyProperty",
          "privateProperty",
          "publicProperty",
          "id",
          "date",
          "array0",
          "array1",
          "unionArrays",
          "unionWithNull",
          "unionWithUndefined",
          "unionWithAny",
          "unionWithObject",
          "unionWithUnknown",
          "func0",
          "any0",
          "unknown0",
          "record",
          "generic",
          "child",
          "optionalString",
          "optionalNull",
          "optionalUndefined",
          "optionalUnknown",
          "optionalAny",
          "optionalRecord",
          "optionalGeneric",
          "optionalChild",
          "readonlyArray");
    }

    @Test
    @Order(2)
    @DisplayName("For read only property")
    void forReadOnlyProperty() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "readOnlyProperty",
          "type", object(
              "__typename", __TYPE,
              "name", STRING,
              "kind", SCALAR,
              "ofType", null));
      assertThat(fields.get(0)).isEqualTo(expectedResult);
    }

    @Test
    @Order(3)
    @DisplayName("For private property")
    void forPrivateProperty() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "privateProperty",
          "type", object(
              "__typename", __TYPE,
              "name", STRING,
              "kind", SCALAR,
              "ofType", null));
      assertThat(fields.get(1)).isEqualTo(expectedResult);
    }

    @Test
    @Order(4)
    @DisplayName("For public property")
    void forPublicProperty() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "publicProperty",
          "type", object(
              "__typename", __TYPE,
              "name", STRING,
              "kind", SCALAR,
              "ofType", null));
      assertThat(fields.get(2)).isEqualTo(expectedResult);
    }

    @Test
    @Order(5)
    @DisplayName("For id (UUID)")
    void forIdUuid() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "id",
          "type", object(
              "__typename", __TYPE,
              "name", null,
              "kind", NON_NULL,
              "ofType", object("__typename", __TYPE, "name", ID, "kind", SCALAR)));
      assertThat(fields.get(3)).isEqualTo(expectedResult);
    }

    @Test
    @Order(6)
    @DisplayName("For dates")
    void forDates() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "date",
          "type", object(
              "__typename", __TYPE,
              "name", null,
              "kind", NON_NULL,
              "ofType", object("__typename", __TYPE, "name", DATE, "kind", SCALAR)));
      assertThat(fields.get(4)).isEqualTo(expectedResult);
    }

    @Test
    @Order(7)
    @DisplayName("For string arrays defined with brackets")
    void forStringArraysDefinedWithBrackets() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "array0",
          "type", object(
              "__typename", __TYPE,
              "name", null,
              "kind", NON_NULL,
              "ofType", object("__typename", __TYPE, "name", null, "kind", LIST)));
      assertThat(fields.get(5)).isEqualTo(expectedResult);
    }

    @Test
    @Order(8)
    @DisplayName("For string arrays defined with Array")
    void forStringArraysDefinedWithArray() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "array1",
          "type", object(
              "__typename", __TYPE,
              "name", null,
              "kind", NON_NULL,
              "ofType", object("__typename", __TYPE, "name", null, "kind", LIST)));
      assertThat(fields.get(6)).isEqualTo(expectedResult);
    }

    @Test
    @Order(9)
    @DisplayName("For union arrays")
    void forUnionArrays() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "unionArrays",
          "type", object(
              "__typename", __TYPE,
              "name", null,
              "kind", NON_NULL,
              "ofType", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR)));
      assertThat(fields.get(7)).isEqualTo(expectedResult);
    }

    @Test
    @Order(10)
    @DisplayName("For union with null")
    void forUnionWithNull() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "unionWithNull",
          "type", object(
              "__typename", __TYPE,
              "name", STRING,
              "kind", SCALAR,
              "ofType", null));
      assertThat(fields.get(8)).isEqualTo(expectedResult);
    }

    @Test
    @Order(11)
    @DisplayName("For union with undefined")
    void forUnionWithUndefined() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "unionWithUndefined",
          "type", object(
              "__typename", __TYPE,
              "name", STRING,
              "kind", SCALAR,
              "ofType", null));
      assertThat(fields.get(9)).isEqualTo(expectedResult);
    }

    @Test
    @Order(12)
    @DisplayName("For union with any")
    void forUnionWithAny() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "unionWithAny",
          "type", object(
              "__typename", __TYPE,
              "name", null,
              "kind", NON_NULL,
              "ofType", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR)));
      assertThat(fields.get(10)).isEqualTo(expectedResult);
    }

    @Test
    @Order(13)
    @DisplayName("For union with object")
    void forUnionWithObject() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "unionWithObject",
          "type", object(
              "__typename", __TYPE,
              "name", null,
              "kind", NON_NULL,
              "ofType", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR)));
      assertThat(fields.get(11)).isEqualTo(expectedResult);
    }

    @Test
    @Order(14)
    @DisplayName("For union with unknown")
    void forUnionWithUnknown() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "unionWithUnknown",
          "type", object(
              "__typename", __TYPE,
              "name", null,
              "kind", NON_NULL,
              "ofType", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR)));
      assertThat(fields.get(12)).isEqualTo(expectedResult);
    }

    @Test
    @Order(15)
    @DisplayName("For functions")
    void forFunctions() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "func0",
          "type", object(
              "__typename", __TYPE,
              "name", null,
              "kind", NON_NULL,
              "ofType", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR)));
      assertThat(fields.get(13)).isEqualTo(expectedResult);
    }

    @Test
    @Order(16)
    @DisplayName("For any")
    void forAny() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "any0",
          "type", object(
              "__typename", __TYPE,
              "name", null,
              "kind", NON_NULL,
              "ofType", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR)));
      assertThat(fields.get(14)).isEqualTo(expectedResult);
    }

    @Test
    @Order(17)
    @DisplayName("For unknown")
    void forUnknown() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "unknown0",
          "type", object(
              "__typename", __TYPE,
              "name", null,
              "kind", NON_NULL,
              "ofType", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR)));
      assertThat(fields.get(15)).isEqualTo(expectedResult);
    }

    @Test
    @Order(18)
    @DisplayName("For record")
    void forRecord() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "record",
          "type", object(
              "__typename", __TYPE,
              "name", null,
              "kind", NON_NULL,
              "ofType", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR)));
      assertThat(fields.get(16)).isEqualTo(expectedResult);
    }

    @Test
    @Order(19)
    @DisplayName("For generic")
    void forGeneric() {
      assertThat(at(fields, "[17].name")).isEqualTo("generic");
      assertThat(at(fields, "[17].__typename")).isEqualTo(__FIELD);
      assertThat(at(fields, "[17].type.kind")).isEqualTo(NON_NULL);
      assertThat(at(fields, "[17].type.name")).isNull();
      assertThat(at(fields, "[17].type.ofType.__typename")).isEqualTo(__TYPE);
      assertThat(at(fields, "[17].type.ofType.name")).isEqualTo(JSON_OBJECT);
      assertThat(at(fields, "[17].type.ofType.kind")).isEqualTo(SCALAR);
    }

    @Test
    @Order(20)
    @DisplayName("For base class")
    void forBaseClass() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "child",
          "type", object(
              "__typename", __TYPE,
              "name", null,
              "kind", NON_NULL,
              "ofType", object("__typename", __TYPE, "name", "BaseClass", "kind", OBJECT_KIND)));
      assertThat(fields.get(18)).isEqualTo(expectedResult);
    }

    @Test
    @Order(21)
    @DisplayName("For optional string")
    void forOptionalString() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "optionalString",
          "type", object(
              "__typename", __TYPE,
              "name", STRING,
              "kind", SCALAR,
              "ofType", null));
      assertThat(fields.get(19)).isEqualTo(expectedResult);
    }

    @Test
    @Order(22)
    @DisplayName("For optional null")
    void forOptionalNull() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "optionalNull",
          "type", object(
              "__typename", __TYPE,
              "name", STRING,
              "kind", SCALAR,
              "ofType", null));
      assertThat(fields.get(20)).isEqualTo(expectedResult);
    }

    @Test
    @Order(23)
    @DisplayName("For optional undefined")
    void forOptionalUndefined() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "optionalUndefined",
          "type", object(
              "__typename", __TYPE,
              "name", JSON_OBJECT,
              "kind", SCALAR,
              "ofType", null));
      assertThat(fields.get(21)).isEqualTo(expectedResult);
    }

    @Test
    @Order(24)
    @DisplayName("For optional unknown")
    void forOptionalUnknown() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "optionalUnknown",
          "type", object(
              "__typename", __TYPE,
              "name", JSON_OBJECT,
              "kind", SCALAR,
              "ofType", null));
      assertThat(fields.get(22)).isEqualTo(expectedResult);
    }

    @Test
    @Order(25)
    @DisplayName("For optional any")
    void forOptionalAny() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "optionalAny",
          "type", object(
              "__typename", __TYPE,
              "name", JSON_OBJECT,
              "kind", SCALAR,
              "ofType", null));
      assertThat(fields.get(23)).isEqualTo(expectedResult);
    }

    @Test
    @Order(26)
    @DisplayName("For optional record")
    void forOptionalRecord() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "optionalRecord",
          "type", object(
              "__typename", __TYPE,
              "name", JSON_OBJECT,
              "kind", SCALAR,
              "ofType", null));
      assertThat(fields.get(24)).isEqualTo(expectedResult);
    }

    @Test
    @Order(27)
    @DisplayName("For optional generic")
    void forOptionalGeneric() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "optionalGeneric",
          "type", object(
              "__typename", __TYPE,
              "name", JSON_OBJECT,
              "kind", SCALAR,
              "ofType", null));
      assertThat(fields.get(25)).isEqualTo(expectedResult);
    }

    @Test
    @Order(28)
    @DisplayName("For optional base class")
    void forOptionalBaseClass() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "optionalChild",
          "type", object(
              "__typename", __TYPE,
              "name", "BaseClass",
              "kind", OBJECT_KIND,
              "ofType", null));
      assertThat(fields.get(26)).isEqualTo(expectedResult);
    }

    @Test
    @Order(29)
    @Disabled(READONLY_ARRAY_DIFFERENCE)
    @DisplayName("For readonly array")
    void forReadonlyArray() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "readonlyArray",
          "type", object(
              "__typename", __TYPE,
              "name", null,
              "kind", LIST,
              "ofType", object("__typename", __TYPE, "name", STRING, "kind", SCALAR)));
      assertThat(fields.get(27)).isEqualTo(expectedResult);
    }

    @Test
    @Order(30)
    @DisplayName("For readonly array as a Java list")
    void forReadonlyArrayAsAJavaList() {
      Map<String, Object> expectedResult = object(
          "__typename", __FIELD,
          "name", "readonlyArray",
          "type", object(
              "__typename", __TYPE,
              "name", null,
              "kind", LIST,
              "ofType", object("__typename", __TYPE, "name", null, "kind", NON_NULL)));
      // Java: [String!], the first StringList Java creates (TypeScript: [String])
      assertThat(fields.get(27)).isEqualTo(expectedResult);
    }
  }
}
