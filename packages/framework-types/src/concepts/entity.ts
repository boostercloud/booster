/**
 * Holds information about a user class annotated with `@Entity`
 */
import { Class } from '../typelevel'
import { EventStreamAuthorizer, UUID } from '.'

export interface EntityInterface {
  id: UUID
}

export interface EntityMetadata {
  readonly class: Class<EntityInterface>
  readonly eventStreamAuthorizer: EventStreamAuthorizer
}
