import { flags } from '@oclif/command'
import BaseCommand from '../common/base-command'
import { deployToCloudProvider } from '../services/provider-service'
import {
  cleanDeploymentSandbox,
  compileProjectAndLoadConfig,
  createDeploymentSandbox,
} from '../services/config-service'
import { BoosterConfig } from '@boostercloud/framework-types'
import { Script } from '../common/script'
import Brand from '../common/brand'
import { logger } from '../services/logger'
import { currentEnvironment, initializeEnvironment } from '../services/environment'

const runTasks = async (
  compileAndLoad: Promise<BoosterConfig>,
  deployer: (config: BoosterConfig) => Promise<void>
): Promise<void> =>
  Script.init(`boost ${Brand.dangerize('deploy')} [${currentEnvironment()}] 🚀`, compileAndLoad)
    .step('Deploying', (config) => deployer(config))
    .step('Cleaning up deployment files', cleanDeploymentSandbox)
    .info('Deployment complete!')
    .done()

export default class Deploy extends BaseCommand {
  public static description = 'Deploy the current application as configured in your `index.ts` file.'

  public static flags = {
    help: flags.help({ char: 'h' }),
    environment: flags.string({
      char: 'e',
      description: 'environment configuration to run',
    }),
    verbose: flags.boolean({
      description: 'display full error messages',
      default: false,
    }),
  }

  public async run(): Promise<void> {
    const { flags } = this.parse(Deploy)

    if (initializeEnvironment(logger, flags.environment)) {
      console.log('###########################')
      console.log(`# Deploying to ${currentEnvironment()} environment`)
      console.log('###########################')
      const deploymentProjectPath = await createDeploymentSandbox()
      await runTasks(compileProjectAndLoadConfig(deploymentProjectPath), deployToCloudProvider)
    }
  }

  async catch(fullError: Error) {
    const {
      flags: { verbose },
    } = this.parse(Deploy)

    if (verbose) {
      console.error(fullError.message)
    }

    return super.catch(fullError)
  }
}
