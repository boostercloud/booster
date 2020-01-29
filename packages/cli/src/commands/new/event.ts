import * as Oclif from '@oclif/command'
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
import { generate } from '../../services/generator'
import * as path from 'path'
import { templates } from '../../templates'

export default class Event extends Oclif.Command {
  public static description = 'create a new event'
  public static flags = {
    help: Oclif.flags.help({ char: 'h' }),
    fields: Oclif.flags.string({
      char: 'f',
      description: 'field that this event will contain',
      multiple: true,
    }),
  }

  public static args = [{ name: 'eventName' }]

  public async run(): Promise<void> {
    return this.runWithErrors().catch(console.error)
  }

  private async runWithErrors(): Promise<void> {
    const { args, flags } = this.parse(Event)
    const fields = flags.fields || []
    if (!args.eventName)
      return Promise.reject("You haven't provided an event name, but it is required, run with --help for usage")
    return run(args.eventName, fields)
  }
}

type EventInfo = HasName & HasFields

const run = async (name: string, rawFields: Array<string>): Promise<void> =>
  Script.init(`boost ${Brand.energize('new:event')} 🚧`, joinParsers(parseName(name), parseFields(rawFields)))
    .step('creating new event', generateEvent)
    .info('Event generated!')
    .done()

function generateImports(): Array<ImportDeclaration> {
  return [
    {
      packagePath: '@boostercloud/framework-core',
      componentNames: ['Event'],
    },
    {
      packagePath: '@boostercloud/framework-types',
      componentNames: ['UUID'],
    },
  ]
}

const generateEvent = (info: EventInfo): Promise<void> =>
  generate({
    name: info.name,
    extension: '.ts',
    placementDir: path.join('src', 'events'),
    template: templates.event,
    info: {
      imports: generateImports(),
      ...info,
    },
  })
