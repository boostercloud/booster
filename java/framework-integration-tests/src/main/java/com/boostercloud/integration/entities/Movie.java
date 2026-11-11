package com.boostercloud.integration.entities;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.MovieAdded;

/**
 * A movie ({@code src/entities/movie.ts}).
 *
 * @param id the movie
 * @param title its title
 */
@Entity(authorizeReadEvents = All.class)
public record Movie(UUID id, String title) {

  /**
   * @return the movie id
   */
  public UUID getId() {
    return id;
  }

  /**
   * @param event the event
   * @param currentMovie the current movie (ignored)
   * @return the movie
   */
  @Reduces(MovieAdded.class)
  public static Movie movieAdded(MovieAdded event, Movie currentMovie) {
    return new Movie(event.id(), event.title());
  }
}
