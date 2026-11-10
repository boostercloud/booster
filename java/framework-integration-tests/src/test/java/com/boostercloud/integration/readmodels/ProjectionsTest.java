package com.boostercloud.integration.readmodels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.concepts.ProjectionInfo;
import com.boostercloud.framework.types.concepts.ProjectionInfoReason;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.ReadModelAction;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.integration.common.Money;
import com.boostercloud.integration.entities.CarModel;
import com.boostercloud.integration.entities.CarOffers;
import com.boostercloud.integration.entities.CarOwner;
import com.boostercloud.integration.entities.CarPurchase;
import com.boostercloud.integration.entities.Concurrency;
import com.boostercloud.integration.entities.Pack;
import com.boostercloud.integration.entities.Product;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The projection methods, join-key functions and authorizers of the read models ({@code src/read-models/*.ts}) other
 * than {@link CartReadModel} (see {@link CartReadModelTest}).
 */
class ProjectionsTest {

  private static final ProjectionInfo PROJECTED = new ProjectionInfo(ProjectionInfoReason.ENTITY_PROJECTED);
  private static final ProjectionInfo DELETED = new ProjectionInfo(ProjectionInfoReason.ENTITY_DELETED);

  @SuppressWarnings("unchecked")
  static <T> T value(ProjectionResult<T> result) {
    assertThat(result).isInstanceOf(ProjectionResult.Upsert.class);
    return ((ProjectionResult.Upsert<T>) result).value();
  }

  private static Product product(String id, String sku, double cents) {
    return new Product(UUID.of(id), sku, "Chair", "A nice chair", new Money(cents, "EUR"), null, false, 0, null, null);
  }

  @Nested
  class CarPurchases {
    private final CarModel model = new CarModel(UUID.of("m1"), "Panda", "Fiat");
    private final CarOwner owner = new CarOwner(UUID.of("o1"), "John");

    @Test
    void aPurchaseKeepsTheOffersOfTheReadModel() {
      CarOffers offer = new CarOffers(UUID.of("of1"), "Offer 1", List.of("pu1"));
      CarPurchasesReadModel old = new CarPurchasesReadModel(UUID.of("pu1"), null, null, List.of(offer));

      CarPurchasesReadModel result = value(CarPurchasesReadModel.projectWithPurchase(
          new CarPurchase(UUID.of("pu1"), model, owner), old));

      assertThat(result).isEqualTo(new CarPurchasesReadModel(UUID.of("pu1"), model, owner, List.of(offer)));
    }

    @Test
    void ownersAndModelsProjectOnlyIntoExistingPurchases() {
      CarPurchasesReadModel old = new CarPurchasesReadModel(UUID.of("pu1"), model, owner, null);
      CarOwner renamed = new CarOwner(UUID.of("o1"), "Johnny");
      CarModel renamedModel = new CarModel(UUID.of("m1"), "Punto", "Fiat");

      assertThat(CarPurchasesReadModel.projectWithOwner(renamed, null, null)).isEqualTo(ReadModelAction.NOTHING);
      assertThat(CarPurchasesReadModel.projectWithModel(renamedModel, null, null)).isEqualTo(ReadModelAction.NOTHING);
      assertThat(value(CarPurchasesReadModel.projectWithOwner(renamed, UUID.of("pu1"), old)))
          .isEqualTo(new CarPurchasesReadModel(UUID.of("pu1"), model, renamed, null));
      assertThat(value(CarPurchasesReadModel.projectWithModel(renamedModel, UUID.of("pu1"), old)))
          .isEqualTo(new CarPurchasesReadModel(UUID.of("pu1"), renamedModel, owner, null));
    }

    @Test
    void theOwnerJoinKeySkipsOwnersNamedSkip() {
      CarPurchasesReadModel.OwnerJoinKey joinKey = new CarPurchasesReadModel.OwnerJoinKey();

      assertThat(joinKey.joinKey(owner)).isEqualTo(Map.of("carOwner", Map.of("id", Map.of("eq", "o1"))));
      assertThat(joinKey.joinKey(new CarOwner(UUID.of("o1"), "SKIP"))).isNull();
      assertThat(new CarPurchasesReadModel.ModelJoinKey().joinKey(model))
          .isEqualTo(Map.of("carModel", Map.of("id", Map.of("eq", "m1"))));
    }

    @Test
    void offersAreAppendedInOrderAndReplacedInPlace() {
      CarOffers first = new CarOffers(UUID.of("of1"), "Offer 1", List.of("pu1"));
      CarOffers second = new CarOffers(UUID.of("of2"), "Offer 2", List.of("pu1"));
      CarOffers firstChanged = new CarOffers(UUID.of("of1"), "Offer 1 bis", List.of("pu1"));
      CarPurchasesReadModel purchase = new CarPurchasesReadModel(UUID.of("pu1"), model, owner, null);

      assertThat(CarPurchasesReadModel.projectWithOffer(first, UUID.of("pu1"), null))
          .as("an offer never creates a purchase").isEqualTo(ReadModelAction.NOTHING);
      purchase = value(CarPurchasesReadModel.projectWithOffer(first, UUID.of("pu1"), purchase));
      purchase = value(CarPurchasesReadModel.projectWithOffer(second, UUID.of("pu1"), purchase));
      purchase = value(CarPurchasesReadModel.projectWithOffer(firstChanged, UUID.of("pu1"), purchase));

      assertThat(purchase.offers()).containsExactly(firstChanged, second);
      assertThat(purchase.carModel()).isEqualTo(model);
      assertThat(purchase.carOwner()).isEqualTo(owner);
    }
  }

  @Nested
  class Products {

    @Test
    void aProductIsProjectedWithoutPacksAndADeletedOneRemovesTheReadModel() {
      Product product = product("p1", "SKU-1", 1999);

      ProductReadModel readModel = value(ProductReadModel.updateWithProduct(product));

      assertThat(readModel).isEqualTo(new ProductReadModel(UUID.of("p1"), "SKU-1", "Chair", "A nice chair", 0, false,
          Map.of(), "Other", new Money(1999, "EUR"), List.of()));
      Product deleted = new Product(UUID.of("p1"), "SKU-1", "Chair", "A nice chair", new Money(1999, "EUR"), null,
          true, 0, null, null);
      assertThat(ProductReadModel.updateWithProduct(deleted)).isEqualTo(ReadModelAction.DELETE);
      assertThat(ProductReadModel.unProjectWithProduct(product, readModel, DELETED)).isEqualTo(ReadModelAction.DELETE);
    }

    @Test
    void packsAreAddedToExistingProductsAndReplacedById() {
      ProductReadModel current = value(ProductReadModel.updateWithProduct(product("p1", "SKU-1", 1)));
      Pack pack = new Pack(UUID.of("pk1"), "Combo", List.of(UUID.of("p1"), UUID.of("p2")));
      Pack renamed = new Pack(UUID.of("pk1"), "Combo 2", List.of(UUID.of("p1")));

      assertThat(ProductReadModel.updateWithPack(pack, UUID.of("p1"), null, PROJECTED))
          .isEqualTo(ReadModelAction.NOTHING);
      ProductReadModel withPack = value(ProductReadModel.updateWithPack(pack, UUID.of("p1"), current, PROJECTED));
      assertThat(withPack.packs()).containsExactly(pack);
      assertThat(value(ProductReadModel.updateWithPack(renamed, UUID.of("p1"), withPack, PROJECTED)).packs())
          .containsExactly(renamed);
      assertThat(ProductReadModel.updateWithPack(pack, UUID.of("p1"), withPack, DELETED))
          .as("a deleted pack deletes the product read models it references (TypeScript quirk)")
          .isEqualTo(ReadModelAction.DELETE);
    }

    @Test
    void productsBySkuKeepsTheLatestPriceOfEveryProductMostExpensiveFirst() {
      ProductsBySKU bySku = value(ProductsBySKU.projectProduct(product("p1", "SKU-1", 1999), null, PROJECTED));
      bySku = value(ProductsBySKU.projectProduct(product("p2", "SKU-1", 3000), bySku, PROJECTED));
      bySku = value(ProductsBySKU.projectProduct(product("p3", "SKU-1", 500), bySku, PROJECTED));
      bySku = value(ProductsBySKU.projectProduct(product("p1", "SKU-1", 10), bySku, PROJECTED));

      assertThat(bySku.id()).isEqualTo(UUID.of("SKU-1"));
      assertThat(bySku.products()).containsExactly(ProductsBySKU.entry("p2", 3000), ProductsBySKU.entry("p3", 500),
          ProductsBySKU.entry("p1", 10));
      assertThat(bySku.firstProduct()).isEqualTo(ProductsBySKU.entry("p2", 3000));
      assertThat(bySku.record()).isEqualTo(Map.of("items", 3.0));
      assertThat(ProductsBySKU.projectProduct(product("p1", "SKU-1", 10), bySku, DELETED))
          .isEqualTo(ReadModelAction.DELETE);
    }

    @Test
    void productUpdatesRememberThePreviousUpdate() {
      ProductUpdatesReadModel first =
          value(ProductUpdatesReadModel.updateWithProduct(product("p1", "S", 1), null, PROJECTED));
      ProductUpdatesReadModel second =
          value(ProductUpdatesReadModel.updateWithProduct(product("p1", "S", 1), first, PROJECTED));

      assertThat(first.previousUpdate()).isNull();
      assertThat(second.previousUpdate()).isEqualTo(first.lastUpdate());
      assertThat(ProductUpdatesReadModel.updateWithProduct(product("p1", "S", 1), second, DELETED))
          .isEqualTo(ReadModelAction.DELETE);
    }

    @Test
    void specialReportsAreLuckyAndOnlyForUsersWithTheStringClaim() {
      SpecialReportsReadModel report =
          value(SpecialReportsReadModel.updateCounter(product("p1", "S", 1), null, PROJECTED));
      int base = "A nice chair".length() + "Chair".length();
      assertThat(report.luck()).isGreaterThanOrEqualTo(base).isLessThan(base + 1);
      assertThat(SpecialReportsReadModel.updateCounter(product("p1", "S", 1), report, DELETED))
          .isEqualTo(ReadModelAction.DELETE);

      SpecialReportsReadModel.SpecialReportAccess authorizer = new SpecialReportsReadModel.SpecialReportAccess();
      UserEnvelope allowed = new UserEnvelope(null, "u", List.of(), Map.of("specialReportAccess", "true"), null);
      UserEnvelope booleanClaim = new UserEnvelope(null, "u", List.of(), Map.of("specialReportAccess", true), null);
      assertThatCode(() -> authorizer.authorize(allowed, null)).doesNotThrowAnyException();
      for (UserEnvelope denied : java.util.Arrays.asList(booleanClaim, null)) {
        assertThatThrownBy(() -> authorizer.authorize(denied, null))
            .isInstanceOf(NotAuthorizedError.class)
            .hasMessage("You are not allowed to see such insightful report");
      }
    }
  }

  @Test
  void otherConcurrencyProjectsTheEntityByBothJoinKeys() {
    Concurrency concurrency = new Concurrency(UUID.of("a"), UUID.of("b"));

    assertThat(value(OtherConcurrencyReadModel.persisted(concurrency, null)))
        .isEqualTo(new OtherConcurrencyReadModel(UUID.of("a"), UUID.of("b")));
    assertThat(value(OtherConcurrencyReadModel.persistedByOtherId(concurrency, null)))
        .isEqualTo(new OtherConcurrencyReadModel(UUID.of("b"), UUID.of("b")));
  }
}
