import * as Oclif from '@oclif/command'
import { Script } from '../../common/script'
import Brand from '../../common/brand'
import { HasFields, HasName, joinParsers, parseName, parseFields } from '../../services/generator/target'
import { templates } from '../../templates'
import { generate } from '../../services/generator'
import * as path from 'path'

export default class Type extends Oclif.Command {
  public static description = 'create a new type'
  public static flags = {
    help: Oclif.flags.help({ char: 'h' }),
    fields: Oclif.flags.string({
      char: 'f',
      description: 'field that this type will contain',
      multiple: true,
    }),
  }

  public static args = [{ name: 'typeName' }]

  public async run(): Promise<void> {
    return this.runWithErrors().catch(console.error)
  }

  private async runWithErrors(): Promise<void> {
    const { args, flags } = this.parse(Type)
    const fields = flags.fields || []
    if (!args.typeName)
      return Promise.reject("You haven't provided a type name, but it is required, run with --help for usage")
    return run(args.typeName, fields)
  }
}

type TypeInfo = HasName & HasFields

const run = async (name: string, rawFields: Array<string>): Promise<void> =>
  Script.init(`boost ${Brand.energize('new:type')} 🚧`, joinParsers(parseName(name), parseFields(rawFields)))
    .step('creating new type', generateType)
    .info('Type generated!')
    .done()

const generateType = (info: TypeInfo): Promise<void> =>
  generate({
    name: info.name,
    extension: '.ts',
    placementDir: path.join('src', 'common'),
    template: templates.type,
    info,
  })
