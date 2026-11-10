package com.boostercloud.framework.core.integration.app.readmodels;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.core.decorators.SequencedBy;
import com.boostercloud.framework.core.integration.app.common.Rating;
import com.boostercloud.framework.core.integration.app.entities.ProductReview;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.TimeKey;
import com.boostercloud.framework.types.concepts.UUID;
import java.time.Instant;
import java.util.Optional;

/**
 * Every review of a product: a sequenced read model (one row per {@code reviewedAt} for the same product id).
 *
 * @param id the product
 * @param reviewedAt the sequence key
 * @param rating the rating
 * @param comment the comment, when there is one
 * @param reviewedOn when
 * @param reviewer who
 */
@ReadModel(authorize = All.class)
public record ProductReviewReadModel(UUID id, @SequencedBy TimeKey reviewedAt, Rating rating, Optional<String> comment,
    Instant reviewedOn, String reviewer) {

  /**
   * @param review the latest review
   * @param current the row of that review, or {@code null}
   * @return the row
   */
  @Projects(entity = ProductReview.class, joinKey = "id")
  public static ProjectionResult<ProductReviewReadModel> project(ProductReview review, ProductReviewReadModel current) {
    return ProjectionResult.of(new ProductReviewReadModel(review.id(), review.reviewedAt(), review.rating(),
        review.comment(), review.reviewedOn(), review.reviewer()));
  }
}
