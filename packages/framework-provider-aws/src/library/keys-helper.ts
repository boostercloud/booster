import { EventEnvelope, UUID } from '@boostercloud/framework-types'

export function partitionKeyForEvent(
  entityTypeName: string,
  entityID: UUID,
  kind: EventEnvelope['kind'] = 'event'
): string {
  return `${entityTypeName}-${entityID}-${kind}`
}

const sortingKeyEncodingSeparator = '__'

export function encodeEventStoreSortingKey(decodedSortingKey: string): string {
  return `${decodedSortingKey}${sortingKeyEncodingSeparator}${UUID.generate()}`
}
export function decodeEventStoreSortingKey(encodedSortingKey: string): string {
  return encodedSortingKey.split(sortingKeyEncodingSeparator)[0]
}

export function modifyEventsDecodingSortingKeys(...events: Array<EventEnvelope>): void {
  for (const event of events) {
    event.createdAt = decodeEventStoreSortingKey(event.createdAt)
  }
}

export function partitionKeyForIndexByEntity(entityTypeName: string, kind: EventEnvelope['kind']): string {
  return `${entityTypeName}-${kind}`
}

export function sortKeyForSubscription(connectionID: string, subscriptionID: string): string {
  return `${connectionID}-${subscriptionID}`
}
