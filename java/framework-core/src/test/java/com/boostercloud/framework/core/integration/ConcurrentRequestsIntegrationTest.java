package com.boostercloud.framework.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.integration.app.readmodels.CartReadModel;
import com.boostercloud.framework.core.integration.app.readmodels.CategoryReadModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Requests served at the same time, as a server does: commands for many carts and products sent from parallel
 * threads all end up reduced and projected, including into one shared read model.
 */
class ConcurrentRequestsIntegrationTest extends IntegrationTestSupport {

  @Test
  void parallelRequestsAreAllProcessed() throws Exception {
    List<Callable<Map<String, Object>>> requests = new ArrayList<>();
    for (int i = 0; i < 24; i++) {
      String cart = "cart-" + i;
      int quantity = i + 20; // 13 would make the event handler fail
      requests.add(() -> graphQL("mutation { ChangeCartItem(input: {cartId: \"" + cart + "\", productId: \"p\", "
          + "quantity: " + quantity + "}) }"));
      String sku = "SKU-" + i;
      requests.add(() -> graphQL("mutation { CreateProduct(input: {sku: \"" + sku + "\", displayName: \"P\", "
          + "price: 1, category: \"shared\"}) }", TestTokens.admin()));
    }

    List<Map<String, Object>> results = new ArrayList<>();
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (Future<Map<String, Object>> future : executor.invokeAll(requests)) {
        results.add(future.get());
      }
    }

    assertThat(results).allSatisfy(result -> assertThat(result).doesNotContainKey("errors"));
    List<CartReadModel> carts = Booster.readModel(CartReadModel.class).search();
    assertThat(carts).hasSize(24).allSatisfy(cart -> {
      assertThat(cart.checks()).isEqualTo(1);
      assertThat(cart.totalQuantity()).isEqualTo(Integer.parseInt(cart.id().value().substring("cart-".length())) + 20);
    });
    CategoryReadModel shared = Booster.readModel(CategoryReadModel.class)
        .filter(Map.of("id", Map.of("eq", "shared"))).searchOne().orElseThrow();
    assertThat(shared.productIds()).hasSize(24).doesNotHaveDuplicates();
    assertThat(RecordingLogger.INSTANCE.errors()).isEmpty();
  }
}
