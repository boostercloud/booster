import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { MovieAdded } from '../events/movie-added'

@Command({
  authorize: 'all',
})
export class AddMovie {
  public constructor(readonly id: UUID, readonly title: string) {}

  public static async handle(command: AddMovie, register: Register): Promise<void> {
    register.events(new MovieAdded(command.id, command.title))
  }
}
