package com.boostercloud.framework.core.fixtures.app.domain;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * An event of the fixture application.
 *
 * @param cartId the cart
 */
@Event
public record CartCreated(UUID cartId) {
  /**
   * @return the cart id
   */
  public UUID entityID() {
    return cartId;
  }
}
