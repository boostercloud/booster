import { AnyClass } from '../typelevel'
import { ScheduleInterface } from '../schedule'

export interface ScheduledCommandInterface extends AnyClass {
  handle(): Promise<void>
}

export interface ScheduledCommandMetadata {
  readonly class: ScheduledCommandInterface
  readonly scheduledOn?: ScheduleInterface
}
