import { flags } from '@oclif/command'
import { Flags, CliCommand, BaseCommand } from '../common/base-command'
import { Logger } from '@boostercloud/framework-types'
import Brand from '../common/brand'
import { UserProject } from 'cli/src/services/user-project'
import { CloudProvider } from 'cli/src/services/cloud-provider'

@CliCommand()
class Implementation {
  constructor(readonly logger: Logger, readonly userProject: UserProject, readonly cloudProvider: CloudProvider) {}

  async run(flags: Flags<typeof Deploy>): Promise<void> {
    this.logger.info('Ensuring environment is properly set')
    if (flags.environment) {
      await this.userProject.overrideEnvironment(flags.environment)
    }
    const currentEnvironment = this.userProject.getEnvironment()
    this.logger.info(`boost ${Brand.dangerize('deploy')} [${currentEnvironment}] 🚀`)
    await this.logger.logProcess('Deploying project', async () => {
      await this.userProject.performChecks()
      await this.userProject.inSandboxRun(this.cloudProvider.deploy)
    })
  }
}

export default class Deploy extends BaseCommand<typeof Deploy> {
  public static description = 'Deploy the current application as configured in your `index.ts` file.'

  public static flags = {
    environment: flags.string({
      char: 'e',
      description: 'environment configuration to run',
    }),
  }

  public async run(): Promise<void> {
    await this.runImplementation(Implementation)
  }
}
