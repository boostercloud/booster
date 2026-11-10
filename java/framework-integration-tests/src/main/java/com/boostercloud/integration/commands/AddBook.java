package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.BookAdded;

/**
 * Adds a book ({@code src/commands/add-book.ts}).
 *
 * @param id the book
 * @param title its title
 * @param pages its number of pages
 */
@Command(authorize = All.class)
public record AddBook(UUID id, String title, double pages) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(AddBook command, Register register) {
    register.events(new BookAdded(command.id(), command.title(), command.pages()));
  }
}
