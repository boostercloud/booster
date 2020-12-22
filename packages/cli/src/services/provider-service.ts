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

function validateConfig(config: BoosterConfig): void {
  assertNameIsCorrect(config.appName)
}

/**
 * We load the infrastructure package dynamically here as a cli plugin to avoid
 * including it among the user project's dependences.
 */
function loadInfrastructure(config: BoosterConfig): ProviderInfrastructure {
  const packageDescription = config.provider.packageDescription()
  // TODO: Check that the package is installed and that the version matches
  // try {
  //   getInstalledPathSync(infrastructurePackageName)
  // } catch (e) {
  //   throw new Error(
  //     `The AWS infrastructure package must be installed to perform this operation, please install it globally running 'yarn global add ${infrastructurePackageName}'`
  //   )
  // }
  return dynamicLoad(packageDescription.name + '-infrastructure').Infrastructure(config.rockets)
}

export async function deployToCloudProvider(config: BoosterConfig, logger: Logger): Promise<void> {
  validateConfig(config)
  const infrastructure = loadInfrastructure(config)
  if (infrastructure.deploy) {
    await infrastructure.deploy(config, logger)
  } else {
    throw new NonSupportedInfrastructureOperationError('deploy')
  }
}

export async function nukeCloudProviderResources(config: BoosterConfig, logger: Logger): Promise<void> {
  validateConfig(config)
  const infrastructure = loadInfrastructure(config)
  if (infrastructure.nuke) {
    await infrastructure.nuke(config, logger)
  } else {
    throw new NonSupportedInfrastructureOperationError('nuke')
  }
}

export async function startProvider(port: number, config: BoosterConfig): Promise<void> {
  validateConfig(config)
  const infrastructure = loadInfrastructure(config)
  if (infrastructure.start) {
    await infrastructure.start(config, port)
  } else {
    throw new NonSupportedInfrastructureOperationError('start')
  }
}

class NonSupportedInfrastructureOperationError extends Error {
  constructor(methodName: 'deploy' | 'nuke' | 'start') {
    super(
      `Attempted to perform the '${methodName}' operation with a provider that does not support this feature, please check your environment configuration.`
    )
  }
}
