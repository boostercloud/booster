import ora from 'ora'
import { LoggerComponent } from '../../common/component'
import * as process from 'process'
import { LevelString, Level, Logger } from '@boostercloud/framework-types'

@LoggerComponent()
export class OraLogger implements Logger {
  private oraLogger = ora({ stream: process.stdout })

  constructor(readonly logLevel: Level = Level.info) {
    const envLevel = process.env.BOOSTER_LOG_LEVEL
    if (envLevel && Object.keys(Level).includes(envLevel)) {
      this.logLevel = Level[envLevel as LevelString]
    }
  }

  logProcess<T>(message: string, process: () => T): T {
    try {
      this.oraLogger.start(message)
      const result = process()
      this.oraLogger.succeed(message)
      return result
    } catch (e) {
      this.oraLogger.fail(message)
      throw e
    }
  }

  debug(data: unknown, ...optionalParams: unknown[]): void {
    if (this.logLevel > Level.debug) return
    const message = this.makeMessage(data, ...optionalParams)
    console.debug(message)
  }

  info(data: unknown, ...optionalParams: unknown[]): void {
    if (this.logLevel > Level.info) return
    const message = this.makeMessage(data, ...optionalParams)
    if (this.oraLogger.isSpinning) {
      this.oraLogger.text = message
    } else {
      this.oraLogger.info(message)
    }
  }

  warn(data: unknown, ...optionalParams: unknown[]): void {
    if (this.logLevel > Level.warn) return
    const message = this.makeMessage(data, ...optionalParams)
    this.oraLogger.warn(message)
  }

  error(data: unknown, ...optionalParams: unknown[]): void {
    if (this.logLevel > Level.error) return
    const message = this.makeMessage(data, ...optionalParams)
    this.oraLogger.fail(message)
  }

  private makeMessage(data: unknown, ...optionalParams: unknown[]): string {
    const msg = typeof data === 'string' ? data : JSON.stringify(data)
    return optionalParams.length > 0 ? msg + '\n\t' + optionalParams.join(' ') : msg
  }
}
