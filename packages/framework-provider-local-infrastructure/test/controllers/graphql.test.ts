import { mockRes, mockReq } from 'sinon-express-mock'
import { restore, SinonStub, SinonStubbedInstance, createStubInstance, stub, replace } from 'sinon'
import { GraphQLService } from '@boostercloud/framework-provider-local'
import { GraphQLController } from '../../src/controllers/graphql'
import { Request, Response } from 'express'
import { expect } from '../expect'
import { lorem } from 'faker'

describe('GraphQL controller', () => {
  let mockQueryResponse: any

  let graphQLServiceStub: SinonStubbedInstance<GraphQLService>
  let queryStub: SinonStub
  let nextStub: SinonStub

  let sut: GraphQLController
  beforeEach(() => {
    mockQueryResponse = {
      status: 'success',
      result: {
        data: {
          ChangeCartItem: true,
        },
      },
    }

    graphQLServiceStub = createStubInstance(GraphQLService)
    queryStub = stub().resolves(mockQueryResponse)
    nextStub = stub()

    replace(graphQLServiceStub, 'handleGraphQLRequest', queryStub as any)

    sut = new GraphQLController(graphQLServiceStub)
  })

  afterEach(() => {
    restore()
  })

  describe('handleGraphQL', () => {
    let mockRequest: Request
    let mockResponse: Response

    let jsonStub: SinonStub

    beforeEach(() => {
      mockRequest = mockReq({})
      mockResponse = mockRes()

      jsonStub = stub()

      replace(mockResponse, 'json', jsonStub)
    })

    it('should call GraphQLService.handleGraphQLRequest', async () => {
      await sut.handleGraphQL(mockRequest, mockResponse, nextStub)

      expect(queryStub).to.have.been.calledOnce.and.calledWith(mockRequest)
    })

    context('on success', () => {
      beforeEach(async () => {
        await sut.handleGraphQL(mockRequest, mockResponse, nextStub)
      })

      it('should not call next', () => {
        expect(nextStub).not.to.be.called
      })

      it('should return expected status code', async () => {
        expect(mockResponse.status).to.be.calledOnceWith(200)
      })

      it('should call response.json with expected arguments', () => {
        expect(jsonStub).to.be.calledOnceWith({ ...mockQueryResponse.result })
      })
    })

    context('on failure', () => {
      let error: Error

      beforeEach(async () => {
        error = new Error(lorem.words())

        queryStub.rejects(error)

        await sut.handleGraphQL(mockRequest, mockResponse, nextStub)
      })

      it('should return expected status code', async () => {
        expect(mockResponse.status).to.be.calledOnceWith(500)
      })

      it('should call response.json with expected arguments', () => {
        expect(jsonStub).to.be.calledOnceWith({ title: 'Error', reason: error.message })
      })

      it('should call next', () => {
        expect(nextStub).to.have.been.calledOnceWith(error)
      })
    })
  })
})
