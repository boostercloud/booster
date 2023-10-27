import { Flags } from '@oclif/core'
import BaseCommand from '../common/base-command'
import {
  cleanDeploymentSandbox,
  compileProjectAndLoadConfig,
  createDeploymentSandbox,
} from '../services/config-service'
import { Script } from '../common/script'
import Brand from '../common/brand'
import { currentEnvironment, initializeEnvironment } from '../services/environment'
import { logger } from '../services/logger'
import { BoosterConfig } from '@boostercloud/framework-types'
import { synthToProvider } from '../services/provider-service'

const runTasks = async (
  compileAndLoad: Promise<BoosterConfig>,
  synther: (config: BoosterConfig) => Promise<void>
): Promise<void> =>
  Script.init(`boost ${Brand.dangerize('synth')} [${currentEnvironment()}] 🚀`, compileAndLoad)
    .step('Synth', (config) => synther(config))
    .step('Cleaning up temporal files', cleanDeploymentSandbox)
    .info('Synth complete!')
    .done()

export default class Synth extends BaseCommand {
  public static description = 'Generate the required cloud templates to deploy your app manually.'

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
    const { flags } = await this.parse(Synth)

    if (initializeEnvironment(logger, flags.environment)) {
      const deploymentProjectPath = await createDeploymentSandbox()
      await runTasks(compileProjectAndLoadConfig(deploymentProjectPath), synthToProvider)
    }
  }

  async catch(fullError: Error) {
    const {
      flags: { verbose },
    } = await this.parse(Synth)

    if (verbose) {
      console.error(fullError.message)
    }

    return super.catch(fullError)
  }
}
