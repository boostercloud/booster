import { Projects, ReadModel } from '@boostercloud/framework-core'
import { ProjectionResult, UUID } from '@boostercloud/framework-types'
import { Book } from '../entities/book'

@ReadModel({
  authorize: 'all',
})
export class BookReadModel {
  public constructor(readonly id: UUID, readonly title: string, readonly pages: number) {}

  @Projects(Book, 'id')
  public static updateBook(book: Book, oldBook?: BookReadModel): ProjectionResult<BookReadModel> {
    // This method calls are here to ensure they work. More info: https://github.com/boostercloud/booster/issues/797
    book.getId()

    return new BookReadModel(book.id, book.title, book.pages)
  }
}
