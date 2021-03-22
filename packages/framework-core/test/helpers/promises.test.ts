import { Promises, PromisesError } from '../../src/helpers/promises'
import { expect } from '../expect'

describe('the `Promises` helpers', () => {
  describe('the `allSettledAndFulfilled` method', () => {
    it('Does not throw if all promises are fulfilled', async () => {
      const promises = [Promise.resolve(), Promise.resolve(), Promise.resolve()]

      await Promises.allSettledAndFulfilled(promises)
    })

    it('throws with an array of rejected promises', async () => {
      const rejectedReason1 = 'rejection 1'
      const rejectedReason2 = 'rejection 2'
      const promises = [
        Promise.resolve(),
        Promise.reject(rejectedReason1),
        Promise.reject(rejectedReason2),
        Promise.resolve(),
      ]

      await expect(Promises.allSettledAndFulfilled(promises)).to.be.rejectedWith(
        PromisesError,
        new RegExp(`.*${rejectedReason1}.*${rejectedReason2}.*`)
      )
    })

    it('it waits for all the promises to finish, even if one of them throws early', async () => {
      const rejectedReason = 'rejected'
      let successfulPromise1Finished = false
      let successfulPromise2Finished = false
      const promises = [
        Promise.reject(rejectedReason),
        new Promise<void>((resolve) =>
          setTimeout(() => {
            successfulPromise1Finished = true
            resolve()
          }, 100)
        ),
        new Promise<void>((resolve) =>
          setTimeout(() => {
            successfulPromise2Finished = true
            resolve()
          }, 500)
        ),
      ]

      await expect(Promises.allSettledAndFulfilled(promises)).to.be.rejectedWith(
        PromisesError,
        new RegExp(rejectedReason)
      )
      expect(successfulPromise1Finished).to.be.true
      expect(successfulPromise2Finished).to.be.true
    })
  })
})
