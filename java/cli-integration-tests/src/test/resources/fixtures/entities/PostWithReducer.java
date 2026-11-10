package com.example.cartdemo.entities;

import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.example.cartdemo.events.PostCreated;

@Entity
public record PostWithReducer(
    UUID id,
    String title,
    String body
) {

  @Reduces(PostCreated.class)
  public static PostWithReducer reducePostCreated(PostCreated event, PostWithReducer currentPostWithReducer) {
    return /* NEW PostWithReducer HERE */;
  }
}
