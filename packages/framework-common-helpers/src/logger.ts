import { BoosterConfig, Level, Logger } from '@boostercloud/framework-types'

const defaultLogPrefix = 'Booster'

export function getLogger(config: BoosterConfig, location?: string, overridenLogPrefix?: string): Logger {
  const debug = config.logger?.debug ?? console.debug
  const info = config.logger?.info ?? console.info
  const warn = config.logger?.warn ?? console.warn
  const error = config.logger?.error ?? console.error

  const logPrefix = overridenLogPrefix ?? config?.logPrefix ?? defaultLogPrefix
  const locationStr = location ? `|${location}: ` : ': '
  const prefix = `[${logPrefix}]${locationStr}`

  const prefixedDebugFunction = debug.bind(null, prefix)
  const prefixedInfoFunction = info.bind(null, prefix)
  const prefixedWarnFunction = warn.bind(null, prefix)
  const prefixedErrFunction = error.bind(null, prefix)

  return {
    debug: config.logLevel <= Level.debug ? prefixedDebugFunction : noopLog,
    info: config.logLevel <= Level.info ? prefixedInfoFunction : noopLog,
    warn: config.logLevel <= Level.warn ? prefixedWarnFunction : noopLog,
    error: prefixedErrFunction,
  }
}

function noopLog(): void {}
