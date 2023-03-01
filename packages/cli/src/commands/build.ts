import { BaseCommand, CliCommand } from '../common/base-command'
import Brand from '../common/brand'
import { Logger } from 'framework-types/dist'
import { UserProject } from 'cli/src/services/user-project'

@CliCommand()
class Implementation {
  constructor(readonly logger: Logger, readonly userProject: UserProject) {}

  async run(): Promise<void> {
    this.logger.info(`boost ${Brand.dangerize('build')} 🚀`)
    await this.logger.logProcess('Building project', this.userProject.compile)
    this.logger.info('Build complete!')
  }
}

export default class Build extends BaseCommand<typeof Build> {
  public static description = 'Build the current application as configured in your `index.ts` file.'

  implementation = Implementation
}
