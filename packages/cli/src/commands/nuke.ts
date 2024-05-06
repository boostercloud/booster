import { Flags } from '@oclif/core'
import BaseCommand from '../common/base-command'
import { nukeCloudProviderResources } from '../services/provider-service'
import { compileProjectAndLoadConfig } from '../services/config-service'
import { BoosterConfig } from '@boostercloud/framework-types'
import { Script } from '../common/script'
import Brand from '../common/brand'
import Prompter from '../services/user-prompt'
import { logger } from '../services/logger'
import { currentEnvironment, initializeEnvironment } from '../services/environment'

const runTasks = async (
  compileAndLoad: Promise<BoosterConfig>,
  nuke: (config: BoosterConfig) => Promise<void>
): Promise<void> =>
  Script.init(`boost ${Brand.dangerize('nuke')} [${currentEnvironment()}] 🧨`, compileAndLoad)
    .step('Removing', (config) => nuke(config))
    .info('Removal complete!')
    .done()

async function askToConfirmRemoval(
  prompter: Prompter,
  force: boolean,
  config: Promise<BoosterConfig>
): Promise<BoosterConfig> {
  if (force) return config
  const configuration = await config
  const appName = await prompter.defaultOrPrompt(
    null,
    'Please, enter the app name to confirm deletion of all resources:'
  )
  if (appName == configuration.appName) {
    return configuration
  } else {
    throw new Error('Wrong app name, stopping nuke!')
  }
}

export default class Nuke extends BaseCommand {
  public static description =
    'Remove all resources used by the current application as configured in your `index.ts` file.'

  public static flags = {
    help: Flags.help({ char: 'h' }),
    environment: Flags.string({
      char: 'e',
      description: 'environment configuration to run',
    }),
    force: Flags.boolean({
      char: 'f',
      description:
        'Run nuke without asking for confirmation. Be EXTRA CAUTIOUS with this option, all your application data will be irreversibly DELETED without confirmation.',
    }),
    verbose: Flags.boolean({
      description: 'display full error messages',
      default: false,
    }),
  }

  public async run(): Promise<void> {
    const { flags } = await this.parse(Nuke)

    if (initializeEnvironment(logger, flags.environment)) {
      await runTasks(
        askToConfirmRemoval(new Prompter(), flags.force, compileProjectAndLoadConfig(process.cwd())),
        nukeCloudProviderResources
      )
    }
  }

  async catch(fullError: Error) {
    const {
      flags: { verbose },
    } = await this.parse(Nuke)

    if (verbose) {
      console.error(fullError.message)
    }

    return super.catch(fullError)
  }
}
