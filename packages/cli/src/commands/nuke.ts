import { Command, flags } from '@oclif/command'
import { nukeCloudProviderResources } from '../services/provider-service'
import { compileProjectAndLoadConfig } from '../services/config-service'
import { checkCurrentDirBoosterVersion } from '../services/project-checker'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { Script } from '../common/script'
import Brand from '../common/brand'
import Prompter from '../services/user-prompt'
import { logger } from '../services/logger'
import { currentEnvironment, initializeEnvironment } from '../services/environment'

const runTasks = async (
  compileAndLoad: Promise<BoosterConfig>,
  nuke: (config: BoosterConfig, logger: Logger) => Promise<void>
): Promise<void> =>
  Script.init(`boost ${Brand.dangerize('nuke')} [${currentEnvironment()}] 🧨`, compileAndLoad)
    .step('Removing', (config) => nuke(config, logger))
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

export default class Nuke extends Command {
  public static description =
    'Remove all resources used by the current application as configured in your `index.ts` file.'

  public static flags = {
    help: flags.help({ char: 'h' }),
    environment: flags.string({
      char: 'e',
      description: 'environment configuration to run',
    }),
    force: flags.boolean({
      char: 'f',
      description:
        'Run nuke without asking for confirmation. Be EXTRA CAUTIOUS with this option, all your application data will be irreversibly DELETED without confirmation.',
    }),
  }

  public async run(): Promise<void> {
    const { flags } = this.parse(Nuke)

    await checkCurrentDirBoosterVersion(logger, this.config.userAgent)
    
    if (initializeEnvironment(logger, flags.environment)) {
      await runTasks(
        askToConfirmRemoval(new Prompter(), flags.force, compileProjectAndLoadConfig(process.cwd())),
        nukeCloudProviderResources
      )
    }
  }
}
