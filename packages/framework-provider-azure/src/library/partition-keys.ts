import { UUID } from '@boostercloud/framework-types'

export function partitionKeyForEvent(entityTypeName: string, entityID: UUID): string {
  return `${entityTypeName}-${entityID}-event`
}

export function partitionKeyForSnapshot(entityTypeName: string, entityID: UUID): string {
  return `${entityTypeName}-${entityID}-snapshot`
}
