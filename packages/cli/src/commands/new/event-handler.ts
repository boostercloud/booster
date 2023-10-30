import { Flags, Args } from '@oclif/core'
import BaseCommand from '../../common/base-command'
import {
  HasEvent,
  HasName,
  ImportDeclaration,
  joinParsers,
  parseEvent,
  parseName,
} from '../../services/generator/target'
import { Script } from '../../common/script'
import Brand from '../../common/brand'
import { checkCurrentDirIsABoosterProject } from '../../services/project-checker'
import { generate, template } from '../../services/generator'
import * as path from 'path'
import { classNameToFileName } from '../../common/filenames'

export default class EventHandler extends BaseCommand {
  public static description = 'create a new event handler'
  public static flags = {
    help: Flags.help({ char: 'h' }),
    event: Flags.string({
      char: 'e',
      description: 'event that this event handler with handle',
      multiple: false,
    }),
  }

  public static args = {
    eventHandlerName: Args.string(),
  }

  public async run(): Promise<void> {
    const { args, flags } = await this.parse(EventHandler)

    try {
      const event = flags.event
      if (!args.eventHandlerName)
        throw "You haven't provided an event handler name, but it is required, run with --help for usage"
      if (!event) throw "You haven't provided an event, but it is required, run with --help for usage"
      return run(args.eventHandlerName, event)
    } catch (error) {
      console.error(error)
    }
  }
}

type EventHandlerInfo = HasName & HasEvent

const run = async (name: string, eventName: string): Promise<void> =>
  Script.init(`boost ${Brand.energize('new:event-handler')} 🚧`, joinParsers(parseName(name), parseEvent(eventName)))
    .step('Verifying project', checkCurrentDirIsABoosterProject)
    .step('Creating new event handler', generateEventHandler)
    .info('Event handler generated!')
    .done()

function generateImports(info: EventHandlerInfo): Array<ImportDeclaration> {
  const fileName = classNameToFileName(info.event)
  return [
    {
      packagePath: `../events/${fileName}`,
      commaSeparatedComponents: info.event,
    },
    {
      packagePath: '@boostercloud/framework-core',
      commaSeparatedComponents: 'EventHandler',
    },
    {
      packagePath: '@boostercloud/framework-types',
      commaSeparatedComponents: 'Register',
    },
  ]
}

const generateEventHandler = (info: EventHandlerInfo): Promise<void> =>
  generate({
    name: info.name,
    extension: '.ts',
    placementDir: path.join('src', 'event-handlers'),
    template: template('event-handler'),
    info: {
      imports: generateImports(info),
      ...info,
    },
  })
