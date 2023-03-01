import * as Oclif from '@oclif/command'
import { Args, BaseCommand, CliCommand, Flags } from '../../common/base-command'
import Brand from '../../common/brand'
import {
  HasFields,
  HasName,
  joinParsers,
  parseName,
  parseFields,
  ImportDeclaration,
  HasProjections,
  parseProjections,
} from '../../services/file-generator/target'
import * as path from 'path'
import { classNameToFileName } from '../../common/filenames'
import { Logger } from 'framework-types/dist'
import { FileGenerator } from 'cli/src/services/file-generator'
import { UserProject } from 'cli/src/services/user-project'

export default class ReadModel extends BaseCommand<typeof ReadModel> {
  public static description = 'create a new read model'
  public static flags = {
    fields: Oclif.flags.string({
      char: 'f',
      description: 'fields that this read model will contain',
      multiple: true,
    }),
    projects: Oclif.flags.string({
      char: 'p',
      description: 'entities that this read model will project to build its state',
      multiple: true,
    }),
  }

  public static args = [{ name: 'readModelName' }]

  implementation = Implementation
}

@CliCommand()
class Implementation {
  constructor(readonly logger: Logger, readonly userProject: UserProject, readonly fileGenerator: FileGenerator) {}

  public async run(flags: Flags<typeof ReadModel>, args: Args<typeof ReadModel>): Promise<void> {
    const fields = flags.fields ?? []
    const projections = flags.projects ?? []
    const readModelName = args.readModelName
    if (!readModelName) {
      throw new Error("You haven't provided a read model name, but it is required, run with --help for usage")
    }

    const info = await joinParsers(parseName(readModelName), parseFields(fields), parseProjections(projections))

    this.logger.info(`boost ${Brand.energize('new:read-model')} 🚧`)
    await this.userProject.performChecks()
    await this.logger.logProcess('Generating read model', () => this.generateReadModel(info))
  }

  private async generateReadModel(info: HasName & HasFields & HasProjections): Promise<void> {
    const template = await this.fileGenerator.template('read-model')
    await this.fileGenerator.generate({
      name: info.name,
      extension: '.ts',
      placementDir: path.join('src', 'read-models'),
      template,
      info: {
        imports: this.generateImports(info),
        ...info,
      },
    })
  }

  private generateImports(info: HasName & HasFields & HasProjections): Array<ImportDeclaration> {
    const eventsImports: Array<ImportDeclaration> = info.projections.map((projection) => {
      const fileName = classNameToFileName(projection.entityName)
      return {
        packagePath: `../entities/${fileName}`,
        commaSeparatedComponents: projection.entityName,
      }
    })

    const coreComponents = ['ReadModel']
    if (info.projections.length > 0) {
      coreComponents.push('Projects')
    }

    return [
      {
        packagePath: '@boostercloud/framework-core',
        commaSeparatedComponents: coreComponents.join(', '),
      },
      {
        packagePath: '@boostercloud/framework-types',
        commaSeparatedComponents: info.projections.length > 0 ? 'UUID, ProjectionResult' : 'UUID',
      },
      ...eventsImports,
    ]
  }
}
