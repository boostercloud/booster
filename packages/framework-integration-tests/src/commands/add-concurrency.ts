import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { ConcurrencyPersisted } from '../events/concurrency-persisted'

export interface ProjectionDetails {
  methodName: string
  joinKey: keyof AddConcurrency
}

@Command({
  authorize: 'all',
})
export class AddConcurrency {
  public constructor(readonly id: UUID, readonly otherId: UUID) {}

  public static async handle(command: AddConcurrency, register: Register): Promise<void> {
    register.events(new ConcurrencyPersisted(command.id, command.otherId))
  }
}
