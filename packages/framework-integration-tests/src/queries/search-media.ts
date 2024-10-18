import { Booster, Query } from '@boostercloud/framework-core'
import { QueryInfo } from '@boostercloud/framework-types'
import { BookReadModel } from '../read-models/book-read-model'
import { MovieReadModel } from '../read-models/movie-read-model'

export abstract class BaseQuery<M extends BaseQuery<M>> {
  constructor(obj: M) {
    Object.assign(this, obj)
  }
}

export class BookMedia extends BaseQuery<BookMedia> {
  title!: string
  pages!: number
}

export class MovieMedia extends BaseQuery<MovieMedia> {
  title!: string
}

export class Media extends BaseQuery<Media> {
  mediaType!: MediaType
  media!: MediaValue
}

export type MediaValue = BookMedia | MovieMedia

export enum MediaType {
  BookMedia = 'BookMedia',
  MovieMedia = 'MovieMedia',
}

class SearchResult {
  readonly results!: Media[]
  constructor(results: Media[]) {
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
          return new Media({
            mediaType: MediaType.BookMedia,
            media: new BookMedia({ title: media.title, pages: media.pages }),
          })
        } else {
          return new Media({
            mediaType: MediaType.MovieMedia,
            media: new MovieMedia({ title: media.title }),
          })
        }
      }),
    }

    return toReturn
  }
}
