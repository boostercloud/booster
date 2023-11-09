import { HealthIndicatorMetadata } from '@boostercloud/framework-types'

const PATH_SEPARATOR = '/'

export function metadataFromId(
  healthProviders: Record<string, HealthIndicatorMetadata>,
  id: string
): HealthIndicatorMetadata {
  const healthProvider = healthProviders[id]
  if (!healthProvider) {
    throw new Error(`Unexpected HealthProvider id ${id}`)
  }
  return healthProvider
}

export function parentId(healthProvider: HealthIndicatorMetadata): string {
  const childId = healthProvider.healthIndicatorConfiguration.id
  const childPath = childId.split(PATH_SEPARATOR)
  return childPath.slice(0, -1).join(PATH_SEPARATOR)
}

export function rootHealthProviders(
  healthProviders: Record<string, HealthIndicatorMetadata>
): Array<HealthIndicatorMetadata> {
  return Object.values(healthProviders).filter(
    (healthProvider) => healthProvider.healthIndicatorConfiguration.id.split(PATH_SEPARATOR).length === 1
  )
}

export function childrenHealthProviders(
  healthIndicatorMetadata: HealthIndicatorMetadata,
  healthProviders: Record<string, HealthIndicatorMetadata>
): Array<HealthIndicatorMetadata> {
  if (!showChildren(healthIndicatorMetadata, healthProviders)) {
    return []
  }
  const currentParentId = healthIndicatorMetadata.healthIndicatorConfiguration.id
  return Object.values(healthProviders).filter((healthProvider) => {
    return parentId(healthProvider) === currentParentId
  })
}

export function isEnabled(
  mainIndicatorMetadata: HealthIndicatorMetadata,
  healthProviders: Record<string, HealthIndicatorMetadata>
): boolean {
  if (!mainIndicatorMetadata.healthIndicatorConfiguration.enabled) {
    return false
  }
  const parent = healthProviders[parentId(mainIndicatorMetadata)]
  if (!parent) {
    return true
  }
  return isEnabled(parent, healthProviders)
}

export function showChildren(
  mainIndicatorMetadata: HealthIndicatorMetadata,
  healthProviders: Record<string, HealthIndicatorMetadata>
): boolean {
  if (!mainIndicatorMetadata.healthIndicatorConfiguration.showChildren) {
    return false
  }
  const parent = healthProviders[parentId(mainIndicatorMetadata)]
  if (!parent) {
    return true
  }
  return showChildren(parent, healthProviders)
}
