import { ReadModelAuthorizer, ReadModelFilterHooks, UUID } from '.'
import { Class } from '../typelevel'
import { PropertyMetadata } from '@boostercloud/metadata-booster'

export interface BoosterMetadata {
  version: number
  schemaVersion: number
  optimisticConcurrencyValue?: string | number
  lastUpdateAt?: string
  lastProjectionInfo?: {
    entityId: string
    entityName: string
    entityUpdatedAt: string
    projectionMethod: string
  }
}

export interface ReadModelInterface {
  id: UUID
  boosterMetadata?: BoosterMetadata
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  [key: string]: any
}

export interface ReadModelMetadata<TReadModel extends ReadModelInterface = ReadModelInterface> {
  readonly class: Class<ReadModelInterface>
  readonly properties: Array<PropertyMetadata>
  readonly authorizer: ReadModelAuthorizer
  readonly before: NonNullable<ReadModelFilterHooks<TReadModel>['before']>
}
