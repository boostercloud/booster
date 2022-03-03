import { BoosterConfig, Level, Logger } from '@boostercloud/framework-types'

const logPrefix = '[Booster] '

export function buildLogger(level: Level, config: BoosterConfig): Logger {
  const debug = config.logger?.debug ?? console.debug
  const info = config.logger?.info ?? console.info
  const error = config.logger?.error ?? console.error

  const prefix = config?.logPrefix ?? logPrefix

  const prefixedDebugFunction = debug.bind(null, prefix)
  const prefixedInfoFunction = info.bind(null, prefix)
  const prefixedErrFunction = error.bind(null, prefix)

  return {
    debug: level <= Level.debug ? prefixedDebugFunction : noopLog,
    info: level <= Level.info ? prefixedInfoFunction : noopLog,
    error: prefixedErrFunction,
  }
}

function noopLog(): void {}
