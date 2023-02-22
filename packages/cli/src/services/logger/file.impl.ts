import * as winston from 'winston'
import { LoggerComponent } from '../../common/component'
import { Level, Logger } from '@boostercloud/framework-types'

@LoggerComponent()
export class FileLogger implements Logger {
  private logger: winston.Logger

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

  public debug(data: unknown, ...optionalParams: unknown[]): void {
    if (this.logLevel > Level.debug) return
    const message = [JSON.stringify(data), ...optionalParams].join('\n\t')
    this.logger.warn(message)
  }

  public info(data: unknown, ...optionalParams: unknown[]): void {
    if (this.logLevel > Level.info) return
    const message = [JSON.stringify(data), ...optionalParams].join('\n\t')
    this.logger.info(message)
  }

  public warn(data: unknown, ...optionalParams: unknown[]): void {
    if (this.logLevel > Level.warn) return
    const message = [JSON.stringify(data), ...optionalParams].join('\n\t')
    this.logger.warn(message)
  }

  public error(data: unknown, ...optionalParams: unknown[]): void {
    if (this.logLevel > Level.error) return
    const message = [JSON.stringify(data), ...optionalParams].join('\n\t')
    this.logger.error(message)
  }
}
