import { EventInterface, NotificationInterface, Register } from '@boostercloud/framework-types'
import { GlobalEventHandler } from '@boostercloud/framework-core'
import { LogEventReceived } from '../events/log-event-received'
import { LogEventReceivedTest } from '../events/log-event-received-test'

@GlobalEventHandler()
export class GlobalHandler {
  public static async handle(event: EventInterface | NotificationInterface, register: Register): Promise<void> {
    if (event instanceof LogEventReceived) {
      register.events(new LogEventReceivedTest(event.entityID(), event.value))
    }
  }
}
