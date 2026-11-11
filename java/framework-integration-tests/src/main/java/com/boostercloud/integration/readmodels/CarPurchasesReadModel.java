package com.boostercloud.integration.readmodels;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.ReadModelJoinKeyFunction;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Nullable;
import com.boostercloud.integration.entities.CarModel;
import com.boostercloud.integration.entities.CarOffers;
import com.boostercloud.integration.entities.CarOwner;
import com.boostercloud.integration.entities.CarPurchase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Car purchases with their model, owner and offers ({@code src/read-models/car-purchases-read-model.ts}): an entity
 * join key ({@code CarPurchase.id}), two read-model join keys (functions returning a filter of this read model, for
 * {@code CarOwner} and {@code CarModel}) and an array join key ({@code CarOffers.purchasesIds}).
 *
 * @param id the purchase
 * @param carModel the model
 * @param carOwner the owner
 * @param offers the offers that apply, in arrival order
 */
@ReadModel(authorize = All.class)
public record CarPurchasesReadModel(
    UUID id, @Nullable CarModel carModel, @Nullable CarOwner carOwner, @Nullable List<CarOffers> offers) {

  /**
   * @param purchase the purchase
   * @param oldCarPurchaseReadModel the current read model, or {@code null}
   * @return the read model (keeping the offers)
   */
  @Projects(entity = CarPurchase.class, joinKey = "id")
  public static ProjectionResult<CarPurchasesReadModel> projectWithPurchase(
      CarPurchase purchase, CarPurchasesReadModel oldCarPurchaseReadModel) {
    return ProjectionResult.of(new CarPurchasesReadModel(purchase.id(), purchase.model(), purchase.owner(),
        oldCarPurchaseReadModel == null ? null : oldCarPurchaseReadModel.offers()));
  }

  /**
   * @param owner the owner
   * @param readModelId the purchase to update, or {@code null} when none matches
   * @param oldCarPurchaseReadModel the current read model, or {@code null}
   * @return the read model with the new owner
   */
  @Projects(entity = CarOwner.class, joinKeyFunction = OwnerJoinKey.class)
  public static ProjectionResult<CarPurchasesReadModel> projectWithOwner(
      CarOwner owner, UUID readModelId, CarPurchasesReadModel oldCarPurchaseReadModel) {
    if (readModelId == null) {
      return ProjectionResult.nothing();
    }
    return ProjectionResult.of(new CarPurchasesReadModel(readModelId,
        oldCarPurchaseReadModel == null ? null : oldCarPurchaseReadModel.carModel(), owner,
        oldCarPurchaseReadModel == null ? null : oldCarPurchaseReadModel.offers()));
  }

  /**
   * @param model the model
   * @param readModelId the purchase to update, or {@code null} when none matches
   * @param oldCarPurchaseReadModel the current read model, or {@code null}
   * @return the read model with the new model
   */
  @Projects(entity = CarModel.class, joinKeyFunction = ModelJoinKey.class)
  public static ProjectionResult<CarPurchasesReadModel> projectWithModel(
      CarModel model, UUID readModelId, CarPurchasesReadModel oldCarPurchaseReadModel) {
    if (readModelId == null) {
      return ProjectionResult.nothing();
    }
    return ProjectionResult.of(new CarPurchasesReadModel(readModelId, model,
        oldCarPurchaseReadModel == null ? null : oldCarPurchaseReadModel.carOwner(),
        oldCarPurchaseReadModel == null ? null : oldCarPurchaseReadModel.offers()));
  }

  /**
   * Adds the offer to (or replaces it in) an existing purchase; never creates a purchase.
   *
   * @param carOffers the offer
   * @param readModelId one of its {@code purchasesIds}
   * @param oldCarPurchaseReadModel the current read model, or {@code null}
   * @return the read model with the offer
   */
  @Projects(entity = CarOffers.class, joinKey = "purchasesIds")
  public static ProjectionResult<CarPurchasesReadModel> projectWithOffer(
      CarOffers carOffers, UUID readModelId, CarPurchasesReadModel oldCarPurchaseReadModel) {
    if (oldCarPurchaseReadModel == null) {
      return ProjectionResult.nothing();
    }
    System.out.println("Updating purchase " + readModelId + " with offers " + BoosterJson.toJson(carOffers));
    List<CarOffers> offers =
        oldCarPurchaseReadModel.offers() == null ? new ArrayList<>() : new ArrayList<>(oldCarPurchaseReadModel.offers());
    boolean exists = offers.stream().anyMatch(existing -> existing.id().equals(carOffers.id()));
    if (exists) {
      offers.replaceAll(existing -> existing.id().equals(carOffers.id()) ? carOffers : existing);
    } else {
      offers.add(carOffers);
    }
    return ProjectionResult.of(new CarPurchasesReadModel(oldCarPurchaseReadModel.id(),
        oldCarPurchaseReadModel.carModel(), oldCarPurchaseReadModel.carOwner(), offers));
  }

  /** The purchases of an owner; owners named {@code SKIP} are not projected ({@code undefined} filter). */
  public static final class OwnerJoinKey implements ReadModelJoinKeyFunction<CarOwner> {
    @Override
    public Map<String, Object> joinKey(CarOwner carOwner) {
      if ("SKIP".equals(carOwner.name())) {
        return null;
      }
      return Map.of("carOwner", Map.of("id", Map.of("eq", carOwner.id().value())));
    }
  }

  /** The purchases of a model. */
  public static final class ModelJoinKey implements ReadModelJoinKeyFunction<CarModel> {
    @Override
    public Map<String, Object> joinKey(CarModel carModel) {
      return Map.of("carModel", Map.of("id", Map.of("eq", carModel.id().value())));
    }
  }
}
