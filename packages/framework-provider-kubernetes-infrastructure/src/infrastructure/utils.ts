import { BoosterConfig } from '@boostercloud/framework-types'

export function getProjectNamespaceName(configuration: BoosterConfig): string {
  return `booster-${configuration.appName}-${configuration.environmentName}`
}

// eslint-disable-next-line @typescript-eslint/explicit-function-return-type
export function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
