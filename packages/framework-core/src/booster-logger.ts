import { Level, Logger } from '@boostercloud/framework-types'

const logPrefix = '[Booster] '

export function buildLogger(level: Level): Logger {
  const prefixedLogFunction = console.log.bind(null, logPrefix)
  const prefixedErrFunction = console.error.bind(null, logPrefix)

  return {
    debug: level <= Level.debug ? prefixedLogFunction : noopLog,
    info: level <= Level.info ? prefixedLogFunction : noopLog,
    error: prefixedErrFunction,
  }
}

function noopLog(): void {}
