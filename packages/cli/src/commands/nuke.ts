import { flags } from '@oclif/command'
import { BaseCommand, CliCommand, Flags } from '../common/base-command'
import { Logger } from '@boostercloud/framework-types'
import Brand from '../common/brand'
import { UserInput } from '../services/user-input'
import { UserProject } from '../services/user-project'
import { CloudProvider } from '../services/cloud-provider'

@CliCommand()
class Implementation {
  constructor(
    readonly logger: Logger,
    readonly userInput: UserInput,
    readonly userProject: UserProject,
    readonly cloudProvider: CloudProvider
  ) {}

  async run(flags: Flags<typeof Nuke>): Promise<void> {
    this.logger.info('Ensuring environment is properly set')
    await this.userProject.overrideEnvironment(flags.environment)
    const currentEnvironment = await this.userProject.getEnvironment()
    this.logger.info(`boost ${Brand.dangerize('nuke')} [${currentEnvironment}] 🧨`)
    const config = await this.userProject.loadConfig()

    if (!flags.force) {
      this.logger.info('This command will remove all the resources used by your application.')
      this.logger.info('You can use the --force flag to skip this confirmation.')
      const appName = await this.userInput.defaultString(
        'Please, enter the app name to confirm deletion of all resources:'
      )
      if (appName != config.appName) {
        throw new Error('Wrong app name, stopping nuke!')
      }
    }

    await this.cloudProvider.nuke()
  }
}

export default class Nuke extends BaseCommand<typeof Nuke> {
  public static description =
    'Remove all resources used by the current application as configured in your `index.ts` file.'

  public static flags = {
    environment: flags.string({
      char: 'e',
      description: 'environment configuration to run',
      required: true,
    }),
    force: flags.boolean({
      char: 'f',
      description:
        'Run nuke without asking for confirmation. Be EXTRA CAUTIOUS with this option, all your application data will be irreversibly DELETED without confirmation.',
    }),
  }

  implementation = Implementation
}
