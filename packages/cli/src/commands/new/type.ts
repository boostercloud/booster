import * as Oclif from '@oclif/command'
import { Args, BaseCommand, CliCommand, Flags } from '../../common/base-command'
import Brand from '../../common/brand'
import { HasFields, HasName, joinParsers, parseName, parseFields } from '../../services/file-generator/target'
import { FileGenerator } from '../../services/file-generator'
import * as path from 'path'
import { Logger } from '@boostercloud/framework-types'
import { UserProject } from '../../services/user-project'
import { TaskLogger } from '../../services/task-logger'

export default class Type extends BaseCommand<typeof Type> {
  public static description = 'create a new type'

  public static flags = {
    fields: Oclif.flags.string({
      char: 'f',
      description: 'field that this type will contain',
      multiple: true,
    }),
  }

  public static args = [{ name: 'typeName' }]

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

  public async run(flags: Flags<typeof Type>, args: Args<typeof Type>): Promise<void> {
    const fields = flags.fields ?? []
    const typeName = args.typeName
    if (!typeName) {
      throw new Error("You haven't provided a type name, but it is required, run with --help for usage")
    }

    const info = await joinParsers(parseName(typeName), parseFields(fields))

    this.logger.info(`boost ${Brand.energize('new:type')} 🚧`)
    await this.userProject.performChecks()
    await this.taskLogger.logTask('Generating type', () => this.generateType(info))
  }

  private async generateType(info: HasName & HasFields): Promise<void> {
    const template = await this.fileGenerator.template('type')
    await this.fileGenerator.generate({
      name: info.name,
      extension: '.ts',
      placementDir: path.join('src', 'common'),
      template,
      info: {
        imports: [],
        ...info,
      },
    })
  }
}
