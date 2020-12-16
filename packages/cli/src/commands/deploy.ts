import { Command, flags } from '@oclif/command'
import { deployToCloudProvider } from '../services/provider-service'
import { compileProjectAndLoadConfig } from '../services/config-service'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { Script } from '../common/script'
import Brand from '../common/brand'
import { logger } from '../services/logger'
import { currentEnvironment, initializeEnvironment } from '../services/environment'
import { installAllDependencies } from '../services/dependencies'

const runTasks = async (
  skipRestoreDependencies: boolean,
  compileAndLoad: Promise<BoosterConfig>,
  deployer: (config: BoosterConfig, logger: Logger) => Promise<void>
): Promise<void> =>
  Script.init(`boost ${Brand.dangerize('deploy')} [${currentEnvironment()}] 🚀`, compileAndLoad)
    .step('Deploying', (config) => deployer(config, logger))
    .optionalStep(skipRestoreDependencies, 'Reinstalling dev dependencies', async () => await installAllDependencies())
    .info('Deployment complete!')
    .done()

export default class Deploy extends Command {
  public static description = 'Deploy the current application as configured in your `index.ts` file.'

  public static flags = {
    help: flags.help({ char: 'h' }),
    environment: flags.string({
      char: 'e',
      description: 'environment configuration to run',
    }),
    skipRestoreDependencies: flags.boolean({
      char: 's',
      description: 'skips restoring dependencies after deployment',
    }),
  }

  public async run(): Promise<void> {
    const { flags } = this.parse(Deploy)

    if (initializeEnvironment(logger, flags.environment)) {
      await runTasks(
        flags.skipRestoreDependencies,
        compileProjectAndLoadConfig({ production: true }),
        deployToCloudProvider
      )
    }
  }
}
