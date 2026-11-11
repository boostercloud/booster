package com.boostercloud.integration.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.integration.events.StockMoved;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The reducer and the event-read authorizer of {@link Stock} ({@code src/entities/stock.ts}). */
class StockTest {

  @Test
  void theFirstMoveCreatesTheStockWithAnEmptyOrigin() {
    Stock stock = Stock.stockMoved(new StockMoved("p1", "provider", "madrid", 10), null);

    assertThat(stock.id()).isEqualTo(UUID.of("p1"));
    assertThat(stock.warehouses()).containsExactly(Map.entry("provider", 0.0), Map.entry("madrid", 10.0));
  }

  @Test
  void theFirstMoveToTheOriginItselfKeepsTheQuantity() {
    Stock stock = Stock.stockMoved(new StockMoved("p1", "madrid", "madrid", 10), null);

    assertThat(stock.warehouses()).containsExactly(Map.entry("madrid", 10.0));
  }

  @Test
  void laterMovesSubtractFromTheOriginAndAddToTheDestination() {
    Stock stock = Stock.stockMoved(new StockMoved("p1", "provider", "madrid", 10), null);
    stock = Stock.stockMoved(new StockMoved("p1", "madrid", "provider", 4), stock);

    assertThat(stock.warehouses()).containsEntry("madrid", 6.0).containsEntry("provider", 4.0);
  }

  @Test
  void anUnknownWarehouseOfAnExistingStockBecomesNaNLikeInJavaScript() {
    Stock stock = Stock.stockMoved(new StockMoved("p1", "provider", "madrid", 10), null);
    stock = Stock.stockMoved(new StockMoved("p1", "madrid", "bilbao", 3), stock);

    assertThat(stock.warehouses().get("madrid")).isEqualTo(7.0);
    assertThat(stock.warehouses().get("bilbao")).isNaN();
  }

  @Test
  void onlyTheMagicWordReadsTheEvents() {
    Stock.MagicWord authorizer = new Stock.MagicWord();
    UserEnvelope withWord = new UserEnvelope(null, "u", List.of(), Map.of("magicWord", "opensesame"), null);
    UserEnvelope withoutWord = new UserEnvelope(null, "u", List.of(), Map.of("magicWord", "please"), null);

    authorizer.authorize(withWord, null);
    assertThatThrownBy(() -> authorizer.authorize(withoutWord, null))
        .isInstanceOf(NotAuthorizedError.class)
        .hasMessage("You don't know the magic word!");
    assertThatThrownBy(() -> authorizer.authorize(null, null)).isInstanceOf(NotAuthorizedError.class);
  }
}
