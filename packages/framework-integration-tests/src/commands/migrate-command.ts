import { BoosterDataMigrations, Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'

@Command({
  authorize: 'all',
})
export class MigrateCommand {
  public constructor() {}

  public static async handle(_command: MigrateCommand, _register: Register): Promise<void> {
    console.log('migrating')
    await BoosterDataMigrations.run()
    console.log('migrated')
  }
}
