import { RoleAccess, UUID } from '.'
import { Class } from '../typelevel'
import { PropertyMetadata } from 'metadata-booster'
import { FilterFor } from '../searcher'
import { UserEnvelope } from '../envelope'

export interface ReadModelInterface {
  id: UUID
  boosterMetadata?: {
    version: number
  }
  [key: string]: any
}

export interface ReadModelMetadata {
  readonly class: Class<ReadModelInterface>
  readonly properties: Array<PropertyMetadata>
  readonly authorizedRoles: RoleAccess['authorize']
  readonly before: NonNullable<ReadModelFilterHooks['before']>
}

export interface ReadModelFilterHooks {
  readonly before?: Array<BeforeFunction>
}

export type BeforeFunction = (
  filter: FilterFor<Class<ReadModelInterface>>,
  currentUser?: UserEnvelope
) => FilterFor<Class<ReadModelInterface>>
