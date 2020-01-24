import { UUID } from './uuid'

/**
 * An `Event` is a fact that has happened in your system.
 * All Event classes of your application must implement this interface.
 */

export interface EventInterface {
  entityID(): UUID
}
