import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { MovieAdded } from '../events/movie-added'

@Entity({
  authorizeReadEvents: 'all',
})
export class Movie {
  public constructor(readonly id: UUID, readonly title: string) {}

  public getId(): UUID {
    return this.id
  }

  @Reduces(MovieAdded)
  public static movieAdded(event: MovieAdded, currentMovie: Movie): Movie {
    return new Movie(event.id, event.title)
  }
}
