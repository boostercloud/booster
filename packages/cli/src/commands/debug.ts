import { Command, flags } from '@oclif/command'
import { runLocally } from '../services/provider-service'
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

export default class Debug extends Command {
  public static description = 'Debug the current application locally, as configured in your `index.ts` file.'

  public static flags = {
    help: flags.help({ char: 'h' }),
    port: flags.integer({
      char: 'p',
      description: 'port to run the local runtime on',
      default: 3000,
    }),
  }

  public async run(): Promise<void> {
    const { flags } = this.parse(Debug)
    process.env.BOOSTER_ENV = 'development'
    await runTasks(flags.port, compileProjectAndLoadConfig(), (cfg) => runLocally(flags.port, cfg))
  }
}
