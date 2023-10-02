import { Level, Logger } from '@boostercloud/framework-types'
import * as util from 'util'

function formatMessage(message?: any, ...optionalParams: any[]): string {
  return util
    .format(message, ...optionalParams)
    .replace(/\n|\\n/g, ' ')
    .replace(/\s+/g, ' ')
}

function getLogLevelColor(logLevel: Level): string {
  let errorColor = ''
  switch (logLevel) {
    case Level.error:
      errorColor = '\x1B[31m'
      break
    case Level.warn:
      errorColor = '\x1B[33m'
      break
    case Level.info:
      errorColor = '\x1B[34m'
      break
    case Level.debug:
      errorColor = '\x1B[32m'
      break
    default:
      errorColor = ''
  }
  return errorColor
}

function getLogLevelMessage(logLevel: Level): string {
  const logLevelMessage = Level[logLevel].toUpperCase()
  if (process.env.BOOSTER_ENV === 'local') {
    const color = getLogLevelColor(logLevel)
    return `${color}[${logLevelMessage}]: `
  }
  return `[${logLevelMessage}]: `
}

function newLine() {
  if (process.env.BOOSTER_ENV === 'local') {
    process.stdout.write('\x1B[0m')
  }
  process.stdout.write('\n')
}

const log = (logLevel: Level, message?: any, ...optionalParams: any[]): void => {
  if (process.env.BOOSTER_ENV === 'local') {
    process.stdout.write(util.format(`${getLogLevelColor(logLevel)}${new Date().toISOString()} - `))
  }
  const logLevelMessage = getLogLevelMessage(logLevel)
  process.stdout.write(util.format(logLevelMessage))
  process.stdout.write(formatMessage(message, optionalParams))
  newLine()
}

export class CustomLogger implements Logger {
  debug(message?: any, ...optionalParams: any[]): void {
    log(Level.debug, message, ...optionalParams)
  }

  info(message?: any, ...optionalParams: any[]): void {
    log(Level.info, message, ...optionalParams)
  }

  warn(message?: any, ...optionalParams: any[]): void {
    log(Level.warn, message, ...optionalParams)
  }

  error(message?: any, ...optionalParams: any[]): void {
    log(Level.error, message, ...optionalParams)
  }
}
