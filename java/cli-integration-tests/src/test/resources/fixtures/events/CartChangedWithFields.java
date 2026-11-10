package com.example.cartdemo.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

@Event
public record CartChangedWithFields(
    UUID cartId,
    String sku,
    double quantity
) {
  public UUID entityID() {
    return /* the associated entity ID */;
  }
}
