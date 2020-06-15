import { random } from 'faker'
import { s3BucketExists } from '../../src/infrastructure/s3utils'
import { SinonStub, stub } from 'sinon'
import { expect } from '../expect'

describe('s3 utils', () => {
  let s3: any

  describe('s3BucketExists', () => {
    let bucketName: string

    let promiseStub: SinonStub
    let headBucketStub: SinonStub

    beforeEach(() => {
      bucketName = random.alphaNumeric(10)

      promiseStub = stub()
      headBucketStub = stub()
    })

    context('bucket found', () => {
      beforeEach(() => {
        promiseStub.resolves()

        headBucketStub.returns({
          promise: promiseStub,
        })

        s3 = {
          headBucket: headBucketStub,
        }
      })

      it('should return true', async () => {
        const result = await s3BucketExists(bucketName, s3)

        expect(headBucketStub).to.have.been.calledOnceWith({ Bucket: bucketName })
        expect(result).to.be.true
      })
    })

    context('an error is thrown', () => {
      beforeEach(() => {
        promiseStub.rejects()

        headBucketStub.returns({
          promise: promiseStub,
        })

        s3 = {
          headBucket: headBucketStub,
        }
      })

      it('should return false', async () => {
        const result = await s3BucketExists(bucketName, s3)

        expect(headBucketStub).to.have.been.calledOnceWith({ Bucket: bucketName })
        expect(result).to.be.false
      })
    })
  })
})
