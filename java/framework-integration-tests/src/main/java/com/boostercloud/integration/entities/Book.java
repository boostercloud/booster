package com.boostercloud.integration.entities;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.BookAdded;

/**
 * A book ({@code src/entities/book.ts}).
 *
 * @param id the book
 * @param title its title
 * @param pages its number of pages
 */
@Entity(authorizeReadEvents = All.class)
public record Book(UUID id, String title, double pages) {

  /**
   * @return the book id
   */
  public UUID getId() {
    return id;
  }

  /**
   * @param event the event
   * @param currentBook the current book (ignored)
   * @return the book
   */
  @Reduces(BookAdded.class)
  public static Book bookAdded(BookAdded event, Book currentBook) {
    return new Book(event.id(), event.title(), event.pages());
  }
}
