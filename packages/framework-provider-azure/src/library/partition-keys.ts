import { EventEnvelope, UUID } from '@boostercloud/framework-types'

export function partitionKeyForEvent(entityTypeName: string, entityID: UUID): string {
  return `${entityTypeName}-${entityID}-event`
}

export function partitionKeyForSnapshot(entityTypeName: string, entityID: UUID): string {
  return `${entityTypeName}-${entityID}-snapshot`
}

export function partitionKeyForIndexByEntity(entityTypeName: string, kind: EventEnvelope['kind']): string {
  return `${entityTypeName}-${kind}`
}
