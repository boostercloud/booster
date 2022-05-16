import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { AnotherCounterAdded } from '../events/another-counter-added'

@Command({
  authorize: 'all',
})
export class IncrementAnotherCounter {
  public constructor(readonly anotherCounterId: UUID, readonly identifier: string) {}

  public static async handle(command: IncrementAnotherCounter, register: Register): Promise<void> {
    register.events(new AnotherCounterAdded(command.anotherCounterId, command.identifier))
  }
}
