import { flags } from '@oclif/command'
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
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { synthToProvider } from '../services/provider-service'

const runTasks = async (
  compileAndLoad: Promise<BoosterConfig>,
  synther: (config: BoosterConfig, logger: Logger) => Promise<void>
): Promise<void> =>
  Script.init(`boost ${Brand.dangerize('synth')} [${currentEnvironment()}] 🚀`, compileAndLoad)
    .step('Synth', (config) => synther(config, logger))
    .step('Cleaning up temporal files', cleanDeploymentSandbox)
    .info('Synth complete!')
    .done()

export default class Synth extends BaseCommand {
  public static description = 'Synth the current application as configured in your `index.ts` file.'

  public static flags = {
    help: flags.help({ char: 'h' }),
    environment: flags.string({
      char: 'e',
      description: 'environment configuration to run',
    }),
  }

  public async run(): Promise<void> {
    const { flags } = this.parse(Synth)

    if (initializeEnvironment(logger, flags.environment)) {
      const deploymentProjectPath = await createDeploymentSandbox()
      await runTasks(compileProjectAndLoadConfig(deploymentProjectPath), synthToProvider)
    }
  }
}
