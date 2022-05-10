import { Register } from './register'
import { EventInterface } from './event'

export interface EventHandlerInterface {
  handle(event: EventInterface, register: Register): Promise<void>
}
