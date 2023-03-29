import { flags } from '@oclif/command'
import { BaseCommand, CliCommand, Flags } from '../common/base-command'
import Brand from '../common/brand'
import { Logger } from '@boostercloud/framework-types'
import { UserProject } from '../services/user-project'
import { CloudProvider } from '../services/cloud-provider'
import { TaskLogger } from '../services/task-logger'

@CliCommand()
class Implementation {
  constructor(
    readonly logger: Logger,
    readonly userProject: UserProject,
    readonly cloudProvider: CloudProvider,
    readonly taskLogger: TaskLogger
  ) {}

  async run(flags: Flags<typeof Synth>): Promise<void> {
    this.logger.info('Ensuring environment is properly set')
    await this.userProject.overrideEnvironment(flags.environment)
    const currentEnvironment = await this.userProject.getEnvironment()
    this.logger.info(`boost ${Brand.dangerize('synth')} [${currentEnvironment}] 🚀`)
    await this.taskLogger.logTask('Synth project', async () => {
      await this.userProject.performChecks()
      await this.userProject.inSandboxRun(this.cloudProvider.synth)
    })
  }
}

export default class Synth extends BaseCommand<typeof Synth> {
  public static description = 'Generate the required cloud templates to deploy your app manually.'

  public static flags = {
    environment: flags.string({
      char: 'e',
      description: 'environment configuration to run',
      required: true,
    }),
  }

  implementation = Implementation
}
