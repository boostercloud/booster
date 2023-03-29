import * as Oclif from '@oclif/command'
import { Args, BaseCommand, CliCommand, Flags } from '../../common/base-command'
import {
  HasEvent,
  HasName,
  ImportDeclaration,
  joinParsers,
  parseEvent,
  parseName,
} from '../../services/file-generator/target'
import Brand from '../../common/brand'
import * as path from 'path'
import { classNameToFileName } from '../../common/filenames'
import { Logger } from '@boostercloud/framework-types'
import { FileGenerator } from '../../services/file-generator'
import { UserProject } from '../../services/user-project'
import { TaskLogger } from '../../services/task-logger'

export default class EventHandler extends BaseCommand<typeof EventHandler> {
  public static description = 'create a new event handler'
  public static flags = {
    event: Oclif.flags.string({
      char: 'e',
      description: 'event that this event handler with handle',
      multiple: false,
      required: true,
    }),
  }

  public static args = [{ name: 'eventHandlerName' }]

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

  async run(flags: Flags<typeof EventHandler>, args: Args<typeof EventHandler>): Promise<void> {
    const eventHandlerName = args.eventHandlerName
    if (!eventHandlerName) {
      throw new Error("You haven't provided a event handler name, but it is required, run with --help for usage")
    }

    const info = await joinParsers(parseName(eventHandlerName), parseEvent(flags.event))

    this.logger.info(`boost ${Brand.energize('new:event-handler')} 🚧`)
    await this.userProject.performChecks()
    await this.taskLogger.logTask('Generating event handler', () => this.generateEventHandler(info))
  }

  private async generateEventHandler(info: HasName & HasEvent): Promise<void> {
    const template = await this.fileGenerator.template('event-handler')
    await this.fileGenerator.generate({
      name: info.name,
      extension: '.ts',
      placementDir: path.join('src', 'event-handlers'),
      template,
      info: {
        imports: this.generateImports(info),
        ...info,
      },
    })
  }

  private generateImports(info: HasName & HasEvent): Array<ImportDeclaration> {
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
}
