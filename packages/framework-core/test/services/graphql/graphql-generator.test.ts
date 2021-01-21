import { replace, restore, SinonStub, stub } from 'sinon'
import { BoosterCommandDispatcher } from '../../../src/booster-command-dispatcher'
import { BoosterReadModelDispatcher } from '../../../src/booster-read-model-dispatcher'
import { GraphQLGenerator } from '../../../src/services/graphql/graphql-generator'
import { BoosterConfig, ReadModelInterface, Logger, Level } from '@boostercloud/framework-types/dist'
import { expect } from '../../expect'
import { GraphQLQueryGenerator } from '../../../src/services/graphql/graphql-query-generator'
import { GraphQLMutationGenerator } from '../../../src/services/graphql/graphql-mutation-generator'
import { GraphQLSubscriptionGenerator } from '../../../src/services/graphql/graphql-subcriptions-generator'
import { random, internet, lorem } from 'faker'
import { buildLogger } from '../../../src/booster-logger';

describe('GraphQL generator', () => {
  let mockEnvironmentName: string
  let mockConfig: BoosterConfig
  let mockLogger: Logger
  let sut: GraphQLGenerator

  beforeEach(() => {
    mockEnvironmentName = random.alphaNumeric(10)
    mockConfig = new BoosterConfig(mockEnvironmentName)
    mockLogger = buildLogger(Level.error)
    sut = GraphQLGenerator.build(mockConfig, mockLogger)
  })

  afterEach(() => {
    restore()
  })

  describe('build', () => {
    
    it('should create the instance', () => {
      const instance = GraphQLGenerator.build(mockConfig, mockLogger)

      expect(instance).to.not.be.undefined
      expect(instance).to.be.an.instanceOf(GraphQLGenerator)
    })

    it('should return always the same instance', () => {
      const instance1 = GraphQLGenerator.build(mockConfig, mockLogger)
      const instance2 = GraphQLGenerator.build(mockConfig, mockLogger)

      expect(instance1).to.not.be.undefined
      expect(instance1).to.be.an.instanceOf(GraphQLGenerator)
      expect(instance2).to.not.be.undefined
      expect(instance2).to.be.an.instanceOf(GraphQLGenerator)
      expect(instance1).to.be.eq(instance2)
    })
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
    let mockFetchResult: Array<ReadModelInterface>
    let mockResolverContext: any
    let mockResolverInfo: any

    beforeEach(() => {
      mockType = random.arrayElement([Boolean, String, Number])
      mockRequestId = random.uuid()
      mockEmail = internet.email()
      mockRole = random.alphaNumeric(10)
      mockFetchResult = []

      for (let i = 0; i < random.number({ min: 1, max: 10 }); i++) {
        mockFetchResult.push({
          id: random.uuid(),
          testKey: random.number(),
        })
      }

      mockResolverContext = {
        requestID: mockRequestId,
        user: {
          email: mockEmail,
          role: mockRole,
        },
      }
      mockResolverInfo = {}
    })

    describe('readModelResolverBuilder', () => {
      let fetchStub: SinonStub

      let returnedFunction: Function

      beforeEach(() => {
        fetchStub = stub().resolves(mockFetchResult)
        replace(BoosterReadModelDispatcher.prototype,'fetch',fetchStub)
        sut = GraphQLGenerator.build(mockConfig, mockLogger)

        returnedFunction = sut.readModelResolverBuilder(mockType)
      })

      it('should call fetch with expected payload', async () => {
        const expectedFetchPayload = {
          currentUser: {
            email: mockEmail,
            role: mockRole,
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

        expect(result).to.be.deep.equal(mockFetchResult)
      })
    })

    describe('readModelByIDResolverBuilder', () => {
      let mockReadModels: Array<ReadModelInterface>

      let readModelResolverBuilderStub: SinonStub

      let returnedFunction: Function

      beforeEach(() => {
        mockReadModels = []

        for (let i = 0; i < random.number({ min: 1, max: 10 }); i++) {
          mockReadModels.push({
            id: random.uuid(),
            testKey: random.number(),
          })
        }

        readModelResolverBuilderStub = stub().returns(() => {
          return mockReadModels
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

        expect(result).to.be.equal(mockReadModels[0])
      })
    })

    describe('commandResolverBuilder', () => {
      let mockInput: any

      let dispatchCommandStub: SinonStub

      let returnedFunction: Function

      beforeEach(() => {
        mockInput = {
          testObjectKey: random.alphaNumeric(10),
        }

        dispatchCommandStub = stub()
        replace(BoosterCommandDispatcher.prototype,'dispatchCommand', dispatchCommandStub)
        sut = GraphQLGenerator.build(mockConfig, mockLogger)

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
            role: mockRole,
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

        replace(BoosterReadModelDispatcher.prototype,'subscribe', subscribeStub)
        sut = GraphQLGenerator.build(mockConfig, mockLogger)

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
            role: mockRole,
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
