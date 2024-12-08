import { Projects, ReadModel } from '@boostercloud/framework-core'
import { ProjectionResult, UUID } from '@boostercloud/framework-types'
import { Book } from '../entities/book'

@ReadModel({
  authorize: 'all',
})
export class BookReadModel {
  public constructor(readonly id: UUID, readonly title: string, readonly pages: number) {}

  @Projects(Book, 'id')
  public static updateBook(book: Book): ProjectionResult<BookReadModel> {
    return new BookReadModel(book.id, book.title, book.pages)
  }
}
