import ora from 'ora'
import * as process from 'process'
import { TaskLogger } from '.'
import { LoggerComponent } from '../../common/component'

@LoggerComponent()
export class OraTaskLogger implements TaskLogger {
  private oraLogger = ora({ stream: process.stdout })

  constructor() {}

  async logTask<T>(message: string, task: () => T): Promise<T> {
    try {
      this.oraLogger.start(message)
      const result = task()
      this.oraLogger.succeed(message)
      return result
    } catch (e) {
      this.oraLogger.fail(message)
      throw e
    }
  }
}
