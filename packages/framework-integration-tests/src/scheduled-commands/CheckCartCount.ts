import { ScheduledCommand } from '@boostercloud/framework-core'

@ScheduledCommand({
  minute: '0/5',
})
export class CheckCartCount {
  public static async handle(): Promise<void> {
    console.log('This is a ScheduledCommand running')
    await Promise.resolve('this is fine!')
  }
}
