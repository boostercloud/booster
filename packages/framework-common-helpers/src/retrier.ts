import { Class, Logger, RetryConfig } from '@boostercloud/framework-types'

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
  logicToRetry: (tryNumber?: number) => Promise<TReturn>,
  errorClassThatRetries: Class<Error>,
  logger?: Logger,
  maxRetries = 1000
): Promise<TReturn> {
  let tryNumber
  let errorAfterMaxTries: Error | undefined
  for (tryNumber = 1; tryNumber <= maxRetries; tryNumber++) {
    try {
      logger?.debug(`[retryIfError] Try number ${tryNumber}`)
      const result = await logicToRetry(tryNumber)
      logger?.debug(`[retryIfError] Succeeded after ${tryNumber} retries`)
      return result
    } catch (e) {
      const error = e as Error
      checkRetryError(error, errorClassThatRetries, logger)
      errorAfterMaxTries = error
    }
  }
  throw new Error(
    `[retryIfError] Reached the maximum number of retries (${maxRetries}), but still getting the following error: ${errorAfterMaxTries}`
  )
}

function checkRetryError(e: Error, errorClassThatRetries: Class<Error>, logger?: Logger): void {
  if (!(e instanceof errorClassThatRetries)) {
    logger?.debug('[checkRetryError] Logic failed with an error that must not be retried. Rethrowing')
    throw e
  }
}

/**
 * Retries an async function with exponential backoff and jitter.
 * @param logicToRetry Async function to retry
 * @param config Retry configuration
 * @param logger Optional logger for retry attempts
 * @returns The result of the first successful retry
 */
export async function retryWithBackoff<TReturn>(
  logicToRetry: () => Promise<TReturn>,
  config: RetryConfig,
  logger?: Logger
): Promise<TReturn> {
  let attempts = 0
  let lastError: Error | undefined

  while (attempts < config.maxRetries) {
    try {
      return await logicToRetry()
    } catch (error) {
      attempts++
      lastError = error as Error

      if (attempts === config.maxRetries) {
        logger?.error(`[retryWithBackoff] Failed after ${attempts} attempts`, lastError)
        throw lastError
      }

      const delay = calculateRetryDelay(attempts, config)
      logger?.debug(`[retryWithBackoff] Attempt ${attempts} failed, retrying in ${delay}ms...`)
      await new Promise((resolve) => setTimeout(resolve, delay))
    }
  }

  throw lastError
}

/**
 * Calculates the delay for a retry attempt using exponential backoff with jitter.
 * @param attempt Current attempt number (1-based)
 * @param config Retry configuration
 * @returns Delay in milliseconds
 */
export function calculateRetryDelay(attempt: number, config: RetryConfig): number {
  const baseDelay = Math.min(config.initialDelay * Math.pow(config.backoffFactor, attempt - 1), config.maxDelay)
  const jitter = baseDelay * config.jitterFactor * (Math.random() * 2 - 1)
  return baseDelay * jitter
}
