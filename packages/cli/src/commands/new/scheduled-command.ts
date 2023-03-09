import { Args, BaseCommand, CliCommand, Flags } from '../../common/base-command'
import Brand from '../../common/brand'
import { HasName, joinParsers, parseName, ImportDeclaration } from '../../services/file-generator/target'
import * as path from 'path'
import { Logger } from '@boostercloud/framework-types'
import { FileGenerator } from '../../services/file-generator'
import { UserProject } from '../../services/user-project'
import { TaskLogger } from '../../services/task-logger'

export default class ScheduledCommand extends BaseCommand<typeof ScheduledCommand> {
  public static description = 'generate a new scheduled command'

  public static args = [{ name: 'scheduledCommandName' }]

  implementation = Implementation
}

@CliCommand()
class Implementation {
  constructor(
    readonly logger: Logger,
    readonly userProject: UserProject,
    readonly fileGenerator: FileGenerator,
    readonly taskLogger: TaskLogger
  ) {}

  async run(flags: Flags<typeof ScheduledCommand>, args: Args<typeof ScheduledCommand>): Promise<void> {
    const scheduledCommandName = args.readModelName
    if (!scheduledCommandName) {
      throw new Error("You haven't provided a scheduled command name, but it is required, run with --help for usage")
    }

    const info = await joinParsers(parseName(scheduledCommandName))

    this.logger.info(`boost ${Brand.energize('new:scheduled-command')} 🚧`)
    await this.userProject.performChecks()
    await this.taskLogger.logTask('Generating scheduled command', () => this.generateScheduledCommand(info))
  }

  private async generateScheduledCommand(info: HasName): Promise<void> {
    const template = await this.fileGenerator.template('scheduled-command')
    await this.fileGenerator.generate({
      name: info.name,
      extension: '.ts',
      placementDir: path.join('src', 'scheduled-commands'),
      template,
      info: {
        imports: this.generateImports(),
        ...info,
      },
    })
  }

  private generateImports(): Array<ImportDeclaration> {
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
}
