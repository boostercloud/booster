import { Flags, Args } from '@oclif/core'
import BaseCommand from '../../common/base-command'
import { Script } from '../../common/script'
import Brand from '../../common/brand'
import {
  HasFields,
  HasReaction,
  HasName,
  joinParsers,
  parseName,
  parseFields,
  parseReaction,
  ImportDeclaration,
} from '../../services/generator/target'
import * as path from 'path'
import { generate, template } from '../../services/generator'
import { checkCurrentDirIsABoosterProject } from '../../services/project-checker'
import { classNameToFileName } from '../../common/filenames'

export default class Entity extends BaseCommand {
  public static description = 'create a new entity'
  public static flags = {
    help: Flags.help({ char: 'h' }),
    fields: Flags.string({
      char: 'f',
      description: 'fields that this entity will contain',
      multiple: true,
    }),
    reduces: Flags.string({
      char: 'r',
      description: 'events that this entity will reduce to build its state',
      multiple: true,
    }),
  }

  public static args = {
    entityName: Args.string(),
  }

  public async run(): Promise<void> {
    const { args, flags } = await this.parse(Entity)

    try {
      const fields = flags.fields || []
      const events = flags.reduces || []
      if (!args.entityName) throw "You haven't provided an entity name, but it is required, run with --help for usage"
      return run(args.entityName, fields, events)
    } catch (error) {
      console.error(error)
    }
  }
}

type EntityInfo = HasName & HasFields & HasReaction

const run = async (name: string, rawFields: Array<string>, rawEvents: Array<string>): Promise<void> =>
  Script.init(
    `boost ${Brand.energize('new:entity')} 🚧`,
    joinParsers(parseName(name), parseFields(rawFields), parseReaction(rawEvents))
  )
    .step('Verifying project', checkCurrentDirIsABoosterProject)
    .step('Creating new entity', generateEntity)
    .info('Entity generated!')
    .done()

function generateImports(info: EntityInfo): Array<ImportDeclaration> {
  const eventsImports: Array<ImportDeclaration> = info.events.map((eventData) => {
    const fileName = classNameToFileName(eventData.eventName)
    return {
      packagePath: `../events/${fileName}`,
      commaSeparatedComponents: eventData.eventName,
    }
  })

  const coreComponents = ['Entity']
  if (info.events.length > 0) {
    coreComponents.push('Reduces')
  }

  return [
    {
      packagePath: '@boostercloud/framework-core',
      commaSeparatedComponents: coreComponents.join(', '),
    },
    {
      packagePath: '@boostercloud/framework-types',
      commaSeparatedComponents: 'UUID',
    },
    ...eventsImports,
  ]
}

const generateEntity = (info: EntityInfo): Promise<void> =>
  generate({
    name: info.name,
    extension: '.ts',
    placementDir: path.join('src', 'entities'),
    template: template('entity'),
    info: {
      imports: generateImports(info),
      ...info,
    },
  })
