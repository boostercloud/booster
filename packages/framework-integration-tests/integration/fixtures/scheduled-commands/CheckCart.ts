import { ScheduledCommand } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'

@ScheduledCommand({
  // Specify schedule settings here. By default, it will be triggered every 30 minutes
  minute: '0/30',
})
export class CheckCart {
  public static async handle(register: Register): Promise<void> {
    /* YOUR CODE HERE */
  }
}
