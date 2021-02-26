import * as Oclif from '@oclif/command'
import { Script } from '../../common/script'
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
} from '../../services/generator/target'
import * as path from 'path'
import { generate } from '../../services/generator'
import { templates } from '../../templates'
import { 
  checkCurrentDirIsABoosterProject,
  checkCurrentDirBoosterVersion
} from '../../services/project-checker'
import { classNameToFileName } from '../../common/filenames'

export default class ReadModel extends Oclif.Command {
  public static description = 'create a new read model'
  public static flags = {
    help: Oclif.flags.help({ char: 'h' }),
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

  public async run(): Promise<void> {
    const { args, flags } = this.parse(ReadModel)
    await checkCurrentDirBoosterVersion(this.config.userAgent)

    try {
      const fields = flags.fields ?? []
      const projections = flags.projects ?? []
      if (!args.readModelName)
        throw "You haven't provided a read model name, but it is required, run with --help for usage"
      return run(args.readModelName, fields, projections)
    } catch (error) {
      console.error(error)
    }
  }
}

type ReadModelInfo = HasName & HasFields & HasProjections

const run = async (name: string, rawFields: Array<string>, rawProjections: Array<string>): Promise<void> =>
  Script.init(
    `boost ${Brand.energize('new:read-model')} 🚧`,
    joinParsers(parseName(name), parseFields(rawFields), parseProjections(rawProjections))
  )
    .step('Verifying project', checkCurrentDirIsABoosterProject)
    .step('Creating new read model', generateReadModel)
    .info('Read model generated!')
    .done()

function generateImports(info: ReadModelInfo): Array<ImportDeclaration> {
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

const generateReadModel = (info: ReadModelInfo): Promise<void> =>
  generate({
    name: info.name,
    extension: '.ts',
    placementDir: path.join('src', 'read-models'),
    template: templates.readModel,
    info: {
      imports: generateImports(info),
      ...info,
    },
  })
