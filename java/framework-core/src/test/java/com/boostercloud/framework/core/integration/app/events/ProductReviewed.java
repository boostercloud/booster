package com.boostercloud.framework.core.integration.app.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.core.integration.app.common.Rating;
import com.boostercloud.framework.types.concepts.TimeKey;
import com.boostercloud.framework.types.concepts.UUID;
import java.time.Instant;
import java.util.Optional;

/**
 * A customer reviewed a product.
 *
 * @param productId the product
 * @param reviewedAt the time key of the review
 * @param rating the rating
 * @param comment an optional comment
 * @param reviewedOn when
 * @param reviewer who
 */
@Event
public record ProductReviewed(UUID productId, TimeKey reviewedAt, Rating rating, Optional<String> comment,
    Instant reviewedOn, String reviewer) {
  /**
   * @return the product
   */
  public UUID entityID() {
    return productId;
  }
}
