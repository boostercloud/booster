import { describe } from 'mocha'
import { expect } from 'chai'
import * as chai from 'chai'
import {
  ProviderLibrary,
  BoosterConfig,
  Logger,
  InvalidParameterError,
  NotFoundError,
  NotAuthorizedError,
  UUID,
  ReadModelRequestEnvelope,
  ReadModelInterface,
} from '@boostercloud/framework-types'
import { replace, fake, restore } from 'sinon'
import { BoosterReadModelFetcher } from '../src/booster-read-model-fetcher'

chai.use(require('sinon-chai'))
chai.use(require('chai-as-promised'))

const logger: Logger = {
  debug() {},
  info() {},
  error() {},
}

describe('BoosterReadModelFetcher', () => {
  const fakeEnvironment = () => ({ provider: {} as any })
  afterEach(() => {
    restore()
  })

  class TestReadModel {
    public id: UUID = ''
  }
  class UserRole {}

  const config = new BoosterConfig()
  config.selectedEnvironment = 'production'
  config.environments = {
    production: {
      provider: ({
        rawReadModelRequestToEnvelope: () => {},
        handleReadModelError: () => {},
        fetchAllReadModels: () => {},
        fetchReadModel: () => {},
        handleReadModelResult: () => {},
      } as unknown) as ProviderLibrary,
    },
    development: fakeEnvironment(),
  }
  config.readModels[TestReadModel.name] = {
    class: TestReadModel,
    authorizedRoles: [UserRole],
  }

  describe('the method `fetch`', () => {
    it('passes the right error to the provider when request is missing "version"', async () => {
      const rawMessage = {}
      const envelope = {
        typeName: 'anyReadModel',
        requestID: '123',
      }
      replace(
        config.environments[config.selectedEnvironment].provider,
        'rawReadModelRequestToEnvelope',
        fake.returns(envelope)
      )
      const providerHandleError = fake()
      replace(config.environments[config.selectedEnvironment].provider, 'handleReadModelError', providerHandleError)

      await BoosterReadModelFetcher.fetch(rawMessage, config, logger)
      expect(providerHandleError).to.have.been.calledOnce
      expect(providerHandleError.getCall(0).lastArg).to.be.an.instanceOf(InvalidParameterError)
    })

    it('passes the right error to the provider when the read model does not exist', async () => {
      const rawMessage = {}
      const envelope: ReadModelRequestEnvelope = {
        typeName: 'nonExistentReadModel',
        requestID: '123',
        version: 1,
      }
      replace(
        config.environments[config.selectedEnvironment].provider,
        'rawReadModelRequestToEnvelope',
        fake.returns(envelope)
      )
      const providerHandleError = fake()
      replace(config.environments[config.selectedEnvironment].provider, 'handleReadModelError', providerHandleError)

      await BoosterReadModelFetcher.fetch(rawMessage, config, logger)
      expect(providerHandleError).to.have.been.calledOnce
      expect(providerHandleError.getCall(0).lastArg).to.be.an.instanceOf(NotFoundError)
    })

    it('passes the right error when the user is not authorized', async () => {
      const rawMessage = {}
      const envelope: ReadModelRequestEnvelope = {
        typeName: TestReadModel.name,
        requestID: '123',
        version: 1,
        currentUser: {
          email: 'test@user.com',
          roles: [],
        },
      }
      replace(
        config.environments[config.selectedEnvironment].provider,
        'rawReadModelRequestToEnvelope',
        fake.returns(envelope)
      )
      const providerHandleError = fake()
      replace(config.environments[config.selectedEnvironment].provider, 'handleReadModelError', providerHandleError)

      await BoosterReadModelFetcher.fetch(rawMessage, config, logger)
      expect(providerHandleError).to.have.been.calledOnce
      expect(providerHandleError.getCall(0).lastArg).to.be.an.instanceOf(NotAuthorizedError)
    })

    it('calls the provider method to get all read models when no ID is provided', async () => {
      const rawMessage = {}
      const envelope: ReadModelRequestEnvelope = {
        typeName: TestReadModel.name,
        requestID: '123',
        version: 1,
        currentUser: {
          email: 'test@user.com',
          roles: ['UserRole'],
        },
      }
      const returnedReadModels: Array<ReadModelInterface> = [{ id: '123' }, { id: '456' }]
      replace(
        config.environments[config.selectedEnvironment].provider,
        'rawReadModelRequestToEnvelope',
        fake.returns(envelope)
      )
      const providerFetchAll = fake.returns(returnedReadModels)
      const providerHandleResult = fake()
      replace(config.environments[config.selectedEnvironment].provider, 'fetchAllReadModels', providerFetchAll)
      replace(config.environments[config.selectedEnvironment].provider, 'handleReadModelResult', providerHandleResult)

      await BoosterReadModelFetcher.fetch(rawMessage, config, logger)
      expect(providerFetchAll).to.have.been.calledOnce
      expect(providerHandleResult).to.have.been.calledWith(returnedReadModels)
    })

    it('calls the provider method to get one read model when ID is provided', async () => {
      const rawMessage = {}
      const envelope: ReadModelRequestEnvelope = {
        typeName: TestReadModel.name,
        readModelID: '456',
        requestID: '123',
        version: 1,
        currentUser: {
          email: 'test@user.com',
          roles: ['UserRole'],
        },
      }
      const returnedReadModel: ReadModelInterface = { id: '123' }
      replace(
        config.environments[config.selectedEnvironment].provider,
        'rawReadModelRequestToEnvelope',
        fake.returns(envelope)
      )
      const providerFetchOne = fake.returns(returnedReadModel)
      const providerHandleResult = fake()
      replace(config.environments[config.selectedEnvironment].provider, 'fetchReadModel', providerFetchOne)
      replace(config.environments[config.selectedEnvironment].provider, 'handleReadModelResult', providerHandleResult)

      await BoosterReadModelFetcher.fetch(rawMessage, config, logger)
      expect(providerFetchOne).to.have.been.calledOnce
      expect(providerHandleResult).to.have.been.calledWith(returnedReadModel)
    })
  })
})
