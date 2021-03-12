import { describe } from 'mocha'
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
} from '@boostercloud/framework-types'
import { restore, fake, match } from 'sinon'
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

  let readModelDispatcher: BoosterReadModelsReader
  Booster.configureCurrentEnv((config) => {
    config.provider = ({} as unknown) as ProviderLibrary
    config.readModels[TestReadModel.name] = {
      class: TestReadModel,
      authorizedRoles: [UserRole],
      properties: [],
    }
    readModelDispatcher = new BoosterReadModelsReader(config, logger)
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

      await expect(readModelDispatcher.fetch(envelope)).to.eventually.be.rejectedWith(InvalidParameterError)
      await expect(
        readModelDispatcher.subscribe(envelope.requestID, envelope, noopGraphQLOperation)
      ).to.eventually.be.rejectedWith(InvalidParameterError)
    })

    it('throws the right error when the read model does not exist', async () => {
      const envelope: ReadModelRequestEnvelope = {
        typeName: 'nonExistentReadModel',
        requestID: random.uuid(),
        version: 1,
      }
      await expect(readModelDispatcher.fetch(envelope)).to.eventually.be.rejectedWith(NotFoundError)
      await expect(
        readModelDispatcher.subscribe(envelope.requestID.toString(), envelope, noopGraphQLOperation)
      ).to.eventually.be.rejectedWith(NotFoundError)
    })

    it('throws the right error when the user is not authorized', async () => {
      const envelope: ReadModelRequestEnvelope = {
        typeName: TestReadModel.name,
        requestID: random.uuid(),
        version: 1,
        currentUser: {
          username: internet.email(),
          role: '',
        },
      }
      await expect(readModelDispatcher.fetch(envelope)).to.eventually.be.rejectedWith(NotAuthorizedError)
      await expect(
        readModelDispatcher.subscribe(envelope.requestID.toString(), envelope, noopGraphQLOperation)
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
    const envelope: ReadModelRequestEnvelope = {
      typeName: TestReadModel.name,
      requestID: random.uuid(),
      version: 1,
      filters,
      currentUser: {
        username: internet.email(),
        role: UserRole.name,
      },
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

        const result = await readModelDispatcher.fetch(envelope)

        expect(providerSearcherFunctionFake).to.have.been.calledOnceWithExactly(
          match.any,
          match.any,
          TestReadModel.name,
          filters
        )
        expect(result).to.be.deep.equal(expectedReadModels)
      })
    })

    context('the "subscribe" method', () => {
      it('calls the provider subscribe function and returns its results', async () => {
        const providerSubscribeFunctionFake = fake()
        Booster.configureCurrentEnv((config) => {
          config.provider.readModels = {
            subscribe: providerSubscribeFunctionFake,
          } as any
        })
        const connectionID = random.uuid()
        const expectedSubscriptionEnvelope: SubscriptionEnvelope = {
          ...envelope,
          connectionID,
          operation: noopGraphQLOperation,
          expirationTime: 1,
        }

        await readModelDispatcher.subscribe(connectionID, envelope, noopGraphQLOperation)

        expect(providerSubscribeFunctionFake).to.have.been.calledOnce
        const gotSubscriptionEnvelope = providerSubscribeFunctionFake.getCall(0).args[2]
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
      await readModelDispatcher.unsubscribe(connectionID, subscriptionID)

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
      await readModelDispatcher.unsubscribeAll(connectionID)

      expect(deleteAllSubscriptionsFake).to.have.been.calledOnceWithExactly(match.any, match.any, connectionID)
    })
  })
})
