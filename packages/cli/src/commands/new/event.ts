import { Flags, Args } from '@oclif/core'
import BaseCommand from '../../common/base-command'
import { Script } from '../../common/script'
import Brand from '../../common/brand'
import {
  HasName,
  HasFields,
  joinParsers,
  parseName,
  parseFields,
  ImportDeclaration,
} from '../../services/generator/target'
import { generate, template } from '../../services/generator'
import * as path from 'path'
import { checkCurrentDirIsABoosterProject } from '../../services/project-checker'

export default class Event extends BaseCommand {
  public static description = 'create a new event'
  public static flags = {
    help: Flags.help({ char: 'h' }),
    fields: Flags.string({
      char: 'f',
      description: 'field that this event will contain',
      multiple: true,
    }),
  }

  public static args = {
    eventName: Args.string(),
  }

  public async run(): Promise<void> {
    const { args, flags } = await this.parse(Event)

    try {
      const fields = flags.fields || []
      if (!args.eventName) throw "You haven't provided an event name, but it is required, run with --help for usage"
      return run(args.eventName, fields)
    } catch (error) {
      console.error(error)
    }
  }
}

type EventInfo = HasName & HasFields

const run = async (name: string, rawFields: Array<string>): Promise<void> =>
  Script.init(`boost ${Brand.energize('new:event')} 🚧`, joinParsers(parseName(name), parseFields(rawFields)))
    .step('Verifying project', checkCurrentDirIsABoosterProject)
    .step('Creating new event', generateEvent)
    .info('Event generated!')
    .done()

function generateImports(): Array<ImportDeclaration> {
  return [
    {
      packagePath: '@boostercloud/framework-core',
      commaSeparatedComponents: 'Event',
    },
    {
      packagePath: '@boostercloud/framework-types',
      commaSeparatedComponents: 'UUID',
    },
  ]
}

const generateEvent = (info: EventInfo): Promise<void> =>
  generate({
    name: info.name,
    extension: '.ts',
    placementDir: path.join('src', 'events'),
    template: template('event'),
    info: {
      imports: generateImports(),
      ...info,
    },
  })
