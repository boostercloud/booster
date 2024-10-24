import { Booster, Query } from '@boostercloud/framework-core'
import { QueryInfo } from '@boostercloud/framework-types'
import { BookReadModel } from '../read-models/book-read-model'
import { MovieReadModel } from '../read-models/movie-read-model'

export class BookMedia {
  title!: string
  pages!: number
  constructor(book: BookMedia) {
    ;(this.title = book.title), (this.pages = book.pages)
  }
}

export class MovieMedia {
  title!: string
  constructor(movie: MovieMedia) {
    this.title = movie.title
  }
}

export type MediaValue = BookMedia | MovieMedia
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
    const toReturn: SearchResult = {
      results: response.map((media) => {
        if (media instanceof BookReadModel) {
          return new BookMedia({ title: media.title, pages: media.pages })
        } else {
          return new MovieMedia({ title: media.title })
        }
      }),
    }

    return toReturn
  }
}
