import { BoosterConfig } from '@boostercloud/framework-types'

export function getProjectNamespaceName(configuration: BoosterConfig): string {
  return `booster-${configuration.appName}-${configuration.environmentName}`
}
