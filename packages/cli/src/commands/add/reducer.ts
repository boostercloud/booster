import * as Oclif from '@oclif/command'
import { BaseCommand, CliCommand, Flags } from '../../common/base-command'
import { HasName, HasReaction, joinParsers, parseName, parseReaction } from '../../services/file-generator/target'
import Brand from '../../common/brand'
import { generateReducers, getResourceSourceFile } from '../../services/method-generator'
import { UserProject } from '../../services/user-project'
import { Logger } from '@boostercloud/framework-types'

@CliCommand()
class Implementation {
  constructor(readonly logger: Logger, readonly userProject: UserProject) {}

  async run(flags: Flags<typeof Reducer>): Promise<void> {
    const entity = flags.entity
    const events = flags.event

    this.logger.info(`boost ${Brand.energize('add:reducer')} 🚀`)
    const templateInfo = await joinParsers(parseName(entity), parseReaction(events))

    const reducerWord = events.length > 1 ? 'reducers' : 'reducer'
    await this.logger.logProcess(`Generating ${reducerWord}`, () => generateReducerMethods(templateInfo))
  }
}
export default class Reducer extends BaseCommand<typeof Reducer> {
  public static description = 'add new reducer to entity'

  static usage = 'reducer --entity Entity --event Event'

  static examples = [
    '$ boost add:reducer --entity Post --event PostCreated',
    '$ boost add:reducer --entity Comment --event CommentUpdated CommentVoted',
  ]

  public static flags = {
    entity: Oclif.flags.string({
      description: 'an entity name',
      required: true,
      multiple: false,
      dependsOn: ['event'],
    }),
    event: Oclif.flags.string({
      description: 'an event name',
      required: true,
      multiple: true,
      dependsOn: ['entity'],
    }),
  }

  implementation = Implementation
}

type ReducerInfo = HasName & HasReaction

async function generateReducerMethods(info: ReducerInfo): Promise<void> {
  const entitySourceFile = getResourceSourceFile(info.name)
  const entityClass = entitySourceFile.getClassOrThrow(info.name)

  entityClass.addMethods(generateReducers(info.name, info.events))

  return await entitySourceFile.fixMissingImports().save()
}
