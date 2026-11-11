package com.boostercloud.framework.core.integration.app.commands;

import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.core.integration.app.common.Rating;
import com.boostercloud.framework.core.integration.app.events.ProductReviewed;
import com.boostercloud.framework.core.integration.app.roles.Customer;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.TimeKey;
import com.boostercloud.framework.types.concepts.UUID;
import java.util.Optional;

/**
 * A customer reviews a product; returns the time key of the review.
 *
 * @param productId the product
 * @param rating the rating
 * @param comment an optional comment
 */
@Command(authorize = Customer.class)
public record ReviewProduct(UUID productId, Rating rating, Optional<String> comment) {
  /**
   * @param command the command
   * @param register the register
   * @return the time key of the review
   */
  public static String handle(ReviewProduct command, Register register) {
    TimeKey reviewedAt = TimeKey.generate();
    register.events(new ProductReviewed(command.productId(), reviewedAt, command.rating(), command.comment(),
        Dates.now(), register.currentUser().username()));
    return reviewedAt.value();
  }
}
