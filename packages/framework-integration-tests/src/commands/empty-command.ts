import { Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'

@Command({
  authorize: 'all',
})
export class EmptyCommand {
  public constructor() {}

  public static async handle(_command: EmptyCommand, _register: Register): Promise<string> {
    return 'Empty command executed'
  }
}
