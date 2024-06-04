import { Booster, Command } from '@boostercloud/framework-core'
import { ReadModelInterface, ReadOnlyNonEmptyArray, Register, UUID } from '@boostercloud/framework-types'

@Command({
  authorize: 'all',
})
export class GetConcurrency {
  public constructor(readonly id: UUID, readonly readModelName: string) {}

  public static async handle(
    command: GetConcurrency,
    register: Register
  ): Promise<ReadOnlyNonEmptyArray<ReadModelInterface>> {
    const config = Booster.config
    return await config.provider.readModels.fetch(config, command.readModelName, command.id)
  }
}
