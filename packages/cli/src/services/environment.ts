import { Logger } from '@boostercloud/framework-types'

export function initializeEnvironment(logger: Logger, environment?: string): boolean {
  // We override the environment with the one passed via flags
  if (environment) {
    process.env.BOOSTER_ENV = environment
  }
  // If the resulting environment is not set, the user didn't provide an environment and it's not configured in the OS
  if (!currentEnvironment()) {
    logger.error(
      'Error: No environment set. Use the flag `-e` or set the environment variable BOOSTER_ENV to set it before running this command. Example usage: `boost deploy -e <environment>`.'
    )
    return false
  }
  return true
}

export function currentEnvironment(): string | undefined {
  return process.env.BOOSTER_ENV
}
