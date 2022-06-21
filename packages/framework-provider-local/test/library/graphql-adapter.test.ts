/* eslint-disable @typescript-eslint/no-explicit-any */
import { mockReq } from 'sinon-express-mock'
import { SinonStub, stub, replace, restore, fake } from 'sinon'
import { rawGraphQLRequestToEnvelope } from '../../src/library/graphql-adapter'
import { expect } from '../expect'
import { BoosterConfig, UUID } from '@boostercloud/framework-types'
import { random } from 'faker'
import { Request } from 'express'

describe('Local provider graphql-adapter', () => {
  describe('rawGraphQLRequestToEnvelope', () => {
    let mockUuid: string
    let mockBody: any
    let mockRequest: Request
    let mockUserToken: string
    const mockConfig = new BoosterConfig('test')
    mockConfig.logger = {
      debug: fake(),
      info: fake(),
      warn: fake(),
      error: fake(),
    }

    let generateStub: SinonStub

    beforeEach(() => {
      mockUuid = random.uuid()
      mockUserToken = random.uuid()
      mockBody = {
        query: '',
        variables: {},
      }
      mockRequest = mockReq()
      mockRequest.body = mockBody
      mockRequest.headers = {
        authorization: mockUserToken,
      }

      generateStub = stub().returns(mockUuid)

      replace(UUID, 'generate', generateStub)
    })

    afterEach(() => {
      restore()
    })

    it('should call logger.debug', async () => {
      await rawGraphQLRequestToEnvelope(mockConfig, mockRequest)

      expect(mockConfig.logger?.debug).to.have.been.calledOnceWith(
        '[Booster]|graphql-adapter#rawGraphQLRequestToEnvelope: ',
        'Received GraphQL request: \n- Headers: ',
        mockRequest.headers,
        '\n- Body: ',
        mockRequest.body
      )
    })

    it('should generate expected envelop', async () => {
      const result = await rawGraphQLRequestToEnvelope(mockConfig, mockRequest)
      expect(result).to.be.deep.equal({
        requestID: mockUuid,
        eventType: 'MESSAGE',
        connectionID: undefined,
        token: mockUserToken,
        value: mockBody,
        context: {
          request: {
            headers: mockRequest.headers,
            body: mockRequest.body,
          },
          rawContext: mockRequest,
        },
      })
    })
  })
})
