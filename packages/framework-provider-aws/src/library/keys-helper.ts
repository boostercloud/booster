import { EntitySnapshotEnvelope, EventEnvelope, UUID } from '@boostercloud/framework-types'

export function partitionKeyForEvent(entityTypeName: string, entityID: UUID): string {
  return `${entityTypeName}-${entityID}-event`
}

export function partitionKeyForEntitySnapshot(entityTypeName: string, entityID: UUID): string {
  return `${entityTypeName}-${entityID}-snapshot`
}

export function partitionKeyForIndexByEntity(
  entityTypeName: string,
  kind: EventEnvelope['kind'] | EntitySnapshotEnvelope['kind']
): string {
  return `${entityTypeName}-${kind}`
}

export function sortKeyForSubscription(connectionID: string, subscriptionID: string): string {
  return `${connectionID}-${subscriptionID}`
}
