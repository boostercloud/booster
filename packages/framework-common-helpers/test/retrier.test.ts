import { calculateRetryDelay, retryIfError, retryWithBackoff } from '../src'
import { expect } from './helpers/expect'
import { RetryConfig } from '@boostercloud/framework-types'
import { fake } from 'sinon'

class ErrorToRetry extends Error {}

describe('the `retrier` helpers', () => {
  describe('the `retryIfError` method', () => {
    it('returns the result and does not retry if there is no error', async () => {
      let retries = 0
      const returnedValue = await retryIfError(async () => {
        retries++
        return 'returned value'
      }, ErrorToRetry)
      expect(returnedValue).to.be.equal('returned value')
      expect(retries).to.be.equal(1)
    })

    it('rethrows a non-expected error and does not retry', async () => {
      let retries = 0
      const returnedValuePromise = retryIfError(async () => {
        retries++
        throw new Error('unexpected error')
      }, ErrorToRetry)
      await expect(returnedValuePromise).to.eventually.be.rejectedWith('unexpected error')
      expect(retries).to.be.equal(1)
    })

    it('retries 5 times if the expected error happens 4 times', async () => {
      let retries = 0
      const result = await retryIfError(async () => {
        retries++
        if (retries <= 4) throw new ErrorToRetry('expected error')
        return 'success'
      }, ErrorToRetry)
      expect(result).to.be.equal('success')
      expect(retries).to.be.equal(5)
    })

    it('throws after "maxRetries" is reached with the expected error happening', async () => {
      const maxRetries = 20
      let retries = 0
      const returnedValuePromise = retryIfError(
        async () => {
          retries++
          throw new ErrorToRetry('expected error')
        },
        ErrorToRetry,
        undefined,
        maxRetries
      )
      await expect(returnedValuePromise).to.eventually.be.rejectedWith('Reached the maximum number of retries')
      expect(retries).to.be.equal(maxRetries)
    })
  })

  describe('the `retryWithBackoff` method', () => {
    const defaultConfig: RetryConfig = {
      maxRetries: 3,
      initialDelay: 100,
      maxDelay: 1000,
      backoffFactor: 2,
      jitterFactor: 0.1,
    }

    it('returns the result and does not retry if there is no error', async () => {
      let retries = 0
      const returnedValue = await retryWithBackoff(async () => {
        retries++
        return 'returned value'
      }, defaultConfig)
      expect(returnedValue).to.be.equal('returned value')
      expect(retries).to.be.equal(1)
    })

    it('retries until success within maxRetries', async () => {
      let retries = 0
      const result = await retryWithBackoff(async () => {
        retries++
        if (retries <= 2) throw new Error('temporary error')
        return 'success'
      }, defaultConfig)
      expect(result).to.be.equal('success')
      expect(retries).to.be.equal(3)
    })

    it('throws after maxRetries is reached', async () => {
      let retries = 0
      const returnedValuePromise = retryWithBackoff(async () => {
        retries++
        throw new Error('persistent error')
      }, defaultConfig)
      await expect(returnedValuePromise).to.eventually.be.rejectedWith('persistent error')
      expect(retries).to.be.equal(defaultConfig.maxRetries)
    })

    it('respects the maxDelay cap', async () => {
      const config: RetryConfig = {
        ...defaultConfig,
        initialDelay: 1000,
        maxDelay: 2000,
      }

      // Calculate delays for 3 attempts
      const delay1 = calculateRetryDelay(1, config)
      const delay2 = calculateRetryDelay(2, config)
      const delay3 = calculateRetryDelay(3, config)

      expect(delay1).to.be.lessThan(config.maxDelay)
      expect(delay2).to.be.lte(config.maxDelay)
      expect(delay3).to.be.lte(config.maxDelay)
    })

    it('includes jitter in the delay calculation', async () => {
      const config: RetryConfig = {
        ...defaultConfig,
        jitterFactor: 0.5, // 50% jitter for easier testing
      }

      // Calculate multiple delays to ensure jitter is applied
      const delays = Array.from({ length: 10 }, (_, i) => calculateRetryDelay(i + 1, config))

      // Check that we have some variation in the delays
      const uniqueDelays = new Set(delays)
      expect(uniqueDelays.size).to.be.greaterThan(1)
    })

    it('logs retry attempts when logger is provided', async () => {
      const logger = {
        debug: fake(),
        error: fake(),
      }

      let retries = 0
      await retryWithBackoff(
        async () => {
          retries++
          if (retries <= 2) throw new Error('temporary error')
          return 'success'
        },
        defaultConfig,
        logger as any
      )

      expect(logger.debug).to.have.been.calledTwice // Two retry attempts
      expect(logger.error).to.not.have.been.called // No error logging since it succeeded
    })

    describe('error filtering', () => {
      class RetryableError extends Error {
        constructor(message: string) {
          super(message)
          this.name = 'RetryableError'
        }
      }

      class NonRetryableError extends Error {
        constructor(message: string) {
          super(message)
          this.name = 'NonRetryableError'
        }
      }

      class OtherError extends Error {
        constructor(message: string) {
          super(message)
          this.name = 'OtherError'
        }
      }

      it('retries all errors when retryAllErrors is true', async () => {
        const config: RetryConfig = {
          ...defaultConfig,
          retryAllErrors: true,
        }

        let retries = 0
        const result = await retryWithBackoff(async () => {
          retries++
          if (retries <= 2) throw new OtherError('some error')
          return 'success'
        }, config)

        expect(result).to.be.equal('success')
        expect(retries).to.be.equal(3)
      })

      it('retries all errors when retryAllErrors is undefined', async () => {
        const config: RetryConfig = {
          ...defaultConfig,
          retryAllErrors: undefined,
        }

        let retries = 0
        const result = await retryWithBackoff(async () => {
          retries++
          if (retries <= 2) throw new OtherError('some error')
          return 'success'
        }, config)

        expect(result).to.be.equal('success')
        expect(retries).to.be.equal(3)
      })

      it('only retries errors in retryableErrors when retryAllErrors is false', async () => {
        const config: RetryConfig = {
          ...defaultConfig,
          retryAllErrors: false,
          retryableErrors: ['RetryableError'],
        }

        let retries = 0
        const result = await retryWithBackoff(async () => {
          retries++
          if (retries <= 2) throw new RetryableError('retryable error')
          return 'success'
        }, config)

        expect(result).to.be.equal('success')
        expect(retries).to.be.equal(3)
      })

      it('does not retry errors no in retryableErrors when retryAllErrors is false', async () => {
        const config: RetryConfig = {
          ...defaultConfig,
          retryAllErrors: false,
          retryableErrors: ['RetryableError'],
        }

        let retries = 0
        const returnedValuePromise = retryWithBackoff(async () => {
          retries++
          throw new OtherError('non-retryable error')
        }, config)

        await expect(returnedValuePromise).to.eventually.be.rejectedWith('non-retryable error')
        expect(retries).to.be.equal(1)
      })

      it('never retries errors in nonRetryableErrors regardless of other settings', async () => {
        const config: RetryConfig = {
          ...defaultConfig,
          retryAllErrors: true,
          nonRetryableErrors: ['NonRetryableError'],
        }

        let retries = 0
        const returnedValuePromise = retryWithBackoff(async () => {
          retries++
          throw new NonRetryableError('non-retryable error')
        }, config)

        await expect(returnedValuePromise).to.eventually.be.rejectedWith('non-retryable error')
        expect(retries).to.be.equal(1)
      })

      it('nonRetryableErrors take precedence over retryableErrors', async () => {
        const config: RetryConfig = {
          ...defaultConfig,
          retryAllErrors: false,
          retryableErrors: ['RetryableError', 'NonRetryableError'],
          nonRetryableErrors: ['NonRetryableError'],
        }

        let retries = 0
        const returnedValuePromise = retryWithBackoff(async () => {
          retries++
          if (retries <= 2) throw new NonRetryableError('non-retryable error')
          return 'success'
        }, config)

        await expect(returnedValuePromise).to.eventually.be.rejectedWith('non-retryable error')
        expect(retries).to.be.equal(1)
      })

      it('logs when skipping retry due to error type', async () => {
        const logger = {
          debug: fake(),
          error: fake(),
        }

        const config: RetryConfig = {
          ...defaultConfig,
          retryAllErrors: false,
          retryableErrors: ['RetryableError'],
        }

        const returnedValuePromise = retryWithBackoff(
          async () => {
            throw new OtherError('non-retryable error')
          },
          config,
          logger as any
        )

        await expect(returnedValuePromise).to.eventually.be.rejectedWith('non-retryable error')
        expect(logger.debug).to.have.been.calledWith(
          '[retryWithBackoff] Error OtherError is not retryable, failing immediately'
        )
      })
    })
  })
})
