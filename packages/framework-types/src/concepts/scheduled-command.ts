import { AnyClass } from '../typelevel'
import { ScheduleInterface } from '../schedule'
import { Register } from './register'

export interface ScheduledCommandInterface extends AnyClass {
  handle(register: Register): Promise<void>
}

export interface ScheduledCommandMetadata {
  readonly class: ScheduledCommandInterface
  readonly scheduledOn: ScheduleInterface
}
