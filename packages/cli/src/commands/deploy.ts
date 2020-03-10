import { Command } from '@oclif/command'
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
  loader: Promise<BoosterConfig>,
  deployer: (config: BoosterConfig) => Observable<string>
): Promise<void> =>
  Script.init(`boost ${Brand.dangerize('deploy')} 🚀`, loader)
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

  public async run(): Promise<void> {
    await runTasks(compileProjectAndLoadConfig(), deployToCloudProvider)
  }
}
