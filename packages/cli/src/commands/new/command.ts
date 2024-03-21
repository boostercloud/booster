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

export default class Command extends BaseCommand {
  public static description = "Generate new resource, write 'boost new' to see options"

  public static flags = {
    help: Flags.help({ char: 'h' }),
    fields: Flags.string({
      char: 'f',
      description: 'Field that this command will contain',
      multiple: true,
    }),
  }

  public static args = {
    commandName: Args.string(),
  }

  public async run(): Promise<void> {
    const { args, flags } = await this.parse(Command)
    try {
      const fields = flags.fields || []
      if (!args.commandName) throw "You haven't provided a command name, but it is required, run with --help for usage"
      return run(args.commandName, fields)
    } catch (error) {
      console.error(error)
    }
  }
}

type CommandInfo = HasName & HasFields

const run = async (name: string, rawFields: Array<string>): Promise<void> =>
  Script.init(`boost ${Brand.energize('new:command')} 🚧`, joinParsers(parseName(name), parseFields(rawFields)))
    .step('Verifying project', checkCurrentDirIsABoosterProject)
    .step('Creating new command', generateCommand)
    .info('Command generated!')
    .done()

function generateImports(info: CommandInfo): Array<ImportDeclaration> {
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

const generateCommand = (info: CommandInfo): Promise<void> =>
  generate({
    name: info.name,
    extension: '.ts',
    placementDir: path.join('src', 'commands'),
    template: template('command'),
    info: {
      imports: generateImports(info),
      ...info,
    },
  })
