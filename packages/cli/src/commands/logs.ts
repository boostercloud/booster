import { flags } from '@oclif/command'
import BaseCommand from '../common/base-command'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { Script } from '../common/script'
import Brand from '../common/brand'
import { logger } from '../services/logger'
import { currentEnvironment, initializeEnvironment } from '../services/environment'
import { fetchLogsProvider } from '../services/provider-service'
import { compileProjectAndLoadConfig } from '../services/config-service'

const runTasks = async (
  compileAndLoad: Promise<BoosterConfig>,
  fetchLogsProvider: (config: BoosterConfig, logger: Logger) => Promise<void>
): Promise<void> =>
  Script.init(`boost ${Brand.dangerize('logs')} [${currentEnvironment()}] 🚀`, compileAndLoad)
    .step('Getting logs...', (config: BoosterConfig) => fetchLogsProvider(config, logger))
    .info('Finished fetching logs!')
    .done()

export default class Logs extends BaseCommand {
  public static description = 'Deploy the current application as configured in your `index.ts` file.'

  public static flags = {
    help: flags.help({ char: 'h' }),
    environment: flags.string({
      char: 'e',
      description: 'environment configuration to run',
    }),
  }

  public async run(): Promise<void> {
    const { flags } = this.parse(Logs)

    if (initializeEnvironment(logger, flags.environment)) {
      await runTasks(compileProjectAndLoadConfig(process.cwd()), fetchLogsProvider)
    }
  }
}
