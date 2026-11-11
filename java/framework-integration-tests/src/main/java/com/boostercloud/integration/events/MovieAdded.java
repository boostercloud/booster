package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A movie was added ({@code src/events/movie-added.ts}).
 *
 * @param id the movie
 * @param title its title
 */
@Event
public record MovieAdded(UUID id, String title) {
  /**
   * @return the movie
   */
  public UUID entityID() {
    return id;
  }
}
