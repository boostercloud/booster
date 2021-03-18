import { Logger } from '@boostercloud/framework-types'

export const scopeLogger = (scope: string, logger: Logger): Logger =>
  Object.keys(logger).reduce((prev, key) => {
    const k = key as keyof Logger
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    prev[k] = (...args: any[]): void => prev[k](`${scope}#`, ...args)
    return prev
  }, logger)
