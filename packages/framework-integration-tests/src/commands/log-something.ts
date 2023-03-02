import { Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'

@Command({
  authorize: (currentUser?) => {
    if (currentUser?.claims['canLogSomething'] !== true) {
      return Promise.reject('You are not allowed to log something')
    }
    return Promise.resolve()
  },
})
export class LogSomething {
  public constructor() {}

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  public static async handle(command: LogSomething, register: Register): Promise<void> {
    console.log('something')
  }
}
