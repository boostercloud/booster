import { Flags } from '@oclif/core'
import BaseCommand from '../common/base-command'
import { startProvider } from '../services/provider-service'
import { compileProjectAndLoadConfig } from '../services/config-service'
import { BOOSTER_LOCAL_PORT, BoosterConfig } from '@boostercloud/framework-types'
import { Script } from '../common/script'
import Brand from '../common/brand'
import { logger } from '../services/logger'
import { currentEnvironment, initializeEnvironment } from '../services/environment'
import * as process from 'process'

const runTasks = async (
  port: number,
  loader: Promise<BoosterConfig>,
  runner: (config: BoosterConfig) => Promise<void>
): Promise<void> =>
  Script.init(`boost ${Brand.canarize('debug')} [${currentEnvironment()}] 🐛`, loader)
    .step(`Starting debug server on port ${port}`, runner)
    .done()

export default class Start extends BaseCommand {
  public static description = 'Start local debug server.'

  public static flags = {
    help: Flags.help({ char: 'h' }),
    port: Flags.integer({
      char: 'p',
      description: 'port to run the local runtime on',
      default: 3000,
    }),
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
    const { flags } = await this.parse(Start)

    if (initializeEnvironment(logger, flags.environment)) {
      process.env[BOOSTER_LOCAL_PORT] = flags.port ? flags.port.toString() : '3000'
      await runTasks(flags.port, compileProjectAndLoadConfig(process.cwd()), startProvider.bind(null, flags.port))
    }
  }

  async catch(fullError: Error) {
    const {
      flags: { verbose },
    } = await this.parse(Start)

    if (verbose) {
      console.error(fullError.message)
    }

    return super.catch(fullError)
  }
}
