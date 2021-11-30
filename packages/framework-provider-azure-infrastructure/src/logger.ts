import ora from 'ora'
import { Logger } from '@boostercloud/framework-types'

export const oraLogger = ora({ stream: process.stdout })

export const logger: Logger = {
  debug: (message) => oraLogger.warn(message),
  info: (message) => oraLogger.info(message),
  error: (message) => oraLogger.fail(message),
}
