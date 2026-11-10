package com.boostercloud.framework.core.integration.app.entities;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.core.integration.app.common.Rating;
import com.boostercloud.framework.core.integration.app.events.ProductReviewed;
import com.boostercloud.framework.types.concepts.TimeKey;
import com.boostercloud.framework.types.concepts.UUID;
import java.time.Instant;
import java.util.Optional;

/**
 * The latest review of a product (its {@code reviewedAt} is the sequence key of the review read model).
 *
 * @param id the product
 * @param reviewedAt the time key of the latest review
 * @param rating its rating
 * @param comment its comment
 * @param reviewedOn when it was written
 * @param reviewer who wrote it
 * @param reviews how many reviews the product has
 */
@Entity(authorizeReadEvents = All.class)
public record ProductReview(UUID id, TimeKey reviewedAt, Rating rating, Optional<String> comment, Instant reviewedOn,
    String reviewer, int reviews) {

  /**
   * @param event the review
   * @param current the previous state, or {@code null}
   * @return the new state
   */
  @Reduces(ProductReviewed.class)
  public static ProductReview reviewed(ProductReviewed event, ProductReview current) {
    return new ProductReview(event.productId(), event.reviewedAt(), event.rating(), event.comment(),
        event.reviewedOn(), event.reviewer(), current == null ? 1 : current.reviews() + 1);
  }
}
