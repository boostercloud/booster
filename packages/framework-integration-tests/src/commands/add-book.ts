import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { BookAdded } from '../events/book-added'

@Command({
  authorize: 'all',
})
export class AddBook {
  public constructor(readonly id: UUID, readonly title: string, readonly pages: number) {}

  public static async handle(command: AddBook, register: Register): Promise<void> {
    register.events(new BookAdded(command.id, command.title, command.pages))
  }
}
