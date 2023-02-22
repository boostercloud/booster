import ora from 'ora'
import { LoggerComponent } from '../../common/component'
import * as process from 'process'
import { LevelString, Level, Logger } from '@boostercloud/framework-types'

@LoggerComponent()
export class OraLogger implements Logger {
  private oraLogger = ora({ stream: process.stdout })

  public constructor(readonly logLevel: Level = Level.info) {
    const envLevel = process.env.BOOSTER_LOG_LEVEL
    if (envLevel && Object.keys(Level).includes(envLevel)) {
      this.logLevel = Level[envLevel as LevelString]
    }
  }

  public debug(data: unknown, ...optionalParams: unknown[]): void {
    if (this.logLevel > Level.debug) return
    const message = [JSON.stringify(data), ...optionalParams].join('\n\t')
    this.oraLogger.warn(message)
  }

  public info(data: unknown, ...optionalParams: unknown[]): void {
    if (this.logLevel > Level.info) return
    const message = [JSON.stringify(data), ...optionalParams].join('\n\t')
    this.oraLogger.info(message)
  }

  public warn(data: unknown, ...optionalParams: unknown[]): void {
    if (this.logLevel > Level.warn) return
    const message = [JSON.stringify(data), ...optionalParams].join('\n\t')
    this.oraLogger.warn(message)
  }

  public error(data: unknown, ...optionalParams: unknown[]): void {
    if (this.logLevel > Level.error) return
    const message = [JSON.stringify(data), ...optionalParams].join('\n\t')
    this.oraLogger.fail(message)
  }
}
