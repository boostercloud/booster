import { BoosterConfig } from '@boostercloud/framework-types'

export function getStackNames(config: BoosterConfig): Array<string> {
  return [config.resourceNames.applicationStack]
}

export function getStackToolkitName(config: BoosterConfig): string {
  return config.appName + '-toolkit'
}

export function getStackToolkitBucketName(config: BoosterConfig): string {
  return config.appName + '-toolkit-bucket'
}
