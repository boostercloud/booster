import * as Oclif from '@oclif/command'
import { Script } from '../../common/script'
import Brand from '../../common/brand'
import { generate } from '../../services/generator'
import { HasName, joinParsers, parseName, parseFields, ImportDeclaration } from '../../services/generator/target'
import * as path from 'path'
import { templates } from '../../templates'
import { checkItIsABoosterProject } from '../../services/project-checker'

export default class ScheduledCommand extends Oclif.Command {
  public static description = "generate new resource, write 'boost new' to see options"
  public static flags = {
    help: Oclif.flags.help({ char: 'h' }),
    fields: Oclif.flags.string({
      char: 'f',
      description: 'field that this command will contain',
      multiple: true,
    }),
  }

  public static args = [{ name: 'commandName' }]

  public async run(): Promise<void> {
    const { args, flags } = this.parse(ScheduledCommand)
    try {
      const fields = flags.fields || []
      if (!args.commandName)
        throw "You haven't provided a scheduled command name, but it is required, run with --help for usage"
      return run(args.commandName, fields)
    } catch (error) {
      console.error(error)
    }
  }
}

type ScheduledCommandInfo = HasName

const run = async (name: string, rawFields: Array<string>): Promise<void> =>
  Script.init(
    `boost ${Brand.energize('new:scheduled-command')} 🚧`,
    joinParsers(parseName(name), parseFields(rawFields))
  )
    .step('Verifying project', checkItIsABoosterProject)
    .step('Creating new scheduled command', generateScheduledCommand)
    .info('Scheduled command generated!')
    .done()

function generateImports(): Array<ImportDeclaration> {
  return [
    {
      packagePath: '@boostercloud/framework-core',
      commaSeparatedComponents: 'ScheduledCommand',
    },
  ]
}

const generateScheduledCommand = (info: ScheduledCommandInfo): Promise<void> =>
  generate({
    name: info.name,
    extension: '.ts',
    placementDir: path.join('src', 'scheduled-commands'),
    template: templates.scheduledCommand,
    info: {
      imports: generateImports(),
      ...info,
    },
  })
