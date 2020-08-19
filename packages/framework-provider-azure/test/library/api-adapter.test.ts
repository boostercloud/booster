import { expect } from '../expect'
import {
  InvalidVersionError,
  InvalidParameterError,
  NotAuthorizedError,
  NotFoundError,
} from '@boostercloud/framework-types'
import { requestFailed } from '../../src/library/api-adapter'

describe('API adapter', () => {
  describe('The "requestFailed" method', () => {
    interface TestCase {
      input: Error
      expectedOutput: {
        status: number
        title: string
      }
    }

    const testCases: Array<TestCase> = [
      {
        input: new InvalidParameterError('error message'),
        expectedOutput: {
          status: 400,
          title: 'Invalid Parameter Error',
        },
      },
      {
        input: new NotAuthorizedError('error message'),
        expectedOutput: {
          status: 401,
          title: 'Not Authorized Error',
        },
      },
      {
        input: new NotFoundError('error message'),
        expectedOutput: {
          status: 404,
          title: 'Not Found Error',
        },
      },
      {
        input: new InvalidVersionError('error message'),
        expectedOutput: {
          status: 422,
          title: 'Invalid Version Error',
        },
      },
    ]

    for (const testCase of testCases) {
      it(`returns the proper body for error '${testCase.input.constructor.name}'`, async () => {
        const got = await requestFailed(testCase.input)
        expect(got.status).to.be.equal(testCase.expectedOutput.status)
        const body = JSON.parse(got.body)
        expect(body.title).to.be.equal(testCase.expectedOutput.title)
      })
    }
  })
})
