package com.boostercloud.integration.readmodels;

import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionInfo;
import com.boostercloud.framework.types.concepts.ProjectionInfoReason;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.ReadModelAuthorizer;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.integration.entities.Product;
import java.util.concurrent.ThreadLocalRandom;

/**
 * An "insightful report" per product ({@code src/read-models/special-reports-read-model.ts}), readable only by users
 * whose {@code specialReportAccess} claim is the string {@code 'true'}.
 *
 * @param id the product
 * @param luck a non-deterministic number
 */
@ReadModel(authorize = SpecialReportsReadModel.SpecialReportAccess.class)
public record SpecialReportsReadModel(UUID id, double luck) {

  /**
   * @param product the product
   * @param oldCounterReadModel the current read model, or {@code null}
   * @param projectionInfo why it is called
   * @return the read model, or a deletion when the product's event was deleted
   */
  @Projects(entity = Product.class, joinKey = "id", unProject = "updateCounter")
  public static ProjectionResult<SpecialReportsReadModel> updateCounter(
      Product product, SpecialReportsReadModel oldCounterReadModel, ProjectionInfo projectionInfo) {
    if (projectionInfo != null && projectionInfo.reason() == ProjectionInfoReason.ENTITY_DELETED) {
      return ProjectionResult.delete();
    }
    double luck = product.description().length() + product.displayName().length()
        + ThreadLocalRandom.current().nextDouble();
    return ProjectionResult.of(new SpecialReportsReadModel(product.id(), luck));
  }

  /** Rejects with {@code You are not allowed to see such insightful report} unless the claim is {@code 'true'}. */
  public static final class SpecialReportAccess implements ReadModelAuthorizer {
    @Override
    public void authorize(UserEnvelope currentUser, ReadModelRequestEnvelope readModelRequestEnvelope) {
      if (currentUser == null || !"true".equals(currentUser.claims().get("specialReportAccess"))) {
        throw new NotAuthorizedError("You are not allowed to see such insightful report");
      }
    }
  }
}
