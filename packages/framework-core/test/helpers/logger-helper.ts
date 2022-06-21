import { Logger } from '@boostercloud/framework-types'

/**
 * Logger that doesn't do anything
 */
export const noopLogger: Logger = {
  debug: () => {},
  warn: () => {},
  info: () => {},
  error: () => {},
}
