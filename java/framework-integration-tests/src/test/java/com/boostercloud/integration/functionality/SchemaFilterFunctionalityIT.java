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
 * Port of {@code integration/provider-unaware/functionality/schema.filter.integration.ts}: the filter input type
 * {@code ListSchemaReadModelFilter} generated for {@code SchemaReadModel}, the application's fixture of the GraphQL
 * type mapping (one filter per exposed property, in declaration order, then {@code and}, {@code or} and {@code not}).
 *
 * <p>{@code For readonly array} expects the TypeScript mapping of {@code ReadonlyArray<string>}, a {@code JSON}
 * filter: Java has no read-only list type, the property is a {@code List<String>} and gets the string array filter.
 * That test is disabled with this reason and {@code For readonly array as a Java list} pins the Java mapping (see the
 * README, "Differences from TypeScript").
 */
@EndToEnd
@DisplayName("schema filters")
class SchemaFilterFunctionalityIT {

  private static final String __TYPE = "__Type";
  private static final String INPUT_OBJECT = "INPUT_OBJECT";
  private static final String __INPUT_VALUE = "__InputValue";
  private static final String UUID_PROPERTY_FILTER = "UUIDPropertyFilter";
  private static final String DATE_PROPERTY_FILTER = "DatePropertyFilter";
  private static final String STRING_ARRAY_PROPERTY_FILTER = "StringArrayPropertyFilter";
  private static final String STRING_PROPERTY_FILTER = "StringPropertyFilter";
  private static final String SCALAR = "SCALAR";
  private static final String JSON_OBJECT = "JSON";
  private static final String BASE_CLASS_PROPERTY_FILTER = "BaseClassPropertyFilter";

  private static final String READONLY_ARRAY_DIFFERENCE = "TypeScript maps ReadonlyArray<string> to a JSON filter; "
      + "Java has no read-only list type and filters this List<String> with StringArrayPropertyFilter "
      + "(pinned by 'For readonly array as a Java list')";

