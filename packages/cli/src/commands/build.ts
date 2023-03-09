import { BaseCommand, CliCommand } from '../common/base-command'
import Brand from '../common/brand'
import { Logger } from '@boostercloud/framework-types'
import { UserProject } from '../services/user-project'
import { TaskLogger } from '../services/task-logger'

@CliCommand()
class Implementation {
  constructor(readonly logger: Logger, readonly userProject: UserProject, readonly taskLogger: TaskLogger) {}

  async run(): Promise<void> {
    this.logger.info(`boost ${Brand.dangerize('build')} 🚀`)
    await this.taskLogger.logTask('Building project', this.userProject.compile)
    this.logger.info('Build complete!')
  }
}

export default class Build extends BaseCommand<typeof Build> {
  public static description = 'Build the current application as configured in your `index.ts` file.'

  implementation = Implementation
}
