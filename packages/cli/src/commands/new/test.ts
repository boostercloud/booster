import BaseCommand from '../../common/base-command'
import { HasName, ImportDeclaration, joinParsers, parseName } from '../../services/generator/target'
import { Script } from '../../common/script'
import Brand from '../../common/brand'
import { checkCurrentDirIsABoosterProject } from '../../services/project-checker'
import { generate } from '../../services/generator'
import { join, resolve } from 'path'
import { templates } from '../../templates'

const projectName = require(join(resolve(process.cwd()), 'package.json')).name

export default class Test extends BaseCommand {
  public static description = 'create a new test'

  public static args = [{ name: 'testName' }]

  public async run(): Promise<void> {
    const { args } = this.parse(Test)

    try {
      if (!args.testName) throw "You haven't provided a test name, but it is required, run with --help for usage"
      return run(args.testName)
    } catch (error) {
      console.error(error)
    }
  }
}

type TestInfo = HasName

const run = async (name: string): Promise<void> =>
  Script.init(`boost ${Brand.energize('new:test')} 🚧`, joinParsers(parseName(name)))
    .step('Verifying project', checkCurrentDirIsABoosterProject)
    .step('Creating new test', generateTest)
    .info('Test generated!')
    .done()

function generateImports(): Array<ImportDeclaration> {
  return [
    {
      packagePath: '@boostercloud/framework-types',
      commaSeparatedComponents: 'BoosterConfig',
    },
    {
      packagePath: 'chai',
      commaSeparatedComponents: 'expect',
    },
  ]
}

const generateTest = (info: TestInfo): Promise<void> =>
  generate({
    name: info.name,
    extension: '.test.ts',
    placementDir: join('test'),
    template: templates.test,
    info: {
      imports: generateImports(),
      projectName,
      ...info,
    },
  })
