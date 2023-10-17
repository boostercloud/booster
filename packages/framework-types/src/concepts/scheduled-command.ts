import { AnyClass } from '../typelevel'
import { Register } from './register'

export interface ScheduleInterface {
  minute?: string;
  hour?: string;
  day?: string;
  month?: string;
  weekDay?: string;
  year?: string;
}

export interface ScheduledCommandInterface extends AnyClass {
  handle(register: Register): Promise<void>
}

export interface ScheduledCommandMetadata {
  readonly class: ScheduledCommandInterface
  readonly scheduledOn: ScheduleInterface
}
