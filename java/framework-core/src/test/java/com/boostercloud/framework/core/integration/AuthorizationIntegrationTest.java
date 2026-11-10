package com.boostercloud.framework.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Authorization with real RS256 tokens verified by the application's {@code PublicKeyTokenVerifier}: role-protected
 * commands, queries, read models and event streams, public resources, and every way a token can be rejected.
 */
class AuthorizationIntegrationTest extends IntegrationTestSupport {

  private static final String CREATE_PRODUCT =
      "mutation { CreateProduct(input: {sku: \"S-1\", displayName: \"Sock\", price: 3, category: \"clothes\"}) }";

  @Test
  void anAdminCanRunAnAdminCommand() {
    Map<String, Object> result = graphQL(CREATE_PRODUCT, TestTokens.admin());

    assertThat((String) data(result).get("CreateProduct")).isNotBlank();
    assertThat(provider.events().events()).singleElement().satisfies(event -> {
      assertThat(event.currentUser().username()).isEqualTo("admin@store.test");
      assertThat(event.currentUser().id()).isEqualTo("admin-1");
      assertThat(event.currentUser().roles()).containsExactly("Admin");
    });
  }

  @Test
  void otherUsersAreDeniedTheAdminCommand() {
    for (String token : new String[] {null, TestTokens.customer(), TestTokens.token("x", "x@store.test", List.of())}) {
      Map<String, Object> result = graphQL(CREATE_PRODUCT, token);

      assertThat(errorMessage(result)).isEqualTo("Access denied for this resource");
      assertThat(errors(result).getFirst()).containsEntry("extensions", Map.of("code", "NotAuthorizedError"))
          .containsEntry("path", List.of("CreateProduct"));
      assertThat(result.get("data")).isNull();
    }
    assertThat(provider.events().events()).isEmpty();
  }

  @Test
  void aCustomerCanRunACustomerCommandButNotAnAdmin() {
    String command = "mutation { AbandonCart(input: {cartId: \"cart-1\"}) }";

    assertThat(data(graphQL(command, TestTokens.customer()))).isEqualTo(Map.of("AbandonCart", true));
    assertThat(errorMessage(graphQL(command, TestTokens.admin()))).isEqualTo("Access denied for this resource");
  }

  @Test
  void readModelsWithRolesRequireOneOfThem() {
    graphQL(CREATE_PRODUCT, TestTokens.admin());
    String query = "{ ProductReadModels(filter: {}) { sku } }";

    assertThat(data(graphQL(query, TestTokens.customer()))).isEqualTo(Map.of("ProductReadModels",
        List.of(Map.of("sku", "S-1"))));
    assertThat(data(graphQL(query, TestTokens.admin()))).isEqualTo(Map.of("ProductReadModels",
        List.of(Map.of("sku", "S-1"))));
    assertThat(errorMessage(graphQL(query))).isEqualTo("Access denied for this resource");
    assertThat(errorMessage(graphQL(query, TestTokens.token("x", "x@store.test", List.of("Visitor")))))
        .isEqualTo("Access denied for this resource");
  }

  @Test
  void publicResourcesNeedNoToken() {
    assertThat(data(graphQL("mutation { ChangeCartItem(input: {cartId: \"c\", productId: \"p\", quantity: 1}) }")))
        .isEqualTo(Map.of("ChangeCartItem", true));
    assertThat(data(graphQL("{ CartReadModel(id: \"c\") { id } }"))).isEqualTo(Map.of("CartReadModel",
        Map.of("id", "c")));
    assertThat(provider.events().events()).first().satisfies(event -> assertThat(event.currentUser()).isNull());
  }

  @Test
  void anAdminQueryIsDeniedToCustomers() {
    String query = "{ ProductsInCategory(input: {category: \"x\"}) }";

    assertThat(data(graphQL(query, TestTokens.admin()))).isEqualTo(Map.of("ProductsInCategory", List.of()));
    assertThat(errorMessage(graphQL(query, TestTokens.customer()))).isEqualTo("Access denied for this resource");
  }

  @Test
  void anExpiredTokenIsRejected() {
    Map<String, Object> result = graphQL("{ CartReadModel(id: \"c\") { id } }", TestTokens.expiredCustomer());

    assertThat(errorMessage(result)).isEqualTo("TokenExpiredError: jwt expired");
    assertThat(errors(result).getFirst().get("extensions")).isEqualTo(Map.of("code", "BoosterTokenExpiredError"));
    assertThat(result).doesNotContainKey("data");
  }

  @Test
  void aTokenSignedWithAnotherKeyIsRejected() {
    Map<String, Object> result = graphQL("{ CartReadModel(id: \"c\") { id } }", TestTokens.forgedAdmin());

    assertThat(errorMessage(result)).isEqualTo("JsonWebTokenError: invalid signature");
    assertThat(errors(result).getFirst().get("extensions")).isEqualTo(Map.of("code", "NotAuthorizedError"));
  }

  @Test
  void aTokenFromAnotherIssuerIsRejected() {
    Map<String, Object> result = graphQL("{ CartReadModel(id: \"c\") { id } }", TestTokens.adminFromAnotherIssuer());

    assertThat(errorMessage(result)).isEqualTo("JsonWebTokenError: jwt issuer invalid. expected: "
        + TestTokens.ISSUER);
  }

  @Test
  void garbageIsNotAToken() {
    assertThat(errorMessage(graphQL("{ CartReadModel(id: \"c\") { id } }", "not-a-token")))
        .isEqualTo("JsonWebTokenError: jwt malformed");
  }

  @Test
  void theEventStreamOfAnEntityFollowsItsAuthorizer() {
    graphQL(CREATE_PRODUCT, TestTokens.admin());
    graphQL("mutation { ChangeCartItem(input: {cartId: \"c\", productId: \"p\", quantity: 1}) }");

    assertThat(listAt(data(graphQL("{ eventsByEntity(entity: Product) { type } }", TestTokens.admin())),
        "eventsByEntity")).extracting(event -> event.get("type")).containsExactly("ProductCreated");
    assertThat(errorMessage(graphQL("{ eventsByEntity(entity: Product) { type } }", TestTokens.customer())))
        .isEqualTo("Access denied for this resource");
    assertThat(errorMessage(graphQL("{ eventsByType(type: ProductCreated) { type } }", TestTokens.customer())))
        .isEqualTo("Access denied for this resource");
    assertThat(listAt(data(graphQL("{ eventsByEntity(entity: Cart, entityID: \"c\") { type } }")), "eventsByEntity"))
        .extracting(event -> event.get("type")).containsExactly("CartChecked", "CartItemChanged");
  }
}
