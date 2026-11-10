package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.MovieAdded;

/**
 * Adds a movie ({@code src/commands/add-movie.ts}).
 *
 * @param id the movie
 * @param title its title
 */
@Command(authorize = All.class)
public record AddMovie(UUID id, String title) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(AddMovie command, Register register) {
    register.events(new MovieAdded(command.id(), command.title()));
  }
}
