import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { LogEventReceived } from '../events/log-event-received'

@Command({
  authorize: 'all',
})
export class LogEvent {
  public constructor(readonly logEventId: UUID, readonly value: string) {}

  public static async handle(command: LogEvent, register: Register): Promise<void> {
    register.events(new LogEventReceived(command.logEventId, command.value))
  }
}
