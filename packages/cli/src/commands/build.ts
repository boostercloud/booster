import { Flags } from '@oclif/core'
import BaseCommand from '../common/base-command'
import { compileProject } from '../services/config-service'
import { checkCurrentDirIsABoosterProject } from '../services/project-checker'
import { Script } from '../common/script'
import Brand from '../common/brand'

const runTasks = async (compileAndLoad: (ctx: string) => Promise<void>): Promise<void> =>
  Script.init(`boost ${Brand.dangerize('build')} 🚀`, Promise.resolve(process.cwd()))
    .step('Checking project structure', checkCurrentDirIsABoosterProject)
    .step('Building project', compileAndLoad)
    .info('Build complete!')
    .done()

export default class Build extends BaseCommand {
  public static description = 'Build the current application as configured in your `index.ts` file.'

  public static flags = {
    help: Flags.help({ char: 'h' }),
    verbose: Flags.boolean({
      description: 'display full error messages',
      default: false,
    }),
  }

  public async run(): Promise<void> {
    await runTasks((ctx: string) => compileProject(process.cwd()))
  }

  async catch(fullError: Error) {
    const {
      flags: { verbose },
    } = await this.parse(Build)

    if (verbose) {
      console.error(fullError.message)
    }

    return super.catch(fullError)
  }
}
