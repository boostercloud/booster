import { ScheduledCommand } from '@boostercloud/framework-core'

@ScheduledCommand({
  // Specify schedule settings here. By default, {{{ name }}} will be triggered every 30 minutes
  minute: '0/30',
})
export class CheckCart {
  public static async handle(): Promise<void> {
    /* YOUR CODE HERE */
  }
}
