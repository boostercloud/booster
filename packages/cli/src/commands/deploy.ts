import { Command, flags } from '@oclif/command'
import { Observable } from 'rxjs'
import { deployToCloudProvider } from '../services/provider-service'
import { compileProjectAndLoadConfig } from '../services/config-service'
import { BoosterConfig } from '@boostercloud/framework-types'
import { Script } from '../common/script'
import Brand from '../common/brand'
import { logger } from '../services/logger'

// TODO: Before loading, we should check:
//    * we're in a booster project
//    * run the compiler to be sure that we're deploying the last version and stop the process if it fails
const runTasks = async (
  environment: string,
  loader: Promise<BoosterConfig>,
  deployer: (config: BoosterConfig) => Observable<string>
): Promise<void> =>
  Script.init(`boost ${Brand.dangerize('deploy')} [${environment}] 🚀`, loader)
    //TODO: We should install dependencies in production mode before deploying
    .step(
      'Deploying',
      (config): Promise<void> =>
        deployer(config).forEach((next): void => {
          logger.info(next)
        })
    )
    .info('Deployment complete!')
    .done()

export default class Deploy extends Command {
  public static description = 'Deploy the current application as configured in your `index.ts` file.'

  public static flags = {
    help: flags.help({ char: 'h' }),
  }

  public static args = [{ name: 'environment' }]

  public async run(): Promise<void> {
    const { args } = this.parse(Deploy)
    if (!args.environment) {
      console.log('Error: no environment name provided. Usage: `boost deploy <environment>`.')
      return
    }
    process.env.BOOSTER_ENV = args.environment
    await runTasks(args.environment, compileProjectAndLoadConfig(), deployToCloudProvider)
  }
}
