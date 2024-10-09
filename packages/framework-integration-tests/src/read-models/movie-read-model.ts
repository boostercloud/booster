import { Projects, ReadModel } from '@boostercloud/framework-core'
import { ProjectionResult, UUID } from '@boostercloud/framework-types'
import { Movie } from '../entities/movie'

@ReadModel({
  authorize: 'all',
})
export class MovieReadModel {
  public constructor(readonly id: UUID, readonly title: string) {}

  @Projects(Movie, 'id')
  public static updateMovie(movie: Movie, oldMovie?: MovieReadModel): ProjectionResult<MovieReadModel> {
    // This method calls are here to ensure they work. More info: https://github.com/boostercloud/booster/issues/797
    movie.getId()

    return new MovieReadModel(movie.id, movie.title)
  }
}
