import { expect } from './expect'
import {
  Logger,
  InvalidParameterError,
  UUID,
  ReadModelRequestEnvelope,
  GraphQLOperation,
  NotFoundError,
  NotAuthorizedError,
  ProviderLibrary,
  SubscriptionEnvelope,
  FilterFor,
  UserEnvelope,
} from '@boostercloud/framework-types'
import { restore, fake, match, spy } from 'sinon'
import { BoosterReadModelsReader } from '../src/booster-read-models-reader'
import { Booster } from '../src/booster'
import { random, internet } from 'faker'

const logger: Logger = {
  debug() {},
  info() {},
  error() {},
}

describe('BoosterReadModelReader', () => {
  afterEach(() => {
    restore()
  })

  class TestReadModel {
    public id: UUID = ''
  }
  class UserRole {}

  let readModelReader: BoosterReadModelsReader
  Booster.configureCurrentEnv((config) => {
    config.provider = {} as unknown as ProviderLibrary
    config.readModels[TestReadModel.name] = {
      class: TestReadModel,
      authorizedRoles: [UserRole],
      properties: [],
      before: [],
    }
    readModelReader = new BoosterReadModelsReader(config, logger)
  })

  const noopGraphQLOperation: GraphQLOperation = {
    query: '',
  }

  describe('the validation for methods `fetch` and `subscribe`', () => {
    it('throws the right error when request is missing "version"', async () => {
      const envelope = {
        typeName: 'anyReadModel',
        requestID: random.uuid(),
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
      } as any // To avoid the compilation failure of "missing version field"

      await expect(readModelReader.search(envelope)).to.eventually.be.rejectedWith(InvalidParameterError)
      await expect(
        readModelReader.subscribe(envelope.requestID, envelope, noopGraphQLOperation)
      ).to.eventually.be.rejectedWith(InvalidParameterError)
    })

    it('throws the right error when the read model does not exist', async () => {
      const envelope: ReadModelRequestEnvelope = {
        typeName: 'nonExistentReadModel',
        filters: {},
        requestID: random.uuid(),
        version: 1,
      }
      await expect(readModelReader.search(envelope)).to.eventually.be.rejectedWith(NotFoundError)
      await expect(
        readModelReader.subscribe(envelope.requestID.toString(), envelope, noopGraphQLOperation)
      ).to.eventually.be.rejectedWith(NotFoundError)
    })

    it('throws the right error when the user is not authorized', async () => {
      const envelope: ReadModelRequestEnvelope = {
        typeName: TestReadModel.name,
        requestID: random.uuid(),
        filters: {},
        version: 1,
        currentUser: {
          username: internet.email(),
          role: '',
          claims: {},
        },
      }
      await expect(readModelReader.search(envelope)).to.eventually.be.rejectedWith(NotAuthorizedError)
      await expect(
        readModelReader.subscribe(envelope.requestID.toString(), envelope, noopGraphQLOperation)
      ).to.eventually.be.rejectedWith(NotAuthorizedError)
    })
  })

  describe("The logic of 'fetch' and 'subscribe'  methods", () => {
    const filters = {
      id: {
        operation: 'eq',
        values: [random.alphaNumeric(5)],
      },
      field: {
        operation: 'lt',
        values: [random.number(10)],
      },
    }

    const currentUser = {
      username: internet.email(),
      role: UserRole.name,
      claims: {},
    }

    const envelope: ReadModelRequestEnvelope = {
      typeName: TestReadModel.name,
      requestID: random.uuid(),
      version: 1,
      filters,
      currentUser,
    }

    const beforeFn = (filter: FilterFor<TestReadModel>, currentUser?: UserEnvelope): FilterFor<TestReadModel> => {
      return { id: { eq: filter.id } } as FilterFor<TestReadModel>
    }

    const beforeFnV2 = (filter: FilterFor<TestReadModel>, currentUser?: UserEnvelope): FilterFor<TestReadModel> => {
      return { id: { eq: currentUser?.username } } as FilterFor<TestReadModel>
    }

    context('the "fetch" method', () => {
      it('calls the provider search function and returns its results', async () => {
        const expectedReadModels = [new TestReadModel(), new TestReadModel()]
        const providerSearcherFunctionFake = fake.returns(expectedReadModels)
        Booster.configureCurrentEnv((config) => {
          config.provider.readModels = {
            search: providerSearcherFunctionFake,
          } as any
        })

        const result = await readModelReader.search(envelope)

        expect(providerSearcherFunctionFake).to.have.been.calledOnceWithExactly(
          match.any,
          match.any,
          TestReadModel.name,
          filters,
          undefined,
          undefined,
          false
        )
        expect(result).to.be.deep.equal(expectedReadModels)
      })

      it('calls the before hook function when there is only one', async () => {
        const providerSearcherFunctionFake = fake.returns([])

        const beforeFnSpy = spy(beforeFn)

        Booster.configureCurrentEnv((config) => {
          config.readModels[TestReadModel.name] = {
            ...config.readModels[TestReadModel.name],
            before: [beforeFnSpy],
          }
          config.provider.readModels = {
            search: providerSearcherFunctionFake,
          } as any
        })

        await readModelReader.search(envelope)

        expect(beforeFnSpy).to.have.returned({ id: { eq: filters.id } })
        expect(beforeFnSpy).to.have.been.calledOnceWithExactly(filters, currentUser)
      })

      it('chains the before hook functions when there is more than one', async () => {
        const providerSearcherFunctionFake = fake.returns([])

        const beforeFnSpy = spy(beforeFn)
        const beforeFnV2Spy = spy(beforeFnV2)

        Booster.configureCurrentEnv((config) => {
          config.readModels[TestReadModel.name] = {
            ...config.readModels[TestReadModel.name],
            before: [beforeFnSpy, beforeFnV2Spy],
          }
          config.provider.readModels = {
            search: providerSearcherFunctionFake,
          } as any
        })

        await readModelReader.search(envelope)

        expect(beforeFnV2Spy).to.have.been.calledAfter(beforeFnSpy)
        expect(beforeFnSpy).to.have.returned({ id: { eq: filters.id } })
        expect(beforeFnSpy).to.have.been.calledOnceWithExactly(filters, currentUser)
        expect(beforeFnV2Spy).to.have.returned({ id: { eq: currentUser.username } })
        expect(beforeFnV2Spy).to.have.been.calledOnceWithExactly(beforeFnSpy.returnValues[0], currentUser)
      })
    })

    context('the "subscribe" method', () => {
      it('calls the provider subscribe function and returns its results', async () => {
        const providerSubscribeFunctionFake = fake()
        Booster.configureCurrentEnv((config) => {
          config.provider.readModels = {
            subscribe: providerSubscribeFunctionFake,
          } as any
          config.readModels[TestReadModel.name] = {
            ...config.readModels[TestReadModel.name],
            before: [],
          }
        })
        const connectionID = random.uuid()
        const expectedSubscriptionEnvelope: SubscriptionEnvelope = {
          ...envelope,
          connectionID,
          operation: noopGraphQLOperation,
          expirationTime: 1,
        }

        await readModelReader.subscribe(connectionID, envelope, noopGraphQLOperation)

        expect(providerSubscribeFunctionFake).to.have.been.calledOnce
        const gotSubscriptionEnvelope = providerSubscribeFunctionFake.getCall(0).lastArg
        expect(gotSubscriptionEnvelope).to.include.keys('expirationTime')
        gotSubscriptionEnvelope.expirationTime = expectedSubscriptionEnvelope.expirationTime // We don't care now about the value
        expect(gotSubscriptionEnvelope).to.be.deep.equal(expectedSubscriptionEnvelope)
      })

      it('calls the provider subscribe function when setting before hooks and returns the new filter in the result', async () => {
        const providerSubscribeFunctionFake = fake()
        Booster.configureCurrentEnv((config) => {
          config.provider.readModels = {
            subscribe: providerSubscribeFunctionFake,
          } as any
          config.readModels[TestReadModel.name] = {
            ...config.readModels[TestReadModel.name],
            before: [beforeFn, beforeFnV2],
          }
        })
        const connectionID = random.uuid()
        envelope.filters = { id: { eq: currentUser?.username } } as Record<string, FilterFor<unknown>>
        const expectedSubscriptionEnvelope: SubscriptionEnvelope = {
          ...envelope,
          connectionID,
          operation: noopGraphQLOperation,
          expirationTime: 1,
        }

        await readModelReader.subscribe(connectionID, envelope, noopGraphQLOperation)

        expect(providerSubscribeFunctionFake).to.have.been.calledOnce
        const gotSubscriptionEnvelope = providerSubscribeFunctionFake.getCall(0).lastArg
        expect(gotSubscriptionEnvelope).to.include.keys('expirationTime')
        gotSubscriptionEnvelope.expirationTime = expectedSubscriptionEnvelope.expirationTime // We don't care now about the value
        expect(gotSubscriptionEnvelope).to.be.deep.equal(expectedSubscriptionEnvelope)
      })
    })
  })

  describe("The 'unsubscribe' method", () => {
    it('calls the provider "deleteSubscription" method with the right data', async () => {
      const deleteSubscriptionFake = fake()
      Booster.configureCurrentEnv((config) => {
        config.provider.readModels = {
          deleteSubscription: deleteSubscriptionFake,
        } as any
      })
      const connectionID = random.uuid()
      const subscriptionID = random.uuid()
      await readModelReader.unsubscribe(connectionID, subscriptionID)

      expect(deleteSubscriptionFake).to.have.been.calledOnceWithExactly(
        match.any,
        match.any,
        connectionID,
        subscriptionID
      )
    })
  })

  describe("The 'unsubscribeAll' method", () => {
    it('calls the provider "deleteAllSubscription" method with the right data', async () => {
      const deleteAllSubscriptionsFake = fake()
      Booster.configureCurrentEnv((config) => {
        config.provider.readModels = {
          deleteAllSubscriptions: deleteAllSubscriptionsFake,
        } as any
      })
      const connectionID = random.uuid()
      await readModelReader.unsubscribeAll(connectionID)

      expect(deleteAllSubscriptionsFake).to.have.been.calledOnceWithExactly(match.any, match.any, connectionID)
    })
  })
})
