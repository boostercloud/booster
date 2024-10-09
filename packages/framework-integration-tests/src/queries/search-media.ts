import { Booster, Query } from '@boostercloud/framework-core'
import { QueryInfo } from '@boostercloud/framework-types'
import { BookReadModel } from '../read-models/book-read-model'
import { MovieReadModel } from '../read-models/movie-read-model'
@Query({
  authorize: 'all',
})
export class SearchMedia {
  public constructor(readonly searchword: string) {}

  public static async handle(query: SearchMedia, queryInfo: QueryInfo): Promise<BookReadModel | MovieReadModel> {
    // const books: Array<BookReadModel> = (await Booster.readModel(BookReadModel)
    // .filter({
    //   title: {
    //     contains: query.searchword,
    //   },
    // })
    // .paginatedVersion(false)
    // .search()) as Array<BookReadModel>

    const [books, movies] = await Promise.all([
      Booster.readModel(BookReadModel)
        .filter({
          title: {
            contains: query.searchword,
          },
        })
        .search(),
      Booster.readModel(MovieReadModel)
        .filter({
          title: {
            contains: query.searchword,
          },
        })
        .search(),
    ])
    return books.concat(movies)[0]
  }
}
