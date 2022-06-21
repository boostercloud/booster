import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { CounterAdded } from '../events/counter-added'

@Command({
  authorize: 'all',
})
export class IncrementCounter {
  public constructor(readonly counterId: UUID, readonly identifier: string) {}

  public static async handle(command: IncrementCounter, register: Register): Promise<void> {
    register.events(new CounterAdded(command.counterId, command.identifier))
  }
}
