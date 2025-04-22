import { Booster, Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { ReadModelSchemaMigrator } from '@boostercloud/framework-core/dist/read-model-schema-migrator'

@Command({
  authorize: 'all',
})
export class MigrateAllReadModel {
  public constructor(readonly readModelName: string) {}

  public static async handle(command: MigrateAllReadModel, register: Register): Promise<string> {
    const readModelSchemaMigrator = new ReadModelSchemaMigrator(Booster.config)
    const result = await readModelSchemaMigrator.migrateAll(command.readModelName)
    return `Migrated ${result} ${command.readModelName}`
  }
}
