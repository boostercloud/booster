import * as Oclif from '@oclif/command'
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
import { checkItIsABoosterProject } from '../../services/project-checker'
import { generate } from '../../services/generator'
import * as path from 'path'
import { templates } from '../../templates'

export default class EventHandler extends Oclif.Command {
  public static description = 'create a new event handler'
  public static flags = {
    help: Oclif.flags.help({ char: 'h' }),
    event: Oclif.flags.string({
      char: 'e',
      description: 'event that this event handler with handle',
      multiple: false,
    }),
  }

  public static args = [{ name: 'eventHandlerName' }]

  public async run(): Promise<void> {
    const { args, flags } = this.parse(EventHandler)
    try {
      const event = flags.event
      if (!args.eventHandlerName)
        throw new Error("You haven't provided an event handler name, but it is required, run with --help for usage")
      if (!event) throw new Error("You haven't provided an event, but it is required, run with --help for usage")
      return run(args.eventHandlerName, event)
    } catch (error) {
      console.error(error)
    }
  }
}

type EventHandlerInfo = HasName & HasEvent

const run = async (name: string, eventName: string): Promise<void> =>
  Script.init(`boost ${Brand.energize('new:event-handler')} 🚧`, joinParsers(parseName(name), parseEvent(eventName)))
    .step('Verifying project', checkItIsABoosterProject)
    .step('Creating new event handler', generateEventHandler)
    .info('Event handler generated!')
    .done()

function generateImports(info: EventHandlerInfo): Array<ImportDeclaration> {
  return [
    {
      packagePath: `../events/${info.event}`,
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
    template: templates.eventHandler,
    info: {
      imports: generateImports(info),
      ...info,
    },
  })
