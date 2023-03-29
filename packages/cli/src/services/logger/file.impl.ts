import * as winston from 'winston'
import { LoggerComponent } from '../../common/component'
import { Level, Logger } from '@boostercloud/framework-types'

@LoggerComponent()
export class FileLogger implements Logger {
  private logger: winston.Logger
  private indentation = 0

  public constructor(readonly logLevel: Level = Level.info) {
    this.logger = winston.createLogger({
      level: Level[this.logLevel],
      format: winston.format.combine(winston.format.timestamp(), winston.format.json()),

      transports: [
        new winston.transports.File({
          filename: 'errors.log',
        }),
      ],
    })
  }

  public logProcess<T>(message: string, process: () => T): T {
    this.info(`${message} [START]`)
    this.indentation += 2
    try {
      const result = process()
      this.indentation -= 2
      this.info(`${message} [SUCCESS]`)
      return result
    } catch (e) {
      this.indentation -= 2
      this.error(`${message} [ERROR]`)
      throw e
    }
  }

  public debug(data: unknown, ...optionalParams: unknown[]): void {
    if (this.logLevel > Level.debug) return
    this.logger.debug(this.buildLogMessage(data, ...optionalParams))
  }

  public info(data: unknown, ...optionalParams: unknown[]): void {
    if (this.logLevel > Level.info) return
    this.logger.info(this.buildLogMessage(data, ...optionalParams))
  }

  public warn(data: unknown, ...optionalParams: unknown[]): void {
    if (this.logLevel > Level.warn) return
    this.logger.warn(this.buildLogMessage(data, ...optionalParams))
  }

  public error(data: unknown, ...optionalParams: unknown[]): void {
    if (this.logLevel > Level.error) return
    this.logger.error(this.buildLogMessage(data, ...optionalParams))
  }

  private buildLogMessage(data: unknown, ...optionalParams: unknown[]): string {
    let message = ' '.repeat(this.indentation)
    if (typeof data === 'string') {
      message += data
    } else {
      message += JSON.stringify(data)
    }
    if (optionalParams.length > 0) {
      message += '\n\t' + optionalParams.map((param) => JSON.stringify(param)).join('\n\t')
    }
    return message
  }
}
