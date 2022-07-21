import { ReadModelAuthorizer, ReadModelFilterHooks, UUID } from '.'
import { Class } from '../typelevel'
import { PropertyMetadata } from '@boostercloud/metadata-booster'

export interface ReadModelInterface {
  id: UUID
  boosterMetadata?: {
    version: number
    schemaVersion: number
    optimisticConcurrencyValue?: string | number
  }
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  [key: string]: any
}

export interface ReadModelMetadata {
  readonly class: Class<ReadModelInterface>
  readonly properties: Array<PropertyMetadata>
  readonly authorizer: ReadModelAuthorizer
  readonly before: NonNullable<ReadModelFilterHooks['before']>
}
