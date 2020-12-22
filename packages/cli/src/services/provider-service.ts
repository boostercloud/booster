import { BoosterConfig, Logger, ProviderInfrastructure } from '@boostercloud/framework-types'
import { dynamicLoad } from './dynamic-loader'

export function assertNameIsCorrect(name: string): void {
  // Current characters max length: 37
  // Lambda name limit is 64 characters
  // `-subscriptions-notifier` lambda is 23 characters
  // `-app` prefix is added to application stack
  // which is 64 - 23 - 4 = 37
  const maxProjectNameLength = 37
  if (name.length > maxProjectNameLength)
    throw new Error(`Project name cannot be longer than ${maxProjectNameLength} chars long:

    Found: '${name}'`)

  if (name.includes(' '))
    throw new Error(`Project name cannot contain spaces:

    Found: '${name}'`)

  if (name.toLowerCase() != name)
    throw new Error(`Project name cannot contain uppercase letters:

    Found: '${name}'`)
}

/* We load the infrastructure package dynamically here as a cli plugin to avoid
 * including it among the dependences that are deployed in the lambda functions.
 * The infrastructure package is only used during the deploy.
 */
function loadInfrastructurePackageWithRockets(config: BoosterConfig): ProviderInfrastructure {
  const packageDescription = config.provider.packageDescription()
  // TODO: Check that the package is installed and that the version matches
  // try {
  //   getInstalledPathSync(infrastructurePackageName)
  // } catch (e) {
  //   throw new Error(
  //     `The AWS infrastructure package must be installed to perform this operation, please install it globally running 'npm install -g ${infrastructurePackageName}'`
  //   )
  // }
  return dynamicLoad(packageDescription.name + '-infrastructure').Infrastructure(config.rockets)
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function supportedInfrastructureMethodOrDie(methodName: 'deploy' | 'nuke' | 'start', config: BoosterConfig): any {
  assertNameIsCorrect(config.appName)
  const infrastructure = loadInfrastructurePackageWithRockets(config)
  const method = infrastructure[methodName]
  if (!method) {
    throw new Error(
      `Attempted to perform the '${methodName}' operation with a provider that does not support this feature, please check your environment configuration.`
    )
  }
  return method
}

export async function deployToCloudProvider(config: BoosterConfig, logger: Logger): Promise<void> {
  return await supportedInfrastructureMethodOrDie('deploy', config)(config, logger)
}

export async function nukeCloudProviderResources(config: BoosterConfig, logger: Logger): Promise<void> {
  return await supportedInfrastructureMethodOrDie('nuke', config)(config, logger)
}

export async function startProvider(port: number, config: BoosterConfig): Promise<void> {
  return await supportedInfrastructureMethodOrDie('start', config)(config, port)
}
