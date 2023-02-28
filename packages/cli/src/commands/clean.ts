import { CliCommand, BaseCommand } from '../common/base-command'
import Brand from '../common/brand'
import { Logger } from '@boostercloud/framework-types'
import { UserProject } from 'cli/src/services/user-project'
import { PackageManager } from 'cli/src/services/package-manager'

@CliCommand()
class Implementation {
  constructor(readonly logger: Logger, readonly packageManager: PackageManager, readonly userProject: UserProject) {}

  async run(): Promise<void> {
    this.logger.info(`boost ${Brand.dangerize('clean')} 🚀`)
    await this.logger.logProcess('Cleaning project', async () => {
      await this.userProject.performChecks()
      await this.packageManager.runScript('clean', [])
    })
    this.logger.info('Clean complete!')
  }
}

export default class Clean extends BaseCommand<typeof Clean> {
  public static description = 'Clean the current application as configured in your `index.ts` file.'

  public async run(): Promise<void> {
    await this.runImplementation(Implementation)
  }
}
