import { Flags } from '@oclif/core'
import BaseCommand from '../../common/base-command'
import { HasName, HasProjection, joinParsers, parseName, parseProjectionField } from '../../services/generator/target'
import { Script } from '../../common/script'
import Brand from '../../common/brand'
import { checkCurrentDirIsABoosterProject } from '../../services/project-checker'
import { generateProjection, getResourceSourceFile } from '../../services/method-generator'

export default class Projection extends BaseCommand {
  public static description = 'add new projection to read model'

  static usage = 'projection --read-model ReadModel --entity Entity:id'

  static examples = [
    '$ boost add:projection --read-model PostReadModel --entity Post:id',
    '$ boost add:projection --read-model CommentReadModel --entity Comment:id',
  ]

  public static flags = {
    help: Flags.help({ char: 'h' }),
    'read-model': Flags.string({
      description: 'read-model name',
      required: true,
      multiple: false,
      dependsOn: ['entity'],
    }),
    entity: Flags.string({
      description: 'an entity name',
      required: true,
      multiple: false,
      dependsOn: ['read-model'],
    }),
  }

  public async run(): Promise<void> {
    const { flags } = await this.parse(Projection)
    const readModel = flags['read-model']
    const entity = flags.entity

    return run(readModel, entity)
  }
}

type ProjectionInfo = HasName & HasProjection

const run = async (rawReadModel: string, rawProjection: string): Promise<void> =>
  Script.init(
    `boost ${Brand.energize('add:projection')} 🚧`,
    joinParsers(parseName(rawReadModel), parseProjectionField(rawProjection))
  )
    .step('Verifying project', checkCurrentDirIsABoosterProject)
    .step('Generating projection', generateProjectionMethod)
    .info('Projection generated!')
    .done()

async function generateProjectionMethod(info: ProjectionInfo): Promise<void> {
  const readModelSourceFile = getResourceSourceFile(info.name)
  const readModelClass = readModelSourceFile.getClassOrThrow(info.name)

  readModelClass.addMethod(generateProjection(info.name, info.projection))

  return await readModelSourceFile.fixMissingImports().save()
}
