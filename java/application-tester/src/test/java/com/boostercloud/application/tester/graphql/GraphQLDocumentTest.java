package com.boostercloud.application.tester.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GraphQLDocumentTest {

  private static Map.Entry<String, Object> entry(String key, Object value) {
    return new java.util.AbstractMap.SimpleEntry<>(key, value);
  }

  /** The printed document with whitespace collapsed, for readable assertions. */
  private static String compact(GraphQLDocument document) {
    return String.join(" ", Arrays.stream(document.print().trim().split("\\s+")).toList());
  }

  @Nested
  @DisplayName("addTypename")
  class AddTypename {

    @Test
    @DisplayName("adds __typename to every nested selection set but not to the operation root")
    void nestedSelections() {
      GraphQLDocument document = GraphQLDocument.parse("""
          query CartReadModel($cartId: ID!) {
            CartReadModel(id: $cartId) {
              id
              cartItems {
                productId
                quantity
              }
            }
          }
          """).withTypename();

      assertThat(compact(document)).isEqualTo(
          "query CartReadModel($cartId: ID!) { CartReadModel(id: $cartId) { id cartItems { productId quantity"
              + " __typename } __typename } }");
    }

    @Test
    @DisplayName("leaves scalar root fields alone")
    void scalarRoot() {
      GraphQLDocument document = GraphQLDocument.parse("""
          mutation ChangeCartItem($cartId: ID!) { ChangeCartItem(input: { cartId: $cartId }) }
          """).withTypename();
      assertThat(compact(document)).isEqualTo(
          "mutation ChangeCartItem($cartId: ID!) { ChangeCartItem(input: {cartId : $cartId}) }");
    }

    @Test
    @DisplayName("does not add it twice, nor to introspection selections")
    void existingTypenameAndIntrospection() {
      GraphQLDocument document = GraphQLDocument.parse("""
          query UniversalQuery {
            __type(name: "ListSchemaReadModelFilter") {
              __typename
              name
              inputFields { name type { name kind } }
            }
            other { __schema { types { name } } }
          }
          """).withTypename();
      assertThat(compact(document)).isEqualTo(
          "query UniversalQuery { __type(name: \"ListSchemaReadModelFilter\") { __typename name inputFields { name"
              + " type { name kind __typename } __typename } } other { __schema { types { name __typename } __typename } } }");
    }

    @Test
    @DisplayName("adds it to fragments and inline fragments")
    void fragments() {
      GraphQLDocument document = GraphQLDocument.parse("""
          query Q { items { ...Item ... on Cart { id } } }
          fragment Item on Product { sku price { cents } }
          """).withTypename();
      assertThat(compact(document)).isEqualTo(
          "query Q { items { ...Item ... on Cart { id __typename } __typename } }"
              + " fragment Item on Product { sku price { cents __typename } __typename }");
    }

    @Test
    @DisplayName("skips selection sets of fields with @export")
    void export() {
      GraphQLDocument document =
          GraphQLDocument.parse("query Q { user @export(as: \"id\") { id } other { id } }").withTypename();
      assertThat(compact(document))
          .isEqualTo("query Q { user @export(as: \"id\") { id } other { id __typename } }");
    }

    @Test
    @DisplayName("keeps aliases, arguments and directives")
    void keepsTheRest() {
      GraphQLDocument document = GraphQLDocument.parse(
          "query Q($x: Boolean!) { a: cart(id: \"1\") @include(if: $x) { b: items(first: 2) { id } } }").withTypename();
      assertThat(compact(document)).isEqualTo(
          "query Q($x: Boolean!) { a: cart(id: \"1\") @include(if: $x) { b: items(first: 2) { id __typename }"
              + " __typename } }");
    }
  }

  @Nested
  @DisplayName("document helpers")
  class Helpers {

    @Test
    @DisplayName("operationName is the first named operation, empty for anonymous ones")
    void operationName() {
      assertThat(GraphQLDocument.parse("query Cart { a }").operationName()).contains("Cart");
      assertThat(GraphQLDocument.parse("{ a }").operationName()).isEmpty();
      assertThat(GraphQLDocument.parse("fragment F on T { a }").operationName()).isEmpty();
    }

    @Test
    @DisplayName("mainDefinition prefers operations, then fragments")
    void mainDefinition() {
      assertThat(GraphQLDocument.parse("fragment F on T { a } subscription S { b }").mainDefinition())
          .isInstanceOf(OperationDefinition.class);
      assertThat(GraphQLDocument.parse("fragment F on T { a }").mainDefinition()).isInstanceOf(FragmentDefinition.class);
    }

    @Test
    @DisplayName("isSubscription follows the main definition")
    void isSubscription() {
      assertThat(GraphQLDocument.parse("subscription S { a { id } }").isSubscription()).isTrue();
      assertThat(GraphQLDocument.parse("fragment F on T { a } subscription S { b }").isSubscription()).isTrue();
      assertThat(GraphQLDocument.parse("query Q { a }").isSubscription()).isFalse();
      assertThat(GraphQLDocument.parse("mutation M { a }").isSubscription()).isFalse();
      assertThat(GraphQLDocument.parse("fragment F on T { a }").isSubscription()).isFalse();
    }

    @Test
    @DisplayName("defaultVariableValues converts the defaults of the operation's variables")
    void defaults() {
      Map<String, Object> defaults = GraphQLDocument.parse("""
          query Q($limit: Int = 10, $big: Int = 99999999999, $f: Float = 1.5, $s: String = "x", $b: Boolean = true,
                  $e: Direction = ASC, $n: String = null, $l: [Int] = [1, 2], $o: In = {a: {b: "c"}}, $none: ID) {
            a
          }
          """).defaultVariableValues();
      assertThat(defaults).containsExactly(
          Map.entry("limit", 10), Map.entry("big", 99999999999L), Map.entry("f", 1.5), Map.entry("s", "x"),
          Map.entry("b", true), Map.entry("e", "ASC"), entry("n", null), Map.entry("l", List.of(1, 2)),
          Map.entry("o", Map.of("a", Map.of("b", "c"))));
    }

    @Test
    @DisplayName("referencedVariables lists the variables used outside their definitions")
    void referencedVariables() {
      GraphQLDocument document = GraphQLDocument.parse("""
          query Q($cartId: ID!, $unused: String, $flag: Boolean!) {
            cart(id: $cartId) @include(if: $flag) { ...F }
          }
          fragment F on Cart { items(filter: { sku: { eq: $sku } }) { id } }
          """);
      assertThat(document.referencedVariables()).containsExactly("cartId", "flag", "sku");
    }
  }

  @Nested
  @DisplayName("checks")
  class Checks {

    @Test
    @DisplayName("rejects documents with several operations")
    void ambiguous() {
      assertThatThrownBy(() -> GraphQLDocument.parse("query A { a } query B { b }"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Ambiguous GraphQL document: contains 2 operations");
    }

    @Test
    @DisplayName("rejects type-system definitions")
    void schemaDefinitions() {
      assertThatThrownBy(() -> GraphQLDocument.parse("type Cart { id: ID }"))
          .hasMessage("Schema type definitions not allowed in queries. Found: \"ObjectTypeDefinition\"");
      assertThatThrownBy(() -> GraphQLDocument.parse("extend type Cart { id: ID }"))
          .hasMessage("Schema type definitions not allowed in queries. Found: \"ObjectTypeExtension\"");
    }

    @Test
    @DisplayName("reports syntax errors")
    void syntax() {
      assertThatThrownBy(() -> GraphQLDocument.parse("query { "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageStartingWith("Syntax Error: ");
    }
  }

  @Nested
  @DisplayName("Operation.prepare")
  class Prepare {

    @Test
    @DisplayName("merges the variables over the defaults and keeps null values")
    void variables() {
      java.util.Map<String, Object> variables = new java.util.LinkedHashMap<>();
      variables.put("limit", 5);
      variables.put("extra", null);
      Operation operation = Operation.prepare("query Q($limit: Int = 10, $x: Int = 1) { a }", variables, true);
      assertThat(operation.variables()).containsExactly(
          Map.entry("limit", 5), Map.entry("x", 1), entry("extra", null));
      assertThat(operation.operationName()).isEqualTo("Q");
      assertThat(operation.subscription()).isFalse();
      assertThat(operation.extensions()).isEmpty();
    }

    @Test
    @DisplayName("builds the websocket start payload in Apollo's key order")
    void startPayload() {
      Operation named = Operation.prepare("subscription S { a { id } }", Map.of("x", 1), true);
      assertThat(named.toStartPayload().keySet()).containsExactly("variables", "extensions", "operationName", "query");
      assertThat(named.subscription()).isTrue();
      Operation anonymous = Operation.prepare("subscription { a { id } }", null, false);
      assertThat(anonymous.toStartPayload().keySet()).containsExactly("variables", "extensions", "query");
      assertThat(anonymous.query()).doesNotContain("__typename");
    }
  }
}
