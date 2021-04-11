import { Level, Logger } from '@boostercloud/framework-types'

export const scopeLogger = (scope: string, logger: Logger, level = Level.info): Logger => {
  const newLogger: Logger = { ...logger }
  newLogger.debug = (...args: any[]) => {
    if (level <= Level.debug) {
      logger.debug([`[${scope}]`, ...args].join(' '))
    }
  }
  newLogger.info = (...args: any[]) => {
    if (level <= Level.info) {
      logger.info([`[${scope}]`, ...args].join(' '))
    }
  }
  newLogger.error = (...args: any[]) => {
    if (level <= Level.error) {
      logger.error([`[${scope}]`, ...args].join(' '))
    }
  }
  return newLogger
}
