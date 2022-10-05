import { BoosterConfig, Register } from '@boostercloud/framework-types'
import { Events } from './sdk/events'

export class RegisterHandler {
  public static async handle(config: BoosterConfig, register: Register): Promise<void> {
    // This method is currently just storing the events in the event store,
    // but it's called by the framework anytime a register object is used,
    // so it's convenient to keep it just in case the logic of register processing
    // changes in the future.
    return Events.with(config).store(register.eventList, register)
  }
}
