import * as Oclif from '@oclif/command'
import { BaseCommand, CliCommand, Flags } from '../../common/base-command'
import {
  HasName,
  HasProjection,
  joinParsers,
  parseName,
  parseProjectionField,
} from '../../services/file-generator/target'
import Brand from '../../common/brand'
import { generateProjection, getResourceSourceFile } from '../../services/method-generator'
import { Logger } from '@boostercloud/framework-types'
import { UserProject } from '../../services/user-project'
import { TaskLogger } from '../../services/task-logger'

@CliCommand()
class Implementation {
  constructor(readonly logger: Logger, readonly userProject: UserProject, readonly taskLogger: TaskLogger) {}

  async run(flags: Flags<typeof Projection>): Promise<void> {
    const readModelName = flags['read-model']
    const projectionName = flags.entity
    this.logger.info(`boost ${Brand.energize('add:projection')} 🚀`)
    const templateInfo = await joinParsers(parseName(readModelName), parseProjectionField(projectionName))
    await this.userProject.performChecks()
    await this.taskLogger.logTask('Generating projection', () => generateProjectionMethod(templateInfo))
  }
}
export default class Projection extends BaseCommand<typeof Projection> {
  public static description = 'add new projection to a read model class'

  static usage = 'projection --read-model ReadModel --entity Entity:id'

  static examples = [
    '$ boost add:projection --read-model PostReadModel --entity Post:id',
    '$ boost add:projection --read-model CommentReadModel --entity Comment:id',
  ]

  public static flags = {
    'read-model': Oclif.flags.string({
      description: 'read-model name',
      required: true,
      multiple: false,
      dependsOn: ['entity'],
    }),

    entity: Oclif.flags.string({
      description: 'an entity name',
      required: true,
      multiple: false,
      dependsOn: ['read-model'],
    }),
  }

  implementation = Implementation
}

async function generateProjectionMethod(info: HasName & HasProjection): Promise<void> {
  const readModelSourceFile = getResourceSourceFile(info.name)
  const readModelClass = readModelSourceFile.getClassOrThrow(info.name)

  readModelClass.addMethod(generateProjection(info.name, info.projection))

  return await readModelSourceFile.fixMissingImports().save()
}
