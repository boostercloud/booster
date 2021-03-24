import { retryIfError } from '../../src/helpers/retrier'
import { expect } from '../expect'
import { Logger } from '@boostercloud/framework-types'

const logger: Logger = console

class ErrorToRetry extends Error {}

describe('the `retrier` helpers', () => {
  describe('the `retryIfError` method', () => {
    it('returns the result and does not retry if there is no error', async () => {
      let retries = 0
      const returnedValue = await retryIfError(
        logger,
        async () => {
          retries++
          return 'returned value'
        },
        ErrorToRetry
      )
      expect(returnedValue).to.be.equal('returned value')
      expect(retries).to.be.equal(1)
    })

    it('rethrows a non-expected error and does not retry', async () => {
      let retries = 0
      const returnedValuePromise = retryIfError(
        logger,
        async () => {
          retries++
          throw new Error('unexpected error')
        },
        ErrorToRetry
      )
      await expect(returnedValuePromise).to.eventually.be.rejectedWith('unexpected error')
      expect(retries).to.be.equal(1)
    })

    it('retries 5 times if the expected error happens 4 times', async () => {
      let retries = 0
      const result = await retryIfError(
        logger,
        async () => {
          retries++
          if (retries <= 4) throw new ErrorToRetry('expected error')
          return 'success'
        },
        ErrorToRetry
      )
      expect(result).to.be.equal('success')
      expect(retries).to.be.equal(5)
    })

    it('throws after "maxRetries" is reached with the expected error happening', async () => {
      const maxRetries = 20
      let retries = 0
      const returnedValuePromise = retryIfError(
        logger,
        async () => {
          retries++
          throw new ErrorToRetry('expected error')
        },
        ErrorToRetry,
        maxRetries
      )
      await expect(returnedValuePromise).to.eventually.be.rejectedWith('Reached the maximum number of retries')
      expect(retries).to.be.equal(maxRetries)
    })
  })
})
