import ora from 'ora'
import { Logger } from '@boostercloud/framework-types'
import { appendFileSync } from 'fs'
import * as path from 'path'

export const oraLogger = ora({ stream: process.stdout })

export const logger: Logger = {
  debug: (message) => oraLogger.warn(message),
  info: (message) => oraLogger.info(message),
  error: (message) => oraLogger.fail(message),
}

export function appendOnErrorsFile(data: string): void {
  const errorsFile = path.join(process.cwd(), 'errors.log')
  const transformedData = data.split("\n").map(line => `[${new Date().toISOString()}] ${line}`).join("\n")
  appendFileSync(errorsFile, transformedData)
}