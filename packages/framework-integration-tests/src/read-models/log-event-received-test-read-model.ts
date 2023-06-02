import { Projects, ReadModel } from '@boostercloud/framework-core'
import { ProjectionResult, UUID } from '@boostercloud/framework-types'
import { LogEventTest } from '../entities/log-event-test'

@ReadModel({
  authorize: 'all',
})
export class LogEventReceivedTestReadModel {
  public constructor(readonly id: UUID, readonly value: string) {}

  @Projects(LogEventTest, 'id')
  public static updateEventCounter(
    eventCounter: LogEventTest,
    old?: LogEventReceivedTestReadModel
  ): ProjectionResult<LogEventReceivedTestReadModel> {
    eventCounter.getId()
    return new LogEventReceivedTestReadModel(eventCounter.id, eventCounter.value)
  }
}
