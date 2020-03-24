import { Command, flags } from '@oclif/command'
import { runProvider } from '../services/provider-service'
import { compileProjectAndLoadConfig } from '../services/config-service'
import { BoosterConfig } from '@boostercloud/framework-types'
import { Script } from '../common/script'
import Brand from '../common/brand'

const runTasks = async (
  port: number,
  loader: Promise<BoosterConfig>,
  runner: (config: BoosterConfig) => Promise<void>
): Promise<void> =>
  Script.init(`boost ${Brand.canarize('debug')} 🐛`, loader)
    .step(`Running debugging server on port ${port}`, runner)
    .done()

export default class Run extends Command {
  public static description = 'Run the current application locally, as configured in your configuration files.'

  public static flags = {
    help: flags.help({ char: 'h' }),
    port: flags.integer({
      char: 'p',
      description: 'port to run the local runtime on',
      default: 3000,
    }),
    environment: flags.string({
      char: 'e',
      description: 'environment configuration to run',
      default: 'development',
    }),
  }

  public async run(): Promise<void> {
    const { flags } = this.parse(Run)
    process.env.BOOSTER_ENV = flags.environment
    await runTasks(flags.port, compileProjectAndLoadConfig(), runProvider.bind(null, flags.port))
  }
}
