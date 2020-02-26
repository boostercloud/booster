import { Command } from '@oclif/command'
import { runLocally } from '../services/provider-service'
import { compileProjectAndLoadConfig } from '../services/config-service'
import { BoosterConfig } from '@boostercloud/framework-types'
import { Script } from '../common/script'
import Brand from '../common/brand'

const runTasks = async (
  loader: Promise<BoosterConfig>,
  runner: (config: BoosterConfig) => Promise<void>
): Promise<void> =>
  Script.init(`boost ${Brand.dangerize('debug')} 🐛`, loader)
    .step('Running debugging server on port 3000', runner)
    .done()

export default class Deploy extends Command {
  public static description = 'Debug the current application locally, as configured in your `index.ts` file.'

  public async run(): Promise<void> {
    await runTasks(compileProjectAndLoadConfig(), runLocally)
  }
}
