import { GlobalErrorContainer } from './global-error-container'
import { ScheduledCommandMetadata } from '../concepts'
import { ScheduledCommandEnvelope } from '../envelope'

export class ScheduleCommandGlobalError extends GlobalErrorContainer {
  constructor(
    readonly scheduleCommandEnvelope: ScheduledCommandEnvelope,
    readonly scheduleCommandMetadata: ScheduledCommandMetadata,
    originalError: Error
  ) {
    super(originalError)
  }
}
