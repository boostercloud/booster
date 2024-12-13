import { Booster, Query } from '@boostercloud/framework-core'
import { QueryInfo } from '@boostercloud/framework-types'
import { BookReadModel } from '../read-models/book-read-model'
import { MovieReadModel } from '../read-models/movie-read-model'

export type MediaValue = BookReadModel | MovieReadModel
class SearchResult {
  readonly results!: MediaValue[]
  constructor(results: MediaValue[]) {
    this.results = results
  }
}
@Query({
  authorize: 'all',
})
export class SearchMedia {
  public constructor(readonly searchword: string) {}

  public static async handle(query: SearchMedia, queryInfo: QueryInfo): Promise<SearchResult> {
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
    const response = [...books, ...movies]

    return {
      results: response,
    }
  }
}
