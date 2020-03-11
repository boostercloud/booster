/**
 * Holds information about a user class annotated with `@Entity`
 */
import { AnyClass, Class } from '../typelevel'
import { UUID } from '.'

export interface EntityInterface {
  id: UUID
}

export interface EntityMetadata {
  readonly class: Class<EntityInterface>
  readonly properties: Array<PropertyMetadata>
}

export interface PropertyMetadata {
  readonly name: string
  readonly type: AnyClass
}
