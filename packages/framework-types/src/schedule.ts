export interface ScheduleInterface {
  readonly minute?: number
  readonly hour?: number | '*'
  readonly day?: number | '*'
  readonly month?: number | '*'
  readonly weekDay?: string | '?'
  readonly year?: number | '*'
}
