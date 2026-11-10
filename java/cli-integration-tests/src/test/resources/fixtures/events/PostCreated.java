package com.example.cartdemo.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

@Event
public record PostCreated(
    UUID postId,
    String title,
    String body
) {
  public UUID entityID() {
    return /* the associated entity ID */;
  }
}
