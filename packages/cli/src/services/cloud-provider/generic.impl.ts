import { CliError } from '../../common/errors'
import { ProviderInfrastructure } from '@boostercloud/framework-types'
import { CloudProvider } from '.'
import { UserProject } from '../user-project'

/**
 * Generic cloud provider implementation.
 * Uses the cloud provider that is currently
 * configured in the users project.
 */
export class GenericCloudProvider implements CloudProvider {
  constructor(readonly userProject: UserProject) {}

  async deploy(): Promise<void> {
    const config = await this.userProject.loadConfig()
    const deploy = await this.getMethodOrDie('deploy')
    return deploy(config)
  }

  async nuke(): Promise<void> {
    const config = await this.userProject.loadConfig()
    const nuke = await this.getMethodOrDie('nuke')
    return nuke(config)
  }

  async synth(): Promise<void> {
    const config = await this.userProject.loadConfig()
    const synth = await this.getMethodOrDie('synth')
    return synth(config)
  }

  async start(port: number): Promise<void> {
    const config = await this.userProject.loadConfig()
    const start = await this.getMethodOrDie('start')
    return start(config, port)
  }

  private async getMethodOrDie<TMethodName extends 'deploy' | 'nuke' | 'start' | 'synth'>(
    methodName: TMethodName
  ): Promise<NonNullable<ProviderInfrastructure[TMethodName]>> {
    const config = await this.userProject.loadConfig()
    await this.assertNameIsCorrect(config.appName)

    const method = config.provider.infrastructure()[methodName]
    if (!method) {
      throw new CliError(
        'CloudProviderError',
        `Attempted to perform the '${methodName}' operation with a provider that does not support this feature, please check your environment configuration.`
      )
    }
    return method
  }

  async assertNameIsCorrect(name: string): Promise<void> {
    type StringPredicate = (x: string) => boolean

    // Current characters max length: 37
    // Lambda name limit is 64 characters
    // `-subscriptions-notifier` lambda is 23 characters
    // `-app` prefix is added to application stack
    // which is 64 - 23 - 4 = 37
    const maxProjectNameLength = 37

    const constraints: ReadonlyArray<[StringPredicate, string]> = [
      [(name: string) => name.length > maxProjectNameLength, `be longer than ${maxProjectNameLength} characters`],
      [(name: string) => name.includes(' '), 'contain spaces'],
      [(name: string) => name.toLowerCase() !== name, 'contain uppercase letters'],
      [(name: string) => name.includes('_'), 'contain underscore'],
    ]

    for (const [constraint, restrictionText] of constraints) {
      if (constraint(name)) {
        const message = this.nameFormatErrorMessage(name, restrictionText)
        throw new CliError('CloudProviderError', message)
      }
    }
  }

  private nameFormatErrorMessage(name: string, restrictionText: string): string {
    return `Project name cannot ${restrictionText}:\n\n    Found: '${name}'`
  }
}
