import { flags } from '@oclif/command'
import { BaseCommand, CliCommand, Flags } from '../common/base-command'
import { Logger } from '@boostercloud/framework-types'
import Brand from '../common/brand'
import { UserProject } from 'cli/src/services/user-project'
import { CloudProvider } from 'cli/src/services/cloud-provider'

@CliCommand()
class Implementation {
  constructor(readonly logger: Logger, readonly userProject: UserProject, readonly cloudProvider: CloudProvider) {}

  async run(flags: Flags<typeof Start>) {
    const port = flags.port
    this.logger.info('Ensuring environment is properly set')
    await this.userProject.overrideEnvironment(flags.environment)
    const currentEnvironment = this.userProject.getEnvironment()
    this.logger.info(`boost ${Brand.dangerize('start')} [${currentEnvironment}] 🚀`)
    await this.logger.logProcess(`Starting project on port ${flags.port}`, async () => {
      await this.cloudProvider.start(port)
    })
  }
}

export default class Start extends BaseCommand<typeof Start> {
  public static description = 'Start a provider on a specific port'

  public static flags = {
    port: flags.integer({
      char: 'p',
      description: 'port to start the provider on',
      default: 3000,
    }),
    environment: flags.string({
      char: 'e',
      description: 'environment configuration to run',
      required: true,
    }),
  }

  implementation = Implementation
}
