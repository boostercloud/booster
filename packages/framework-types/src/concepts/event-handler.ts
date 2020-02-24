import { EventInterface } from '../concepts/event'
import { Register } from './register'

export interface EventHandlerInterface {
  handle(event: EventInterface, register: Register): void
}