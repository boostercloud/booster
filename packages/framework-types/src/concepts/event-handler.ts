import { Register } from './register'
import { EventInterface } from './event'
import { NotificationInterface } from './notification'

export interface EventHandlerInterface {
  handle(event: EventInterface | NotificationInterface, register: Register): Promise<void>
}
