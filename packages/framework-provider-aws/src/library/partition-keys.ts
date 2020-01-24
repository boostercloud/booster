import { EventEnvelope } from '@boostercloud/framework-types'

export function partitionKeyForEvent(
  entityTypeName: string,
  entityID: string,
  kind: EventEnvelope['kind'] = 'event'
): string {
  return `${entityTypeName}-${entityID}-${kind}`
}
