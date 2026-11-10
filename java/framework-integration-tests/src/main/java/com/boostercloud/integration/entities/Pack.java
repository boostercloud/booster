package com.boostercloud.integration.entities;

import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.PackCreated;
import java.util.List;

/**
 * A pack of products ({@code src/entities/pack.ts}); {@code products} is an array join key of
 * {@code ProductReadModel}. Its events cannot be read through the API.
 *
 * @param id the pack
 * @param name its name
 * @param products the products in it
 */
@Entity
public record Pack(UUID id, String name, List<UUID> products) {

  /**
   * @param event the event
   * @return the pack (the method name is TypeScript's)
   */
  @Reduces(PackCreated.class)
  public static Pack createOrder(PackCreated event) {
    return new Pack(event.packID(), event.name(), event.products());
  }
}
