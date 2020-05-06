import { describe } from 'mocha'
import { expect } from 'chai'
import * as chai from 'chai'
import {
  ProviderLibrary,
  BoosterConfig,
  Logger,
  InvalidParameterError,
  UUID,
  ReadModelRequestEnvelope,
  GraphQLOperation,
  NotFoundError,
  NotAuthorizedError,
} from '@boostercloud/framework-types'
import { restore } from 'sinon'
import { BoosterReadModelDispatcher } from '../src/booster-read-model-dispatcher'

chai.use(require('sinon-chai'))
chai.use(require('chai-as-promised'))

const logger: Logger = {
  debug() {},
  info() {},
  error() {},
}

describe('BoosterReadModelDispatcher', () => {
  afterEach(() => {
    restore()
  })

  class TestReadModel {
    public id: UUID = ''
  }
  class UserRole {}

  const config = new BoosterConfig('test')
  config.provider = ({
    rawReadModelRequestToEnvelope: () => {},
    handleReadModelError: () => {},
    fetchAllReadModels: () => {},
    fetchReadModel: () => {},
    handleReadModelResult: () => {},
  } as unknown) as ProviderLibrary
  config.readModels[TestReadModel.name] = {
    class: TestReadModel,
    authorizedRoles: [UserRole],
    properties: [],
  }

  const readModelDispatcher = new BoosterReadModelDispatcher(config, logger)
  const noopGraphQLOperation: GraphQLOperation = {
    query: '',
  }

  describe('the validation for methods `fetch` and `subscribe`', () => {
    it('throws the right error when request is missing "version"', async () => {
      const envelope = {
        typeName: 'anyReadModel',
        requestID: '123',
      } as any // To avoid the compilation failure of "missing version field"

      await expect(readModelDispatcher.fetch(envelope)).to.eventually.be.rejectedWith(InvalidParameterError)
      await expect(readModelDispatcher.subscribe('123', envelope, noopGraphQLOperation)).to.eventually.be.rejectedWith(
        InvalidParameterError
      )
    })

    it('throws the right error when the read model does not exist', async () => {
      const envelope: ReadModelRequestEnvelope = {
        typeName: 'nonExistentReadModel',
        requestID: '123',
        version: 1,
      }
      await expect(readModelDispatcher.fetch(envelope)).to.eventually.be.rejectedWith(NotFoundError)
      await expect(readModelDispatcher.subscribe('123', envelope, noopGraphQLOperation)).to.eventually.be.rejectedWith(
        NotFoundError
      )
    })

    it('throws the right error when the user is not authorized', async () => {
      const envelope: ReadModelRequestEnvelope = {
        typeName: TestReadModel.name,
        requestID: '123',
        version: 1,
        currentUser: {
          email: 'test@user.com',
          roles: [],
        },
      }
      await expect(readModelDispatcher.fetch(envelope)).to.eventually.be.rejectedWith(NotAuthorizedError)
      await expect(readModelDispatcher.subscribe('123', envelope, noopGraphQLOperation)).to.eventually.be.rejectedWith(
        NotAuthorizedError
      )
    })
  })
})
