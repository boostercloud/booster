package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A book was added ({@code src/events/book-added.ts}).
 *
 * @param id the book
 * @param title its title
 * @param pages its number of pages
 */
@Event
public record BookAdded(UUID id, String title, double pages) {
  /**
   * @return the book
   */
  public UUID entityID() {
    return id;
  }
}
