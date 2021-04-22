import { Logger } from '@boostercloud/framework-types'

export const scopeLogger = (scope: string, logger: Logger): Logger => {
  const newLogger: Logger = { ...logger }
  newLogger.debug = (...args: any[]) => logger.debug([`[${scope}]`, ...args].join(' '))
  newLogger.info = (...args: any[]) => logger.info([`[${scope}]`, ...args].join(' '))
  newLogger.error = (...args: any[]) => logger.error([`[${scope}]`, ...args].join(' '))
  return newLogger
}
