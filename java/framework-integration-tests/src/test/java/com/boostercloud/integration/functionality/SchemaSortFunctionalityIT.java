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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-unaware/functionality/schema.sort.integration.ts}: the sort input type
 * {@code SchemaReadModelSortBy} generated for {@code SchemaReadModel}, the application's fixture of the GraphQL type
 * mapping (the enum {@code orderProperty} for every exposed property, a nested sort type for class properties).
 * TypeScript's {@code For optional base class} queries the filter type instead of the sort type (a copy of the filter
 * suite's test) and its two generic tests check {@code type.inputFileds} (sic), which is always undefined; the port
 * keeps both as they are.
 */
@EndToEnd
@DisplayName("schema sort")
class SchemaSortFunctionalityIT {

  private static final String __TYPE = "__Type";
  private static final String INPUT_OBJECT = "INPUT_OBJECT";
  private static final String __INPUT_VALUE = "__InputValue";
  private static final String STRING_PROPERTY_FILTER = "StringPropertyFilter";
  private static final String ENUM = "ENUM";
  private static final String ORDER_PROPERTY = "orderProperty";

  private static final String SORT_QUERY = """
      query UniversalQuery {
        __type(name: "SchemaReadModelSortBy") {
          __typename
          name
          kind
          inputFields {
            __typename
            name
            type {
              __typename
              name
              kind
            }
          }
        }
      }
      """;

  /** The filter query TypeScript's {@code For optional base class} sends (copied from the filter suite). */
  private static final String NESTED_FILTER_QUERY = """
      query UniversalQuery {
        __type(name: "ListSchemaReadModelFilter") {
          kind
          name
          inputFields(includeDeprecated: false) {
            name
            type {
              kind
              name
              inputFields {
                name
                type {
                  kind
                  name
                }
              }
            }
          }
        }
      }
      """;

  private final ApplicationUnderTest applicationUnderTest;
  private GraphQLClient client;

  SchemaSortFunctionalityIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    client = applicationUnderTest.graphql().client();
  }

  @Nested
  @DisplayName("should return the expected schema for each sort fields")
  class ShouldReturnTheExpectedSchemaForEachSortFields {
    private List<Map<String, Object>> inputFields;

    @BeforeEach
    void beforeEach() {
      GraphQLResponse queryResult = client.query(SORT_QUERY);

      assertThat(queryResult.get("__type.__typename")).isEqualTo(__TYPE);
      assertThat(queryResult.get("__type.name")).isEqualTo("SchemaReadModelSortBy");
      assertThat(queryResult.get("__type.kind")).isEqualTo(INPUT_OBJECT);
      inputFields = inputFieldsOf(queryResult);
    }

    @Test
    @Order(1)
    @DisplayName("NonExposed properties are not included")
    void nonExposedPropertiesAreNotIncluded() {
      List<Object> fieldsNames = inputFields.stream().map(field -> field.get("name")).toList();
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
          "__typename", __INPUT_VALUE,
          "name", "readOnlyProperty",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(0)).isEqualTo(expectedResult);
    }

    @Test
    @Order(3)
    @DisplayName("For private property")
    void forPrivateProperty() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "privateProperty",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(1)).isEqualTo(expectedResult);
    }

    @Test
    @Order(4)
    @DisplayName("For public property")
    void forPublicProperty() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "publicProperty",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(2)).isEqualTo(expectedResult);
    }

    @Test
    @Order(5)
    @DisplayName("For id (UUID)")
    void forIdUuid() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "id",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(3)).isEqualTo(expectedResult);
    }

    @Test
    @Order(6)
    @DisplayName("For dates")
    void forDates() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "date",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(4)).isEqualTo(expectedResult);
    }

    @Test
    @Order(7)
    @DisplayName("For string arrays defined with brackets")
    void forStringArraysDefinedWithBrackets() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "array0",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(5)).isEqualTo(expectedResult);
    }

    @Test
    @Order(8)
    @DisplayName("For string arrays defined with Array")
    void forStringArraysDefinedWithArray() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "array1",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(6)).isEqualTo(expectedResult);
    }

    @Test
    @Order(9)
    @DisplayName("For union arrays")
    void forUnionArrays() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "unionArrays",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(7)).isEqualTo(expectedResult);
    }

    @Test
    @Order(10)
    @DisplayName("For union with null")
    void forUnionWithNull() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "unionWithNull",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(8)).isEqualTo(expectedResult);
    }

    @Test
    @Order(11)
    @DisplayName("For union with undefined")
    void forUnionWithUndefined() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "unionWithUndefined",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(9)).isEqualTo(expectedResult);
    }

    @Test
    @Order(12)
    @DisplayName("For union with any")
    void forUnionWithAny() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "unionWithAny",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(10)).isEqualTo(expectedResult);
    }

    @Test
    @Order(13)
    @DisplayName("For union with object")
    void forUnionWithObject() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "unionWithObject",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(11)).isEqualTo(expectedResult);
    }

    @Test
    @Order(14)
    @DisplayName("For union with unknown")
    void forUnionWithUnknown() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "unionWithUnknown",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(12)).isEqualTo(expectedResult);
    }

    @Test
    @Order(15)
    @DisplayName("For functions")
    void forFunctions() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "func0",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(13)).isEqualTo(expectedResult);
    }

    @Test
    @Order(16)
    @DisplayName("For any")
    void forAny() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "any0",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(14)).isEqualTo(expectedResult);
    }

    @Test
    @Order(17)
    @DisplayName("For unknown")
    void forUnknown() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "unknown0",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(15)).isEqualTo(expectedResult);
    }

    @Test
    @Order(18)
    @DisplayName("For record")
    void forRecord() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "record",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(16)).isEqualTo(expectedResult);
    }

    @Test
    @Order(19)
    @DisplayName("For generic")
    void forGeneric() {
      assertThat(at(inputFields, "[17].name")).isEqualTo("generic");
      assertThat(at(inputFields, "[17].__typename")).isEqualTo(__INPUT_VALUE);
      assertThat(at(inputFields, "[17].type.name")).isEqualTo(ORDER_PROPERTY);
      assertThat(at(inputFields, "[17].type.kind")).isEqualTo(ENUM);
      assertThat(at(inputFields, "[17].type.__typename")).isEqualTo(__TYPE);
      // (sic) as in TypeScript: no type has a property named inputFileds
      assertThat(at(inputFields, "[17].type.inputFileds")).isNull();
    }

    @Test
    @Order(20)
    @DisplayName("For base class")
    void forBaseClass() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "child",
          "type", object("__typename", __TYPE, "name", "BaseClassPropertySortBy", "kind", INPUT_OBJECT));
      assertThat(inputFields.get(18)).isEqualTo(expectedResult);
    }

    @Test
    @Order(21)
    @DisplayName("For optional string")
    void forOptionalString() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "optionalString",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(19)).isEqualTo(expectedResult);
    }

    @Test
    @Order(22)
    @DisplayName("For optional null")
    void forOptionalNull() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "optionalNull",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(20)).isEqualTo(expectedResult);
    }

    @Test
    @Order(23)
    @DisplayName("For optional undefined")
    void forOptionalUndefined() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "optionalUndefined",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(21)).isEqualTo(expectedResult);
    }

    @Test
    @Order(24)
    @DisplayName("For optional unknown")
    void forOptionalUnknown() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "optionalUnknown",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(22)).isEqualTo(expectedResult);
    }

    @Test
    @Order(25)
    @DisplayName("For optional any")
    void forOptionalAny() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "optionalAny",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(23)).isEqualTo(expectedResult);
    }

    @Test
    @Order(26)
    @DisplayName("For optional record")
    void forOptionalRecord() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "optionalRecord",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(24)).isEqualTo(expectedResult);
    }

    @Test
    @Order(27)
    @DisplayName("For optional generic")
    void forOptionalGeneric() {
      assertThat(at(inputFields, "[25].name")).isEqualTo("optionalGeneric");
      assertThat(at(inputFields, "[25].__typename")).isEqualTo(__INPUT_VALUE);
      assertThat(at(inputFields, "[25].type.name")).isEqualTo(ORDER_PROPERTY);
      assertThat(at(inputFields, "[25].type.kind")).isEqualTo(ENUM);
      assertThat(at(inputFields, "[25].type.__typename")).isEqualTo(__TYPE);
      // (sic) as in TypeScript: no type has a property named inputFileds
      assertThat(at(inputFields, "[25].type.inputFileds")).isNull();
    }

    @Test
    @Order(28)
    @DisplayName("For optional base class")
    void forOptionalBaseClass() {
      // As in TypeScript, this test looks at the filter type, not at the sort type
      GraphQLResponse queryResult = client.query(NESTED_FILTER_QUERY);
      inputFields = inputFieldsOf(queryResult);

      assertThat(at(inputFields, "[26].name")).isEqualTo("optionalChild");
      assertThat(at(inputFields, "[26].__typename")).isEqualTo(__INPUT_VALUE);
      assertThat(at(inputFields, "[26].type.kind")).isEqualTo(INPUT_OBJECT);
      assertThat(at(inputFields, "[26].type.name")).isEqualTo("BaseClassPropertyFilter");
      assertThat(at(inputFields, "[26].type.inputFields[0].name")).isEqualTo("base");
      assertThat(at(inputFields, "[26].type.inputFields[0].__typename")).isEqualTo(__INPUT_VALUE);
      assertThat(at(inputFields, "[26].type.inputFields[0].type.kind")).isEqualTo(INPUT_OBJECT);
      assertThat(at(inputFields, "[26].type.inputFields[0].type.name")).isEqualTo(STRING_PROPERTY_FILTER);
      assertThat(at(inputFields, "[26].type.inputFields[0].type.__typename")).isEqualTo(__TYPE);
      assertThat(at(inputFields, "[26].type.__typename")).isEqualTo(__TYPE);
    }

    @Test
    @Order(29)
    @DisplayName("For readonly array")
    void forReadonlyArray() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "readonlyArray",
          "type", object("__typename", __TYPE, "name", ORDER_PROPERTY, "kind", ENUM));
      assertThat(inputFields.get(27)).isEqualTo(expectedResult);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> inputFieldsOf(GraphQLResponse queryResult) {
    return (List<Map<String, Object>>) queryResult.get("__type.inputFields");
  }
}
