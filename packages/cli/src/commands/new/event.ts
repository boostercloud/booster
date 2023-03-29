import * as Oclif from '@oclif/command'
import { Args, BaseCommand, CliCommand, Flags } from '../../common/base-command'
import Brand from '../../common/brand'
import {
  HasName,
  HasFields,
  joinParsers,
  parseName,
  parseFields,
  ImportDeclaration,
} from '../../services/file-generator/target'
import { FileGenerator } from '../../services/file-generator'
import * as path from 'path'
import { Logger } from '@boostercloud/framework-types'
import { UserProject } from '../../services/user-project'
import { TaskLogger } from '../../services/task-logger'

export default class Event extends BaseCommand<typeof Event> {
  public static description = 'create a new event'
  public static flags = {
    fields: Oclif.flags.string({
      char: 'f',
      description: 'field that this event will contain',
      multiple: true,
    }),
  }

  public static args = [{ name: 'eventName' }]

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

  public async run(flags: Flags<typeof Event>, args: Args<typeof Event>): Promise<void> {
    const fields = flags.fields ?? []
    const eventName = args.eventName
    if (!eventName) {
      throw new Error("You haven't provided an event name, but it is required, run with --help for usage")
    }

    const info = await joinParsers(parseName(eventName), parseFields(fields))

    this.logger.info(`boost ${Brand.energize('new:event')} 🚧`)
    await this.userProject.performChecks()
    await this.taskLogger.logTask('Generating event', () => this.generateEvent(info))
  }

  private async generateEvent(info: HasName & HasFields): Promise<void> {
    const template = await this.fileGenerator.template('event')
    await this.fileGenerator.generate({
      name: info.name,
      extension: '.ts',
      placementDir: path.join('src', 'events'),
      template,
      info: {
        imports: this.generateImports(),
        ...info,
      },
    })
  }

  private generateImports(): Array<ImportDeclaration> {
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
}
