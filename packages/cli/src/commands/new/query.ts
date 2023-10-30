import { Flags, Args } from '@oclif/core'
import BaseCommand from '../../common/base-command'
import { Script } from '../../common/script'
import Brand from '../../common/brand'
import { generate, template } from '../../services/generator'
import {
  HasName,
  HasFields,
  joinParsers,
  parseName,
  parseFields,
  ImportDeclaration,
} from '../../services/generator/target'
import * as path from 'path'
import { checkCurrentDirIsABoosterProject } from '../../services/project-checker'

export default class Query extends BaseCommand {
  public static description = "generate new query resource, write 'boost new' to see options"
  public static flags = {
    help: Flags.help({ char: 'h' }),
    fields: Flags.string({
      char: 'f',
      description: 'field list that this query will contain',
      multiple: true,
    }),
  }

  public static args = {
    queryName: Args.string(),
  }

  public async run(): Promise<void> {
    const { args, flags } = await this.parse(Query)
    try {
      const fields = flags.fields || []
      if (!args.queryName) throw "You haven't provided a query name, but it is required, run with --help for usage"
      return run(args.queryName, fields)
    } catch (error) {
      console.error(error)
    }
  }
}

type QueryInfo = HasName & HasFields

const run = async (name: string, rawFields: Array<string>): Promise<void> =>
  Script.init(`boost ${Brand.energize('new:query')} 🚧`, joinParsers(parseName(name), parseFields(rawFields)))
    .step('Verifying project', checkCurrentDirIsABoosterProject)
    .step('Creating new query', generateQuery)
    .info('Query generated!')
    .done()

function generateImports(info: QueryInfo): Array<ImportDeclaration> {
  const queryFieldTypes = info.fields.map((f) => f.type)
  const queryUsesUUID = queryFieldTypes.some((type) => type == 'UUID')

  const componentsFromBoosterTypes = ['QueryInfo']
  if (queryUsesUUID) {
    componentsFromBoosterTypes.push('UUID')
  }

  return [
    {
      packagePath: '@boostercloud/framework-core',
      commaSeparatedComponents: 'Query',
    },
    {
      packagePath: '@boostercloud/framework-types',
      commaSeparatedComponents: componentsFromBoosterTypes.join(', '),
    },
  ]
}

const generateQuery = (info: QueryInfo): Promise<void> =>
  generate({
    name: info.name,
    extension: '.ts',
    placementDir: path.join('src', 'queries'),
    template: template('query'),
    info: {
      imports: generateImports(info),
      ...info,
    },
  })
