import { Command, flags } from '@oclif/command'
import { Observable } from 'rxjs'
import { nukeCloudProviderResources } from '../services/provider-service'
import { compileProjectAndLoadConfig } from '../services/config-service'
import { BoosterConfig } from '@boostercloud/framework-types'
import { Script } from '../common/script'
import Brand from '../common/brand'
import Prompter from '../services/user-prompt'
import { logger } from '../services/logger'

const runTasks = async (
  environment: string,
  loader: Promise<BoosterConfig>,
  nuke: (config: BoosterConfig) => Observable<string>
): Promise<void> =>
  Script.init(`boost ${Brand.dangerize('nuke')} [${environment}] 🧨`, loader)
    .step(
      'Removing',
      (config): Promise<void> =>
        nuke(config).forEach((next): void => {
          logger.info(next)
        })
    )
    .info('Removal complete!')
    .done()

async function askToConfirmRemoval(prompter: Prompter, config: Promise<BoosterConfig>): Promise<BoosterConfig> {
  const configuration = await config
  const appName = await prompter.defaultOrPrompt(
    null,
    'Please, enter the app name to confirm deletion of all resources:'
  )
  if (appName == configuration.appName) {
    return Promise.resolve(configuration)
  } else {
    return Promise.reject(new Error('Wrong app name'))
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
  }

  public async run(): Promise<void> {
    const { flags } = this.parse(Nuke)
    if (!flags.environment) {
      console.log('Error: no environment name provided. Usage: `boost deploy -e <environment>`.')
      return
    }
    process.env.BOOSTER_ENV = flags.environment
    await runTasks(
      flags.environment,
      askToConfirmRemoval(new Prompter(), compileProjectAndLoadConfig()),
      nukeCloudProviderResources
    )
  }
}
