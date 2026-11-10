package com.boostercloud.framework.core.integration.app.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A cart was checked by the {@code CheckCartOnChange} event handler.
 *
 * @param cartId the cart
 */
@Event
public record CartChecked(UUID cartId) {
  /**
   * @return the cart
   */
  public UUID entityID() {
    return cartId;
  }
}
