import { Flags } from '@oclif/core'
import BaseCommand from '../common/base-command'
import { cleanProject } from '../services/config-service'
import { checkCurrentDirIsABoosterProject } from '../services/project-checker'
import { Script } from '../common/script'
import Brand from '../common/brand'

const runTasks = async (clean: (ctx: string) => Promise<void>): Promise<void> =>
  Script.init(`boost ${Brand.dangerize('clean')} 🚀`, Promise.resolve(process.cwd()))
    .step('Checking project structure', checkCurrentDirIsABoosterProject)
    .step('Cleaning project', clean)
    .info('Clean complete!')
    .done()

export default class Clean extends BaseCommand {
  public static description = 'Clean the current application as configured in your `index.ts` file.'

  public static flags = {
    help: Flags.help({ char: 'h' }),
    verbose: Flags.boolean({
      description: 'display full error messages',
      default: false,
    }),
  }

  public async run(): Promise<void> {
    await runTasks((ctx: string) => cleanProject(process.cwd()))
  }

  async catch(fullError: Error) {
    const {
      flags: { verbose },
    } = await this.parse(Clean)

    if (verbose) {
      console.error(fullError.message)
    }

    return super.catch(fullError)
  }
}
