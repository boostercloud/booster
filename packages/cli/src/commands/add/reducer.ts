import { Flags } from '@oclif/core'
import BaseCommand from '../../common/base-command'
import { HasName, HasReaction, joinParsers, parseName, parseReaction } from '../../services/generator/target'
import { Script } from '../../common/script'
import Brand from '../../common/brand'
import { checkCurrentDirIsABoosterProject } from '../../services/project-checker'
import { generateReducers, getResourceSourceFile } from '../../services/method-generator'

export default class Reducer extends BaseCommand {
  public static description = 'add new reducer to entity'

  static usage = 'reducer --entity Entity --event Event'

  static examples = [
    '$ boost add:reducer --entity Post --event PostCreated',
    '$ boost add:reducer --entity Comment --event CommentUpdated CommentVoted',
  ]

  public static flags = {
    help: Flags.help({ char: 'h' }),
    entity: Flags.string({
      description: 'an entity name',
      required: true,
      multiple: false,
      dependsOn: ['event'],
    }),
    event: Flags.string({
      description: 'an event name',
      required: true,
      multiple: true,
      dependsOn: ['entity'],
    }),
  }

  public async run(): Promise<void> {
    const { flags } = await this.parse(Reducer)
    const entity = flags.entity
    const events = flags.event

    return run(entity, events)
  }
}

type ReducerInfo = HasName & HasReaction

/* eslint-disable @typescript-eslint/no-extra-parens */
const pluralize = (word: string, count: number): string => (count === 1 ? word : `${word}s`)

const run = async (rawEntity: string, rawEvents: string[]): Promise<void> =>
  Script.init(`boost ${Brand.energize('add:reducer')} 🚧`, joinParsers(parseName(rawEntity), parseReaction(rawEvents)))
    .step('Verifying project', checkCurrentDirIsABoosterProject)
    .step(`Generating ${pluralize('reducer', rawEvents.length)}`, generateReducerMethods)
    .info(`${pluralize('Reducer', rawEvents.length)} generated!`)
    .done()

async function generateReducerMethods(info: ReducerInfo): Promise<void> {
  const entitySourceFile = getResourceSourceFile(info.name)
  const entityClass = entitySourceFile.getClassOrThrow(info.name)

  entityClass.addMethods(generateReducers(info.name, info.events))

  return await entitySourceFile.fixMissingImports().save()
}
