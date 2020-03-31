import { Level, Logger } from '@boostercloud/framework-types'

const logPrefix = '[Booster] '

export function buildLogger(level: Level): Logger {
  const prefixedDebugFunction = console.debug.bind(null, logPrefix)
  const prefixedInfoFunction = console.info.bind(null, logPrefix)
  const prefixedErrFunction = console.error.bind(null, logPrefix)

  return {
    debug: level <= Level.debug ? prefixedDebugFunction : noopLog,
    info: level <= Level.info ? prefixedInfoFunction : noopLog,
    error: prefixedErrFunction,
  }
}

function noopLog(): void {}
