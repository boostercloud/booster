/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from './expect'
import {
  Logger,
  InvalidParameterError,
  UUID,
  ReadModelRequestEnvelope,
  GraphQLOperation,
  NotFoundError,
  NotAuthorizedError,
  SubscriptionEnvelope,
  FilterFor,
  UserEnvelope,
  BoosterConfig,
} from '@boostercloud/framework-types'
import { restore, fake, match, spy, replace } from 'sinon'
import { BoosterReadModelsReader } from '../src/booster-read-models-reader'
import { random, internet } from 'faker'
import { BoosterAuth } from '../src/booster-auth'
import * as filterHelpers from '../src/services/filter-helpers'
import { Booster } from '../src/booster'

const logger: Logger = {
  debug() {},
  info() {},
  error() {},
}

describe('BoosterReadModelReader', () => {
  const config = new BoosterConfig('test')
  config.provider = {
    readModels: {
      search: fake(),
      subscribe: fake(),
      deleteSubscription: fake(),
      deleteAllSubscriptions: fake(),
    },
  } as any

  afterEach(() => {
    restore()
  })

  class TestReadModel {
    public id: UUID = '∂'
  }

  class SequencedReadModel {
    public id: UUID = 'π'
  }

  class UserRole {}

  // Why sorting by salmon? Salmons are fun! https://youtu.be/dDj7DuHVV9E
  config.readModelSequenceKeys[SequencedReadModel.name] = 'salmon'

  const readModelReader = new BoosterReadModelsReader(config, logger)

  const noopGraphQLOperation: GraphQLOperation = {
    query: '',
  }

  context('requests by Id', () => {
    beforeEach(() => {
      config.readModels[TestReadModel.name] = {
        class: TestReadModel,
        authorizedRoles: [UserRole],
        properties: [],
        before: [],
      }
      config.readModels[SequencedReadModel.name] = {
        class: SequencedReadModel,
        authorizedRoles: [UserRole],
        properties: [],
        before: [],
      }
    })

    afterEach(() => {
      delete config.readModels[TestReadModel.name]
      delete config.readModels[SequencedReadModel.name]
    })

    describe('the `validateByIdRequest', () => {
      const validateByIdRequest = (readModelReader as any).validateByIdRequest.bind(readModelReader)

      it('throws an invalid parameter error when the version is not present in a request', () => {
        expect(() => {
          validateByIdRequest({})
        }).to.throw('"version" was not present')
      })

      it("throws a not found error when it can't find the read model metadata", () => {
        expect(() => {
          validateByIdRequest({ version: 1, typeName: 'NonexistentReadModel' })
        }).to.throw(/Could not find read model/)
      })

      it('throws a non authorized error when the current user is not allowed to perform the request', () => {
        expect(() => {
          validateByIdRequest({ version: 1, typeName: TestReadModel.name })
        }).to.throw(/Access denied/)
      })

      it('throws an invalid parameter error when the request receives a sequence key but it cannot be found in the Booster metadata', () => {
        replace(BoosterAuth, 'isUserAuthorized', fake.returns(true))
        expect(() => {
          validateByIdRequest({
            version: 1,
            typeName: TestReadModel.name,
            currentUser: { id: '666', username: 'root', role: 'root' },
            sequenceKey: { name: 'salmon', value: 'sammy' },
          })
        }).to.throw(/Could not find a sort key/)
      })

      it('does not throw an error when there is no sequence key and everything else is ok', () => {
        replace(BoosterAuth, 'isUserAuthorized', fake.returns(true))
        expect(() => {
          validateByIdRequest({
            version: 1,
            typeName: TestReadModel.name,
            currentUser: { id: '666', username: 'root', role: 'root' },
          })
        }).not.to.throw()
      })

      it('does not throw an error when there is a valid sequence key and everything else is ok', () => {
        replace(BoosterAuth, 'isUserAuthorized', fake.returns(true))
        expect(() => {
          validateByIdRequest({
            version: 1,
            typeName: SequencedReadModel.name,
            currentUser: { id: '666', username: 'root', role: 'root' },
            sequenceKey: { name: 'salmon', value: 'sammy' },
          })
        }).not.to.throw()
      })
    })

    describe('the `findById` method', () => {
      it('validates and uses the searcher to find a read model by id', async () => {
        const fakeValidateByIdRequest = fake()
        replace(readModelReader as any, 'validateByIdRequest', fakeValidateByIdRequest)
        const fakeSearcher = { findById: fake() }
        const fakeInitializeSearcherWithFilters = fake.returns(fakeSearcher)
        replace(readModelReader as any, 'initializeSearcherWithFilters', fakeInitializeSearcherWithFilters)

        const currentUser = {
          id: 'a user',
        } as any
        const sequenceKey = {
          name: 'salmon',
          value: 'sammy',
        }
        const readModelByIdRequestEnvelope = {
          id: '42',
          typeName: 'SomeReadModel',
          sequenceKey,
          currentUser,
          version: 1,
          requestID: 'my request!',
        }
        await readModelReader.findById(readModelByIdRequestEnvelope)

        expect(fakeValidateByIdRequest).to.have.been.calledOnceWith(readModelByIdRequestEnvelope)
        expect(fakeInitializeSearcherWithFilters).to.have.been.calledOnceWith('SomeReadModel', currentUser)
        expect(fakeSearcher.findById).to.have.been.calledOnceWith('42')
      })
    })
  })

  describe('the validation for methods `search` and `subscribe`', () => {
    beforeEach(() => {
      config.readModels[TestReadModel.name] = {
        class: TestReadModel,
        authorizedRoles: [UserRole],
        properties: [],
        before: [],
      }
    })

    afterEach(() => {
      delete config.readModels[TestReadModel.name]
    })

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

  context("The logic of 'search' and 'subscribe'  methods", () => {
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

    const beforeFn = (filter: FilterFor<TestReadModel>, _currentUser?: UserEnvelope): FilterFor<TestReadModel> => {
      return { id: { eq: filter.id } } as FilterFor<TestReadModel>
    }

    const beforeFnV2 = (filter: FilterFor<TestReadModel>, currentUser?: UserEnvelope): FilterFor<TestReadModel> => {
      return { id: { eq: currentUser?.username } } as FilterFor<TestReadModel>
    }

    describe('the "search" method', () => {
      beforeEach(() => {
        config.readModels[TestReadModel.name] = {
          class: TestReadModel,
          authorizedRoles: [UserRole],
          properties: [],
          before: [],
        }
      })

      afterEach(() => {
        delete config.readModels[TestReadModel.name]
      })

      it('calls the provider search function and returns its results', async () => {
        const expectedReadModels = [new TestReadModel(), new TestReadModel()]
        const providerSearcherFunctionFake = fake.returns(expectedReadModels)
        replace(config.provider.readModels, 'search', providerSearcherFunctionFake)

        replace(Booster, 'config', config) // Needed because the function `Booster.readModel` references `this.config` from `searchFunction`

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

      context('when there is only one before hook function', () => {
        const beforeFnSpy = spy(beforeFn)

        beforeEach(() => {
          const providerSearcherFunctionFake = fake.returns([])

          config.readModels[TestReadModel.name] = {
            class: TestReadModel,
            authorizedRoles: [UserRole],
            properties: [],
            before: [beforeFnSpy],
          }

          replace(Booster, 'config', config) // Needed because the function `Booster.readModel` references `this.config` from `searchFunction`
          replace(config.provider.readModels, 'search', providerSearcherFunctionFake)
        })

        afterEach(() => {
          delete config.readModels[TestReadModel.name]
        })

        it('calls the before hook function', async () => {
          await readModelReader.search(envelope)

          expect(beforeFnSpy).to.have.been.calledOnceWithExactly(filters, currentUser)
          expect(beforeFnSpy).to.have.returned({ id: { eq: filters.id } })
        })
      })

      context('when there are more than one before hook functions', () => {
        const beforeFnSpy = spy(beforeFn)
        const beforeFnV2Spy = spy(beforeFnV2)

        beforeEach(() => {
          const providerSearcherFunctionFake = fake.returns([])

          config.readModels[TestReadModel.name] = {
            class: TestReadModel,
            authorizedRoles: [UserRole],
            properties: [],
            before: [beforeFnSpy, beforeFnV2Spy],
          }

          replace(Booster, 'config', config) // Needed because the function `Booster.readModel` references `this.config` from `searchFunction`

          replace(config.provider.readModels, 'search', providerSearcherFunctionFake)
        })

        afterEach(() => {
          delete config.readModels[TestReadModel.name]
        })

        it('chains the before hook functions when there is more than one', async () => {
          await readModelReader.search(envelope)

          expect(beforeFnV2Spy).to.have.been.calledAfter(beforeFnSpy)
          expect(beforeFnSpy).to.have.returned({ id: { eq: filters.id } })
          expect(beforeFnSpy).to.have.been.calledOnceWithExactly(filters, currentUser)
          expect(beforeFnV2Spy).to.have.returned({ id: { eq: currentUser.username } })
          expect(beforeFnV2Spy).to.have.been.calledOnceWithExactly(beforeFnSpy.returnValues[0], currentUser)
        })
      })
    })

    describe('the "subscribe" method', () => {
      context('with no before hooks defined', () => {
        const providerSubscribeFunctionFake = fake()

        beforeEach(() => {
          config.readModels[TestReadModel.name] = {
            class: TestReadModel,
            authorizedRoles: [UserRole],
            properties: [],
            before: [],
          }

          replace(config.provider.readModels, 'subscribe', providerSubscribeFunctionFake)
        })

        it('calls the provider subscribe function and returns its results', async () => {
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
      })

      context('with before hooks', () => {
        const providerSubscribeFunctionFake = fake()

        beforeEach(() => {
          config.readModels[TestReadModel.name] = {
            class: TestReadModel,
            authorizedRoles: [UserRole],
            properties: [],
            before: [beforeFn, beforeFnV2],
          }

          replace(config.provider.readModels, 'subscribe', providerSubscribeFunctionFake)
        })

        it('calls the provider subscribe function when setting before hooks and returns the new filter in the result', async () => {
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
  })

  describe("The 'unsubscribe' method", () => {
    it('calls the provider "deleteSubscription" method with the right data', async () => {
      const deleteSubscriptionFake = fake()
      replace(config.provider.readModels, 'deleteSubscription', deleteSubscriptionFake)
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
      replace(config.provider.readModels, 'deleteAllSubscriptions', deleteAllSubscriptionsFake)
      const connectionID = random.uuid()
      await readModelReader.unsubscribeAll(connectionID)

      expect(deleteAllSubscriptionsFake).to.have.been.calledOnceWithExactly(match.any, match.any, connectionID)
    })
  })

  describe('the `initializeSearcherWithFilters` method', () => {
    class ReadModelClass1 {
      readonly id: string = '42'
    }

    const fakeBefore = [fake(), fake()]

    beforeEach(() => {
      config.readModels['ReadModelClass1'] = {
        class: ReadModelClass1,
        properties: [],
        authorizedRoles: [],
        before: fakeBefore,
      }
    })

    afterEach(() => {
      delete config.readModels['ReadModelClass1']
    })

    it('builds a searcher for a non-sequenced read model request', () => {
      const fakeSearcher = {
        filter: fake(),
      }
      const fakeReadModelSearcherBuilder = fake.returns(fakeSearcher)
      replace(Booster, 'readModel', fakeReadModelSearcherBuilder)
      const fakeReadModelFilters = { fake: 'read model filters' }
      const fakeGetReadModelFilters = fake.returns(fakeReadModelFilters)
      replace(filterHelpers, 'getReadModelFilters', fakeGetReadModelFilters)

      const fakeUser = {
        id: 'a user',
      }
      const fakeFilters = {
        fake: 'filters',
      }
      const untypedReadModelReader = readModelReader as any
      untypedReadModelReader.initializeSearcherWithFilters('ReadModelClass1', fakeUser, fakeFilters)

      expect(fakeReadModelSearcherBuilder).to.have.been.calledOnceWith(ReadModelClass1)
      expect(fakeGetReadModelFilters).to.have.been.calledOnceWith(fakeFilters, fakeBefore, fakeUser)
      expect(fakeSearcher.filter).to.have.been.calledOnceWith(fakeReadModelFilters)
    })

    it('builds a searcher for a sequenced read model request', () => {
      const fakeSearcher = {
        filter: fake(),
      }
      const fakeReadModelSearcherBuilder = fake.returns(fakeSearcher)
      replace(Booster, 'readModel', fakeReadModelSearcherBuilder)
      const fakeReadModelFilters = { fake: 'read model filters' }
      const fakeGetReadModelFilters = fake.returns(fakeReadModelFilters)
      replace(filterHelpers, 'getReadModelFilters', fakeGetReadModelFilters)

      const fakeUser = {
        id: 'a user',
      }
      const untypedReadModelReader = readModelReader as any
      untypedReadModelReader.initializeSearcherWithFilters('ReadModelClass1', fakeUser)

      expect(fakeReadModelSearcherBuilder).to.have.been.calledOnceWith(ReadModelClass1)
      expect(fakeGetReadModelFilters).to.have.been.calledOnceWith({}, fakeBefore, fakeUser)
      expect(fakeSearcher.filter).to.have.been.calledOnceWith(fakeReadModelFilters)
    })
  })
})
