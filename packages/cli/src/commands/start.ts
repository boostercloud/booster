import { Command, flags } from '@oclif/command'
import { startProvider } from '../services/provider-service'
import { compileProjectAndLoadConfig } from '../services/config-service'
import { BoosterConfig } from '@boostercloud/framework-types'
import { Script } from '../common/script'
import Brand from '../common/brand'

const runTasks = async (
  environment: string,
  port: number,
  loader: Promise<BoosterConfig>,
  runner: (config: BoosterConfig) => Promise<void>
): Promise<void> =>
  Script.init(`boost ${Brand.canarize('debug')} [${environment}] 🐛`, loader)
    .step(`Starting debug server on port ${port}`, runner)
    .done()

export default class Start extends Command {
  public static description = 'Start local debug server.'

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
    }),
  }

  public async run(): Promise<void> {
    const { flags } = this.parse(Start)
    if (!flags.environment) {
      console.log('Error: no environment name provided. Usage: `boost start -e <environment>`.')
      return
    }
    process.env.BOOSTER_ENV = flags.environment
    await runTasks(flags.environment, flags.port, compileProjectAndLoadConfig(), startProvider.bind(null, flags.port))
  }
}
