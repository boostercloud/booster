import { Booster, Query } from '@boostercloud/framework-core'
import { QueryInfo } from '@boostercloud/framework-types'
import { BookReadModel } from '../read-models/book-read-model'
import { MovieReadModel } from '../read-models/movie-read-model'

abstract class BaseConstruct<M extends BaseConstruct<M>> {
  constructor(obj: M) {
    Object.assign(this, obj)
  }
}

type Media = BookReadModel | MovieReadModel

class SearchResult extends BaseConstruct<SearchResult> {
  readonly results!: Media[]
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
    console.log(books, movies)
    const response = [...books, ...movies]
    const toReturn = new SearchResult({
      results: response,
    })
    toReturn.results.map((media) => console.log(typeof media))
    return toReturn
  }
}
