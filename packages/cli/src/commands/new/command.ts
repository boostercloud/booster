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
import * as path from 'path'
import { Logger } from '@boostercloud/framework-types'
import { UserProject } from '../../services/user-project'
import { FileGenerator } from '../../services/file-generator'

export default class Command extends BaseCommand<typeof Command> {
  public static description = "generate new resource, write 'boost new' to see options"
  public static flags = {
    fields: Oclif.flags.string({
      char: 'f',
      description: 'field that this command will contain',
      multiple: true,
    }),
  }

  public static args = [{ name: 'commandName' }]

  implementation = Implementation
}

@CliCommand()
class Implementation {
  constructor(readonly logger: Logger, readonly userProject: UserProject, readonly fileGenerator: FileGenerator) {}

  public async run(flags: Flags<typeof Command>, args: Args<typeof Command>): Promise<void> {
    const fields = flags.fields ?? []
    const commandName = args.commandName
    if (!commandName) {
      throw new Error("You haven't provided a command name, but it is required, run with --help for usage")
    }

    const info = await joinParsers(parseName(commandName), parseFields(fields))

    this.logger.info(`boost ${Brand.energize('new:command')} 🚧`)
    await this.userProject.performChecks()
    await this.logger.logProcess('Generating command', () => this.generateCommand(info))
  }

  private async generateCommand(info: HasName & HasFields): Promise<void> {
    const template = await this.fileGenerator.template('command')
    await this.fileGenerator.generate({
      name: info.name,
      extension: '.ts',
      placementDir: path.join('src', 'commands'),
      template,
      info: {
        imports: this.generateImports(info),
        ...info,
      },
    })
  }

  private generateImports(info: HasName & HasFields): Array<ImportDeclaration> {
    const commandFieldTypes = info.fields.map((f) => f.type)
    const commandUsesUUID = commandFieldTypes.some((type) => type == 'UUID')

    const componentsFromBoosterTypes = ['Register']
    if (commandUsesUUID) {
      componentsFromBoosterTypes.push('UUID')
    }

    return [
      {
        packagePath: '@boostercloud/framework-core',
        commaSeparatedComponents: 'Command',
      },
      {
        packagePath: '@boostercloud/framework-types',
        commaSeparatedComponents: componentsFromBoosterTypes.join(', '),
      },
    ]
  }
}
