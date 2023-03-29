import { CliError, cliErrorCatch } from '../../common/errors'
import { Logger, ProviderInfrastructure } from '@boostercloud/framework-types'
import { CloudProvider } from '.'
import { UserProject } from '../user-project'
import { Component } from '../../common/component'

/**
 * Generic cloud provider implementation.
 * Uses the cloud provider that is currently
 * configured in the users project.
 */
@Component({ throws: CliError })
export class GenericCloudProvider implements CloudProvider {
  constructor(readonly logger: Logger, readonly userProject: UserProject) {}

  async catch(e: unknown): Promise<CliError> {
    return cliErrorCatch('CloudProviderError', e)
  }

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

    const method = config.provider.infrastructure()[methodName]
    if (!method) {
      throw new CliError(
        'CloudProviderError',
        `Attempted to perform the '${methodName}' operation with a provider that does not support this feature, please check your environment configuration.`
      )
    }
    return method as NonNullable<ProviderInfrastructure[TMethodName]>
  }
}