  private static final String FILTER_QUERY = """
      query UniversalQuery {
        __type(name: "ListSchemaReadModelFilter") {
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

  /** The query of the tests that look into the nested filter types (Apollo adds {@code __typename}, so does Java). */
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

  SchemaFilterFunctionalityIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    client = applicationUnderTest.graphql().client();
  }

  @Nested
  @DisplayName("should return the expected schema for each filter fields")
  class ShouldReturnTheExpectedSchemaForEachFilterFields {
    private List<Map<String, Object>> inputFields;

    @BeforeEach
    void beforeEach() {
      GraphQLResponse queryResult = client.query(FILTER_QUERY);

      assertThat(queryResult.get("__type.__typename")).isEqualTo(__TYPE);
      assertThat(queryResult.get("__type.name")).isEqualTo("ListSchemaReadModelFilter");
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
          "readonlyArray",
          "and",
          "or",
          "not");
    }

    @Test
    @Order(2)
    @DisplayName("For read only property")
    void forReadOnlyProperty() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "readOnlyProperty",
          "type", object("__typename", __TYPE, "name", STRING_PROPERTY_FILTER, "kind", INPUT_OBJECT));
      assertThat(inputFields.get(0)).isEqualTo(expectedResult);
    }

    @Test
    @Order(3)
    @DisplayName("For private property")
    void forPrivateProperty() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "privateProperty",
          "type", object("__typename", __TYPE, "name", STRING_PROPERTY_FILTER, "kind", INPUT_OBJECT));
      assertThat(inputFields.get(1)).isEqualTo(expectedResult);
    }

    @Test
    @Order(4)
    @DisplayName("For public property")
    void forPublicProperty() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "publicProperty",
          "type", object("__typename", __TYPE, "name", STRING_PROPERTY_FILTER, "kind", INPUT_OBJECT));
      assertThat(inputFields.get(2)).isEqualTo(expectedResult);
    }

    @Test
    @Order(5)
    @DisplayName("For id (UUID)")
    void forIdUuid() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "id",
          "type", object("__typename", __TYPE, "name", UUID_PROPERTY_FILTER, "kind", INPUT_OBJECT));
      assertThat(inputFields.get(3)).isEqualTo(expectedResult);
    }

    @Test
    @Order(6)
    @DisplayName("For dates")
    void forDates() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "date",
          "type", object("__typename", __TYPE, "name", DATE_PROPERTY_FILTER, "kind", INPUT_OBJECT));
      assertThat(inputFields.get(4)).isEqualTo(expectedResult);
    }

    @Test
    @Order(7)
    @DisplayName("For string arrays defined with brackets")
    void forStringArraysDefinedWithBrackets() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "array0",
          "type", object("__typename", __TYPE, "name", STRING_ARRAY_PROPERTY_FILTER, "kind", INPUT_OBJECT));
      assertThat(inputFields.get(5)).isEqualTo(expectedResult);
    }

    @Test
    @Order(8)
    @DisplayName("For string arrays defined with Array")
    void forStringArraysDefinedWithArray() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "array1",
          "type", object("__typename", __TYPE, "name", STRING_ARRAY_PROPERTY_FILTER, "kind", INPUT_OBJECT));
      assertThat(inputFields.get(6)).isEqualTo(expectedResult);
    }

    @Test
    @Order(9)
    @DisplayName("For union arrays")
    void forUnionArrays() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "unionArrays",
          "type", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR));
      // TypeScript notes the ideal mapping: INPUT_OBJECT UndefinedPropertyFilter
      assertThat(inputFields.get(7)).isEqualTo(expectedResult);
    }

    @Test
    @Order(10)
    @DisplayName("For union with null")
    void forUnionWithNull() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "unionWithNull",
          "type", object("__typename", __TYPE, "name", STRING_PROPERTY_FILTER, "kind", INPUT_OBJECT));
      assertThat(inputFields.get(8)).isEqualTo(expectedResult);
    }

    @Test
    @Order(11)
    @DisplayName("For union with undefined")
    void forUnionWithUndefined() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "unionWithUndefined",
          "type", object("__typename", __TYPE, "name", STRING_PROPERTY_FILTER, "kind", INPUT_OBJECT));
      assertThat(inputFields.get(9)).isEqualTo(expectedResult);
    }

    @Test
    @Order(12)
    @DisplayName("For union with any")
    void forUnionWithAny() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "unionWithAny",
          "type", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR));
      assertThat(inputFields.get(10)).isEqualTo(expectedResult);
    }

    @Test
    @Order(13)
    @DisplayName("For union with object")
    void forUnionWithObject() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "unionWithObject",
          "type", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR));
      assertThat(inputFields.get(11)).isEqualTo(expectedResult);
    }

    @Test
    @Order(14)
    @DisplayName("For union with unknown")
    void forUnionWithUnknown() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "unionWithUnknown",
          "type", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR));
      assertThat(inputFields.get(12)).isEqualTo(expectedResult);
    }

    @Test
    @Order(15)
    @DisplayName("For functions")
    void forFunctions() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "func0",
          "type", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR));
      assertThat(inputFields.get(13)).isEqualTo(expectedResult);
    }

    @Test
    @Order(16)
    @DisplayName("For any")
    void forAny() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "any0",
          "type", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR));
      assertThat(inputFields.get(14)).isEqualTo(expectedResult);
    }

    @Test
    @Order(17)
    @DisplayName("For unknown")
    void forUnknown() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "unknown0",
          "type", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR));
      assertThat(inputFields.get(15)).isEqualTo(expectedResult);
    }

    @Test
    @Order(18)
    @DisplayName("For record")
    void forRecord() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "record",
          "type", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR));
      assertThat(inputFields.get(16)).isEqualTo(expectedResult);
    }

    @Test
    @Order(19)
    @DisplayName("For generic")
    void forGeneric() {
      GraphQLResponse queryResult = client.query(NESTED_FILTER_QUERY);
      inputFields = inputFieldsOf(queryResult);
      assertThat(at(inputFields, "[17].name")).isEqualTo("generic");
      assertThat(at(inputFields, "[17].__typename")).isEqualTo(__INPUT_VALUE);
      assertThat(at(inputFields, "[17].type.kind")).isEqualTo(INPUT_OBJECT);
      assertThat(at(inputFields, "[17].type.name")).isEqualTo("GenericClass_string_PropertyFilter");
      assertThat(at(inputFields, "[17].type.__typename")).isEqualTo(__TYPE);
      assertThat(at(inputFields, "[17].type.inputFields[0].name")).isEqualTo("genericValue");
      assertThat(at(inputFields, "[17].type.inputFields[0].type.kind")).isEqualTo(SCALAR);
      assertThat(at(inputFields, "[17].type.inputFields[0].type.name")).isEqualTo(JSON_OBJECT);
      assertThat(at(inputFields, "[17].type.inputFields[0].type.__typename")).isEqualTo(__TYPE);
    }

    @Test
    @Order(20)
    @DisplayName("For base class")
    void forBaseClass() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "child",
          "type", object("__typename", __TYPE, "name", BASE_CLASS_PROPERTY_FILTER, "kind", INPUT_OBJECT));
      assertThat(inputFields.get(18)).isEqualTo(expectedResult);
    }

    @Test
    @Order(21)
    @DisplayName("For optional string")
    void forOptionalString() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "optionalString",
          "type", object("__typename", __TYPE, "name", STRING_PROPERTY_FILTER, "kind", INPUT_OBJECT));
      assertThat(inputFields.get(19)).isEqualTo(expectedResult);
    }

    @Test
    @Order(22)
    @DisplayName("For optional null")
    void forOptionalNull() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "optionalNull",
          "type", object("__typename", __TYPE, "name", STRING_PROPERTY_FILTER, "kind", INPUT_OBJECT));
      assertThat(inputFields.get(20)).isEqualTo(expectedResult);
    }

    @Test
    @Order(23)
    @DisplayName("For optional undefined")
    void forOptionalUndefined() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "optionalUndefined",
          "type", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR));
      assertThat(inputFields.get(21)).isEqualTo(expectedResult);
    }

    @Test
    @Order(24)
    @DisplayName("For optional unknown")
    void forOptionalUnknown() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "optionalUnknown",
          "type", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR));
      assertThat(inputFields.get(22)).isEqualTo(expectedResult);
    }

    @Test
    @Order(25)
    @DisplayName("For optional any")
    void forOptionalAny() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "optionalAny",
          "type", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR));
      assertThat(inputFields.get(23)).isEqualTo(expectedResult);
    }

    @Test
    @Order(26)
    @DisplayName("For optional record")
    void forOptionalRecord() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "optionalRecord",
          "type", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR));
      assertThat(inputFields.get(24)).isEqualTo(expectedResult);
    }

    @Test
    @Order(27)
    @DisplayName("For optional generic")
    void forOptionalGeneric() {
      GraphQLResponse queryResult = client.query(NESTED_FILTER_QUERY);
      inputFields = inputFieldsOf(queryResult);
      assertThat(at(inputFields, "[25].name")).isEqualTo("optionalGeneric");
      assertThat(at(inputFields, "[25].__typename")).isEqualTo(__INPUT_VALUE);
      assertThat(at(inputFields, "[25].type.kind")).isEqualTo(INPUT_OBJECT);
      assertThat(at(inputFields, "[25].type.name")).isEqualTo("GenericClass_Cart_PropertyFilter");
      assertThat(at(inputFields, "[25].type.__typename")).isEqualTo(__TYPE);
      assertThat(at(inputFields, "[25].type.inputFields[0].name")).isEqualTo("genericValue");
      assertThat(at(inputFields, "[25].type.inputFields[0].type.kind")).isEqualTo(SCALAR);
      assertThat(at(inputFields, "[25].type.inputFields[0].type.name")).isEqualTo(JSON_OBJECT);
      assertThat(at(inputFields, "[25].type.inputFields[0].type.__typename")).isEqualTo(__TYPE);
    }

    @Test
    @Order(28)
    @DisplayName("For optional base class")
    void forOptionalBaseClass() {
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
    @Disabled(READONLY_ARRAY_DIFFERENCE)
    @DisplayName("For readonly array")
    void forReadonlyArray() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "readonlyArray",
          "type", object("__typename", __TYPE, "name", JSON_OBJECT, "kind", SCALAR));
      assertThat(inputFields.get(27)).isEqualTo(expectedResult);
    }

    @Test
    @Order(30)
    @DisplayName("For readonly array as a Java list")
    void forReadonlyArrayAsAJavaList() {
      Map<String, Object> expectedResult = object(
          "__typename", __INPUT_VALUE,
          "name", "readonlyArray",
          "type", object("__typename", __TYPE, "name", STRING_ARRAY_PROPERTY_FILTER, "kind", INPUT_OBJECT));
      // Java: a List<String> like array0 and array1 (TypeScript: JSON for ReadonlyArray<string>)
      assertThat(inputFields.get(27)).isEqualTo(expectedResult);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> inputFieldsOf(GraphQLResponse queryResult) {
    return (List<Map<String, Object>>) queryResult.get("__type.inputFields");
  }
}
