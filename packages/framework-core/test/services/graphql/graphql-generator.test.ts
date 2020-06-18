import { SinonStubbedInstance, replace, restore, SinonStub, stub } from 'sinon'
import { BoosterCommandDispatcher } from '../../../src/booster-command-dispatcher'
import sinon = require('sinon')
import { BoosterReadModelDispatcher } from '../../../src/booster-read-model-dispatcher'
import { GraphQLGenerator } from '../../../src/services/graphql/graphql-generator'
import { BoosterConfig } from '@boostercloud/framework-types/dist'
import { expect } from '../../expect'
import { GraphQLQueryGenerator } from '../../../src/services/graphql/graphql-query-generator'
import { GraphQLMutationGenerator } from '../../../src/services/graphql/graphql-mutation-generator'
import { GraphQLSubscriptionGenerator } from '../../../src/services/graphql/graphql-subcriptions-generator'
import { random, internet, lorem } from 'faker'

describe('GraphQL generator', () => {
  let configStub: SinonStubbedInstance<BoosterConfig>
  let commandDispatcherStub: SinonStubbedInstance<BoosterCommandDispatcher>
  let readModelDispatcherStub: SinonStubbedInstance<BoosterReadModelDispatcher>

  let sut: GraphQLGenerator

  beforeEach(() => {
    configStub = sinon.createStubInstance(BoosterConfig)
    commandDispatcherStub = sinon.createStubInstance(BoosterCommandDispatcher)
    readModelDispatcherStub = sinon.createStubInstance(BoosterReadModelDispatcher)

    sut = new GraphQLGenerator(configStub as any, commandDispatcherStub, readModelDispatcherStub as any)
  })

  afterEach(() => {
    restore()
  })

  describe('generateSchema', () => {
    let mockQueryTypeName: string
    let mockMutationTypeName: string
    let mockSubscriptionTypeName: string

    let fakeQueryGenerator: SinonStub
    let fakeMutationGenerator: SinonStub
    let fakeSubscriptionGenerator: SinonStub

    beforeEach(() => {
      mockQueryTypeName = random.alphaNumeric(10)
      mockMutationTypeName = random.alphaNumeric(10)
      mockSubscriptionTypeName = random.alphaNumeric(10)
      fakeQueryGenerator = stub().returns({ name: mockQueryTypeName })
      fakeMutationGenerator = stub().returns({ name: mockMutationTypeName })
      fakeSubscriptionGenerator = stub().returns({ name: mockSubscriptionTypeName })

      replace(GraphQLQueryGenerator.prototype, 'generate', fakeQueryGenerator)
      replace(GraphQLMutationGenerator.prototype, 'generate', fakeMutationGenerator)
      replace(GraphQLSubscriptionGenerator.prototype, 'generate', fakeSubscriptionGenerator)
    })

    it('should call QueryGenerator', () => {
      sut.generateSchema()

      expect(fakeQueryGenerator).to.have.been.calledOnceWithExactly()
    })

    it('should call MutationGenerator', () => {
      sut.generateSchema()

      expect(fakeMutationGenerator).to.have.been.calledOnceWithExactly()
    })

    it('should call SubscriptionGenerator', () => {
      sut.generateSchema()

      expect(fakeSubscriptionGenerator).to.have.been.calledOnceWithExactly()
    })

    it('should return a GraphQL schema', () => {
      const result = sut.generateSchema()

      const expectedTypes = {
        _queryType: {
          name: mockQueryTypeName,
        },
        _mutationType: {
          name: mockMutationTypeName,
        },
        _subscriptionType: {
          name: mockSubscriptionTypeName,
        },
      }

      expect(result).to.deep.contain(expectedTypes)
    })
  })

  describe('builders', () => {
    let mockType: any
    let mockRequestId: string
    let mockEmail: string
    let mockRole: string
    let mockFetchResult: string
    let mockResolverContext: any
    let mockResolverInfo: any

    beforeEach(() => {
      mockType = random.arrayElement([Boolean, String, Number])
      mockRequestId = random.uuid()
      mockEmail = internet.email()
      mockRole = random.alphaNumeric(10)
      mockFetchResult = lorem.word()

      mockResolverContext = {
        requestID: mockRequestId,
        user: {
          email: mockEmail,
          roles: [mockRole],
        },
      }
      mockResolverInfo = {}
    })

    describe('readModelResolverBuilder', () => {
      let fetchStub: SinonStub

      let returnedFunction: Function

      beforeEach(() => {
        fetchStub = stub().returns(mockFetchResult)

        readModelDispatcherStub = sinon.createStubInstance(BoosterReadModelDispatcher)
        replace(readModelDispatcherStub, 'fetch', fetchStub as any)

        sut = new GraphQLGenerator(configStub as any, commandDispatcherStub, readModelDispatcherStub as any)

        returnedFunction = sut.readModelResolverBuilder(mockType)
      })

      it('should call fetch with expected payload', async () => {
        const expectedFetchPayload = {
          currentUser: {
            email: mockEmail,
            roles: [mockRole],
          },
          filters: {},
          requestID: mockRequestId,
          typeName: mockType.name,
          version: 1,
        }

        await returnedFunction('', {}, mockResolverContext, {} as any)

        expect(fetchStub).to.have.been.calledOnceWithExactly(expectedFetchPayload)
      })

      it('should return expected result', async () => {
        const result = await returnedFunction('', {}, mockResolverContext, mockResolverInfo)

        expect(result).to.be.equal(mockFetchResult)
      })
    })

    describe('readModelByIDResolverBuilder', () => {
      let mockResolverBuilderResult0: string
      let mockResolverBuilderResult1: string

      let readModelResolverBuilderStub: SinonStub

      let returnedFunction: Function

      beforeEach(() => {
        mockResolverBuilderResult0 = random.alphaNumeric(10)
        mockResolverBuilderResult1 = random.alphaNumeric(10)

        readModelResolverBuilderStub = stub().returns(() => {
          return [mockResolverBuilderResult0, mockResolverBuilderResult1]
        })
        replace(sut, 'readModelResolverBuilder', readModelResolverBuilderStub)

        returnedFunction = sut.readModelByIDResolverBuilder(mockType)
      })

      it('should call readModelByIDResolverBuilder', async () => {
        await returnedFunction('', {}, mockResolverContext, mockResolverInfo)

        expect(readModelResolverBuilderStub).to.have.been.calledOnce
      })

      it('should return expected result', async () => {
        const result = await returnedFunction('', {}, mockResolverContext, mockResolverInfo)

        expect(result).to.be.equal(mockResolverBuilderResult0)
      })
    })

    describe('commandResolverBuilder', () => {
      let mockInput: string

      let dispatchCommandStub: SinonStub

      let returnedFunction: Function

      beforeEach(() => {
        mockInput = random.alphaNumeric(10)

        dispatchCommandStub = stub()

        readModelDispatcherStub = sinon.createStubInstance(BoosterReadModelDispatcher)
        replace(commandDispatcherStub, 'dispatchCommand', dispatchCommandStub as any)

        sut = new GraphQLGenerator(configStub as any, commandDispatcherStub, readModelDispatcherStub as any)

        returnedFunction = sut.commandResolverBuilder(mockType)
      })

      it('should call dispatchCommand with expected input', async () => {
        await returnedFunction(
          '',
          {
            input: mockInput,
          },
          mockResolverContext,
          mockResolverInfo
        )

        expect(dispatchCommandStub).to.have.been.calledOnceWithExactly({
          requestID: mockRequestId,
          currentUser: {
            email: mockEmail,
            roles: [mockRole],
          },
          typeName: mockType.name,
          value: mockInput,
          version: 1,
        })
      })

      it('should return true', async () => {
        const result = await returnedFunction(
          '',
          {
            input: mockInput,
          },
          mockResolverContext,
          mockResolverInfo
        )

        expect(result).to.be.true
      })
    })

    describe('subscriptionByIDResolverBuilder', () => {
      let mockResolverResult: string

      let subscriptionResolverBuilderStub: SinonStub

      let returnedFunction: Function

      beforeEach(() => {
        mockResolverResult = random.alphaNumeric(10)

        subscriptionResolverBuilderStub = stub().returns(() => {
          return mockResolverResult
        })
        replace(sut, 'subscriptionResolverBuilder', subscriptionResolverBuilderStub)

        returnedFunction = sut.subscriptionByIDResolverBuilder(mockType)
      })

      it('should call subscriptionResolverBuilder', async () => {
        await returnedFunction('', {}, mockResolverContext, mockResolverInfo)

        expect(subscriptionResolverBuilderStub).to.have.been.calledOnce
      })

      it('should return expected result', async () => {
        const result = await returnedFunction('', {}, mockResolverContext, mockResolverInfo)

        expect(result).to.be.equal(mockResolverResult)
      })
    })

    describe('subscriptionResolverBuilder', () => {
      let mockContextConnectionID: string
      let mockContextOperation: string
      let mockAsyncIteratorResult: string

      let subscribeStub: SinonStub
      let asyncIteratorStub: SinonStub

      let returnedFunction: Function

      beforeEach(() => {
        mockContextConnectionID = random.uuid()
        mockContextOperation = random.alphaNumeric(10)
        mockAsyncIteratorResult = lorem.word()

        mockResolverContext.connectionID = mockContextConnectionID
        mockResolverContext.operation = mockContextOperation
        mockResolverContext.pubSub = {
          asyncIterator: (x: any) => asyncIteratorStub(x),
        }

        subscribeStub = stub().resolves()
        asyncIteratorStub = stub().returns(mockAsyncIteratorResult)

        readModelDispatcherStub = sinon.createStubInstance(BoosterReadModelDispatcher)
        replace(readModelDispatcherStub, 'subscribe', subscribeStub as any)

        sut = new GraphQLGenerator(configStub as any, commandDispatcherStub, readModelDispatcherStub as any)

        returnedFunction = sut.subscriptionResolverBuilder(mockType)
      })

      context('missing context.connectionID', () => {
        it('should throw an error', async () => {
          mockResolverContext.connectionID = undefined

          let error: Error = new Error()

          try {
            await returnedFunction('', {}, mockResolverContext, mockResolverInfo)
          } catch (e) {
            error = e
          } finally {
            expect(error.message).to.be.equal('Missing "connectionID". It is required for subscriptions')
          }
        })
      })

      context('storeSubscriptions', () => {
        describe('should storeSubscriptions', () => {
          it('should call readModelsDispatcher.subscribe', async () => {
            mockResolverContext.storeSubscriptions = true

            await returnedFunction('', {}, mockResolverContext, mockResolverInfo)

            expect(subscribeStub).to.be.calledOnce
          })
        })

        describe('should not storeSubscriptions', () => {
          it('should not call readModelsDispatcher.subscribe', async () => {
            mockResolverContext.storeSubscriptions = false

            await returnedFunction('', {}, mockResolverContext, mockResolverInfo)

            expect(subscribeStub).to.not.be.called
          })
        })
      })

      it('should call pubsub.asyncIterator', async () => {
        await returnedFunction('', {}, mockResolverContext, mockResolverInfo)

        expect(asyncIteratorStub).to.be.calledOnceWithExactly({
          currentUser: {
            email: mockEmail,
            roles: [mockRole],
          },
          filters: {},
          requestID: mockRequestId,
          typeName: mockType.name,
          version: 1,
        })
      })

      it('should return expected result', async () => {
        const result = await returnedFunction('', {}, mockResolverContext, mockResolverInfo)

        expect(result).to.be.equal(mockAsyncIteratorResult)
      })
    })
  })
})
