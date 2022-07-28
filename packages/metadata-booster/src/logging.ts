import { createLogger, format, Logger, transports } from 'winston'
import * as process from 'process'
import { match } from 'ts-pattern'
import * as str from 'fp-ts/string'

const loggerFormat = format.printf(({ level, message, label }) => `[${level}] ${label} - ${message}`)

export const makeModuleLogger =
  (moduleName: string) =>
  (functionName: string): Logger => {
    const loggingOpts = process.env['METADATA_BOOSTER_DEBUG']?.trim() ?? ''

    const trs = match(loggingOpts)
      .with('', () => [])
      .when(str.includes('.log'), () => [new transports.File({ filename: loggingOpts })])
      .otherwise(() => [new transports.Console()])

    return createLogger({
      level: 'debug',
      format: format.combine(format.label({ label: `${moduleName}#${functionName}` }), format.splat(), loggerFormat),
      transports: trs,
    })
  }
