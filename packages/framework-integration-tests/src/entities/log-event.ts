import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { LogEventReceived } from '../events/log-event-received'

@Entity({
  authorizeReadEvents: 'all',
})
export class LogEvent {
  public constructor(readonly id: UUID, readonly value: string) {}

  public getId() {
    return this.id
  }

  @Reduces(LogEventReceived)
  public static eventReceived(event: LogEventReceived, current: LogEvent): LogEvent {
    return new LogEvent(event.entityID(), event.value)
  }
}
