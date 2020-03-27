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
    .catch('SyntaxError', () => 'Unable to remove project resources. Are you in a booster project?')
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
  }

  public static args = [{ name: 'environment' }]

  public async run(): Promise<void> {
    const { args } = this.parse(Nuke)
    if (!args.environment) {
      console.log('Error: no environment name provided. Usage: `boost nuke <environment>`.')
      return
    }
    process.env.BOOSTER_ENV = args.environment
    await runTasks(
      args.environment,
      askToConfirmRemoval(new Prompter(), compileProjectAndLoadConfig()),
      nukeCloudProviderResources
    )
  }
}
