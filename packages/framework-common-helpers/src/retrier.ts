import { Class, Logger } from '@boostercloud/framework-types'

/**
 * Retries an async function if it fails with an error that matches the given class.
 *
 * @param logicToRetry Async function to retry
 * @param errorClassThatRetries Error class. If the async function fails with an error that matches this class, it will be retried.
 * @param logger Logger to use for logging. If not provided, no logging will be performed.
 * @param maxRetries Maximum number of retries. If not provided, the default is 1000.
 * @returns The result of the first successful retry.
 */
export async function retryIfError<TReturn>(
  logicToRetry: () => Promise<TReturn>,
  errorClassThatRetries: Class<Error>,
  logger?: Logger,
  maxRetries = 1000
): Promise<TReturn> {
  let tryNumber
  let errorAfterMaxTries: Error | undefined
  for (tryNumber = 1; tryNumber <= maxRetries; tryNumber++) {
    try {
      logger?.debug(`[retryIfError] Try number ${tryNumber}`)
      const result = await logicToRetry()
      logger?.debug(`[retryIfError] Succeeded after ${tryNumber} retries`)
      return result
    } catch (error_) {
      const error = error_ as Error
      checkRetryError(error, errorClassThatRetries, logger)
      errorAfterMaxTries = error
    }
  }
  throw new Error(
    `[retryIfError] Reached the maximum number of retries (${maxRetries}), but still getting the following error: ${errorAfterMaxTries}`
  )
}

function checkRetryError(error: Error, errorClassThatRetries: Class<Error>, logger?: Logger): void {
  if (!(error instanceof errorClassThatRetries)) {
    logger?.debug('[checkRetryError] Logic failed with an error that must not be retried. Rethrowing')
    throw error
  }
}
