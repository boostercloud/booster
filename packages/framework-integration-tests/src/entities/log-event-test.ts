import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { LogEventReceivedTest } from '../events/log-event-received-test'

@Entity({
  authorizeReadEvents: 'all',
})
export class LogEventTest {
  public constructor(readonly id: UUID, readonly value: string) {}

  public getId() {
    return this.id
  }

  @Reduces(LogEventReceivedTest)
  public static eventReceived(event: LogEventReceivedTest, current: LogEventTest): LogEventTest {
    return new LogEventTest(event.entityID(), event.value)
  }
}
