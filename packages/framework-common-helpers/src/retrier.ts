import { Class, Logger } from '@boostercloud/framework-types'

export async function retryIfError<TReturn>(
  logger: Logger,
  logicToRetry: () => Promise<TReturn>,
  errorClassThatRetries: Class<Error>,
  maxRetries = 1000
): Promise<TReturn> {
  let tryNumber
  let errorAfterMaxTries: Error | undefined
  for (tryNumber = 1; tryNumber <= maxRetries; tryNumber++) {
    try {
      logger.debug(`[retrier] Try number ${tryNumber}`)
      const result = await logicToRetry()
      logger.debug(`[retrier] Logic succeeded after ${tryNumber} retries`)
      return result
    } catch (e) {
      checkRetryError(e, errorClassThatRetries, logger)
      errorAfterMaxTries = e
    }
  }
  throw new Error(
    `[retrier] Reached the maximum number of retries (${maxRetries}), but still getting the following error: ${errorAfterMaxTries}`
  )
}

function checkRetryError(e: Error, errorClassThatRetries: Class<Error>, logger: Logger): void {
  if (!(e instanceof errorClassThatRetries)) {
    logger.debug('[retrier] Logic failed with an error that must not be retried. Rethrowing')
    throw e
  }
}
