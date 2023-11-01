import { Flags, Args } from '@oclif/core'
import BaseCommand from '../../common/base-command'
import { Script } from '../../common/script'
import Brand from '../../common/brand'
import { HasFields, HasName, joinParsers, parseName, parseFields } from '../../services/generator/target'
import { generate, template } from '../../services/generator'
import * as path from 'path'
import { checkCurrentDirIsABoosterProject } from '../../services/project-checker'

export default class Type extends BaseCommand {
  public static description = 'create a new type'

  public static flags = {
    help: Flags.help({ char: 'h' }),
    fields: Flags.string({
      char: 'f',
      description: 'field that this type will contain',
      multiple: true,
    }),
  }

  public static args = {
    typeName: Args.string(),
  }

  public async run(): Promise<void> {
    const { args, flags } = await this.parse(Type)

    try {
      const fields = flags.fields || []
      if (!args.typeName) throw "You haven't provided a type name, but it is required, run with --help for usage"
      return run(args.typeName, fields)
    } catch (error) {
      console.error(error)
    }
  }
}

type TypeInfo = HasName & HasFields

const run = async (name: string, rawFields: Array<string>): Promise<void> =>
  Script.init(`boost ${Brand.energize('new:type')} 🚧`, joinParsers(parseName(name), parseFields(rawFields)))
    .step('Verifying project', checkCurrentDirIsABoosterProject)
    .step('Creating new type', generateType)
    .info('Type generated!')
    .done()

const generateType = (info: TypeInfo): Promise<void> =>
  generate({
    name: info.name,
    extension: '.ts',
    placementDir: path.join('src', 'common'),
    template: template('type'),
    info: {
      imports: [],
      ...info,
    },
  })
