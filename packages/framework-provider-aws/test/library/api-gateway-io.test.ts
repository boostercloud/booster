import { expect } from '../expect'
import {
  InvalidVersionError,
  InvalidParameterError,
  NotAuthorizedError,
  NotFoundError,
} from '@boostercloud/framework-types'
import { requestFailed } from '../../src/library/api-gateway-io'

describe('the requestFailed method', () => {
  interface TestCase {
    input: Error
    expectedOutput: {
      statusCode: number
      title: string
    }
  }

  it('returns a proper body with several errors', async () => {
    const testCases: Array<TestCase> = [
      {
        input: new InvalidParameterError('error message'),
        expectedOutput: {
          statusCode: 400,
          title: 'Invalid Parameter Error',
        },
      },
      {
        input: new NotAuthorizedError('error message'),
        expectedOutput: {
          statusCode: 401,
          title: 'Not Authorized Error',
        },
      },
      {
        input: new NotFoundError('error message'),
        expectedOutput: {
          statusCode: 404,
          title: 'Not Found Error',
        },
      },
      {
        input: new InvalidVersionError('error message'),
        expectedOutput: {
          statusCode: 422,
          title: 'Invalid Version Error',
        },
      },
    ]

    for (const testCase of testCases) {
      const testDescription = `In test case '${testCase.input.constructor.name}'`
      const got = await requestFailed(testCase.input)
      expect(got.statusCode).to.be.equal(testCase.expectedOutput.statusCode, testDescription)
      const body = JSON.parse(got.body)
      expect(body.title).to.be.equal(testCase.expectedOutput.title, testDescription)
    }
  })
})
