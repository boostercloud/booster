package com.boostercloud.integration.readmodels;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.entities.Book;

/**
 * The public view of a book ({@code src/read-models/book-read-model.ts}).
 *
 * @param id the book
 * @param title its title
 * @param pages its number of pages
 */
@ReadModel(authorize = All.class)
public record BookReadModel(UUID id, String title, double pages) implements MediaValue {

  /**
   * @param book the book
   * @return the read model
   */
  @Projects(entity = Book.class, joinKey = "id")
  public static ProjectionResult<BookReadModel> updateBook(Book book) {
    return ProjectionResult.of(new BookReadModel(book.id(), book.title(), book.pages()));
  }
}
