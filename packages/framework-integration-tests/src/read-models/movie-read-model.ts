import { Projects, ReadModel } from '@boostercloud/framework-core'
import { ProjectionResult, UUID } from '@boostercloud/framework-types'
import { Movie } from '../entities/movie'

@ReadModel({
  authorize: 'all',
})
export class MovieReadModel {
  public constructor(readonly id: UUID, readonly title: string) {}

  @Projects(Movie, 'id')
  public static updateMovie(movie: Movie): ProjectionResult<MovieReadModel> {
    return new MovieReadModel(movie.id, movie.title)
  }
}
