import * as Oclif from '@oclif/command'
import { Args, BaseCommand, CliCommand, Flags } from '../../common/base-command'
import Brand from '../../common/brand'
import {
  HasFields,
  HasReaction,
  HasName,
  joinParsers,
  parseName,
  parseFields,
  parseReaction,
  ImportDeclaration,
} from '../../services/file-generator/target'
import * as path from 'path'
import { classNameToFileName } from '../../common/filenames'
import { FileGenerator } from '../../services/file-generator'
import { UserProject } from '../../services/user-project'
import { Logger } from '@boostercloud/framework-types'

export default class Entity extends BaseCommand<typeof Entity> {
  public static description = 'create a new entity'
  public static flags = {
    fields: Oclif.flags.string({
      char: 'f',
      description: 'fields that this entity will contain',
      multiple: true,
    }),
    reduces: Oclif.flags.string({
      char: 'r',
      description: 'events that this entity will reduce to build its state',
      multiple: true,
    }),
  }

  public static args = [{ name: 'entityName' }]

  implementation = Implementation
}

@CliCommand()
class Implementation {
  constructor(readonly logger: Logger, readonly userProject: UserProject, readonly fileGenerator: FileGenerator) {}

  async run(flags: Flags<typeof Entity>, args: Args<typeof Entity>): Promise<void> {
    const fields = flags.fields ?? []
    const events = flags.reduces ?? []
    const entityName = args.commandName
    if (!entityName) {
      throw new Error("You haven't provided a entity name, but it is required, run with --help for usage")
    }

    const info = await joinParsers(parseName(entityName), parseFields(fields), parseReaction(events))

    this.logger.info(`boost ${Brand.energize('new:entity')} 🚧`)
    await this.userProject.performChecks()
    await this.logger.logProcess('Generating entity', () => this.generateEntity(info))
  }

  private async generateEntity(info: HasName & HasFields & HasReaction): Promise<void> {
    const template = await this.fileGenerator.template('entity')
    await this.fileGenerator.generate({
      name: info.name,
      extension: '.ts',
      placementDir: path.join('src', 'entities'),
      template,
      info: {
        imports: this.generateImports(info),
        ...info,
      },
    })
  }

  private generateImports(info: HasName & HasFields & HasReaction): Array<ImportDeclaration> {
    const eventsImports: Array<ImportDeclaration> = info.events.map((eventData) => {
      const fileName = classNameToFileName(eventData.eventName)
      return {
        packagePath: `../events/${fileName}`,
        commaSeparatedComponents: eventData.eventName,
      }
    })

    const coreComponents = ['Entity']
    if (info.events.length > 0) {
      coreComponents.push('Reduces')
    }

    return [
      {
        packagePath: '@boostercloud/framework-core',
        commaSeparatedComponents: coreComponents.join(', '),
      },
      {
        packagePath: '@boostercloud/framework-types',
        commaSeparatedComponents: 'UUID',
      },
      ...eventsImports,
    ]
  }
}
