import * as Oclif from '@oclif/command'
import { Script } from '../../common/script'
import Brand from '../../common/brand'
import { generate } from '../../services/generator'
import { HasName, joinParsers, parseName, ImportDeclaration } from '../../services/generator/target'
import * as path from 'path'
import { templates } from '../../templates'
import { 
  checkCurrentDirIsABoosterProject,
  checkCurrentDirBoosterVersion
} from '../../services/project-checker'

export default class ScheduledCommand extends Oclif.Command {
  public static description = "generate new scheduled command, write 'boost new:scheduled-command -h' to see options"
  public static flags = {
    help: Oclif.flags.help({ char: 'h' }),
  }

  public static args = [{ name: 'scheduledCommandName' }]

  public async run(): Promise<void> {
    const { args } = this.parse(ScheduledCommand)
    await checkCurrentDirBoosterVersion(this.config.userAgent)

    try {
      if (!args.scheduledCommandName)
        throw "You haven't provided a scheduled command name, but it is required, run with --help for usage"
      return run(args.scheduledCommandName)
    } catch (error) {
      console.error(error)
    }
  }
}

type ScheduledCommandInfo = HasName

const run = async (name: string): Promise<void> =>
  Script.init(`boost ${Brand.energize('new:scheduled-command')} 🚧`, joinParsers(parseName(name)))
    .step('Verifying project', checkCurrentDirIsABoosterProject)
    .step('Creating new scheduled command', generateScheduledCommand)
    .info('Scheduled command generated!')
    .done()

function generateImports(): Array<ImportDeclaration> {
  const componentsFromBoosterTypes = ['Register']
  return [
    {
      packagePath: '@boostercloud/framework-core',
      commaSeparatedComponents: 'ScheduledCommand',
    },
    {
      packagePath: '@boostercloud/framework-types',
      commaSeparatedComponents: componentsFromBoosterTypes.join(', '),
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
