import { Flags } from '@oclif/core'
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
    help: Flags.help({ char: 'h' }),
    environment: Flags.string({
      char: 'e',
      description: 'environment configuration to run',
    }),
    verbose: Flags.boolean({
      description: 'display full error messages',
      default: false,
    }),
  }

  public async run(): Promise<void> {
    const { flags } = await this.parse(Deploy)

    if (initializeEnvironment(logger, flags.environment)) {
      const deploymentProjectPath = await createDeploymentSandbox()
      await runTasks(compileProjectAndLoadConfig(deploymentProjectPath), deployToCloudProvider)
    }
  }

  async catch(fullError: Error) {
    const {
      flags: { verbose },
    } = await this.parse(Deploy)

    if (verbose) {
      console.error(fullError.message)
    }

    return super.catch(fullError)
  }
}
