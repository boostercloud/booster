/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from './expect'
import {
  BoosterConfig,
  FilterFor,
  GraphQLOperation,
  InvalidParameterError,
  NotAuthorizedError,
  NotFoundError,
  ReadModelInterface,
  ReadModelListResult,
  ReadModelRequestEnvelope,
  SubscriptionEnvelope,
  UUID,
} from '@boostercloud/framework-types'
import { fake, match, replace, restore, SinonStub, stub } from 'sinon'
import { BoosterReadModelsReader } from '../src/booster-read-models-reader'
import { internet, random } from 'faker'
import { Booster } from '../src/booster'
import { BoosterAuthorizer } from '../src/booster-authorizer'
import { ReadModelSchemaMigrator } from '../src/read-model-schema-migrator'

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

  // Avoid printing logs during tests
  config.logger = {
    debug: fake(),
    info: fake(),
    warn: fake(),
    error: fake(),
  }

  afterEach(() => {
    restore()
  })

  class TestReadModel implements ReadModelInterface {
    public id: UUID = '∂'
  }

  class SequencedReadModel implements ReadModelInterface {
    public id: UUID = 'π'
  }

  class UserRole {}

  // Why sorting by salmon? Salmons are fun! https://youtu.be/dDj7DuHVV9E
  config.readModelSequenceKeys[SequencedReadModel.name] = 'salmon'

  const readModelReader = new BoosterReadModelsReader(config)

  const noopGraphQLOperation: GraphQLOperation = {
    query: '',
  }

  context('requests by Id', () => {
    beforeEach(() => {
      config.readModels[TestReadModel.name] = {
        class: TestReadModel,
        authorizer: BoosterAuthorizer.authorizeRoles.bind(null, [UserRole]),
        properties: [],
        before: [],
      }
      config.readModels[SequencedReadModel.name] = {
        class: SequencedReadModel,
        authorizer: BoosterAuthorizer.authorizeRoles.bind(null, [UserRole]),
        properties: [],
        before: [],
      }
    })

    afterEach(() => {
      delete config.readModels[TestReadModel.name]
      delete config.readModels[SequencedReadModel.name]
    })

    describe('the `validateByIdRequest` function', () => {
      const validateByIdRequest = (readModelReader as any).validateByIdRequest.bind(readModelReader) as (
        readModelByIdRequest: ReadModelRequestEnvelope<ReadModelInterface>
      ) => Promise<void>

      it('throws an invalid parameter error when the version is not present in a request', async () => {
        const emptyReadmodelByIdRequest = {} as any
        await expect(validateByIdRequest(emptyReadmodelByIdRequest)).to.be.eventually.rejectedWith(
          '"version" was not present'
        )
      })

      it("throws a not found error when it can't find the read model metadata", async () => {
        const readModelByIdRequest = { version: 1, class: { name: 'NonexistentReadModel' } } as any
        await expect(validateByIdRequest(readModelByIdRequest)).to.be.eventually.rejectedWith(
          /Could not find read model/
        )
      })

      it('throws a non authorized error when the current user is not allowed to perform the request', async () => {
        const readModelByIdRequest = { version: 1, class: TestReadModel } as any
        await expect(validateByIdRequest(readModelByIdRequest)).to.be.eventually.rejectedWith(/Access denied/)
      })

      it('throws an invalid parameter error when the request receives a sequence key but it cannot be found in the Booster metadata', async () => {
        const readModel = {
          version: 1,
          class: TestReadModel,
          currentUser: { id: '666', username: 'root', roles: ['UserRole'], claims: {} },
          key: {
            id: 'π',
            sequenceKey: { name: 'salmon', value: 'sammy' },
          },
        } as any
        await expect(validateByIdRequest(readModel)).to.be.eventually.rejectedWith(/Could not find a sort key/)
      })

      it('does not throw an error when there is no sequence key and everything else is ok', async () => {
        const readModel = {
          version: 1,
          class: TestReadModel,
          currentUser: { id: '666', username: 'root', roles: ['UserRole'] },
        } as any
        await expect(validateByIdRequest(readModel)).to.be.eventually.fulfilled
      })

      it('does not throw an error when there is a valid sequence key and everything else is ok', async () => {
        const readModel = {
          version: 1,
          class: SequencedReadModel,
          currentUser: { id: '666', username: 'root', roles: ['UserRole'] },
          key: {
            id: '§',
            sequenceKey: { name: 'salmon', value: 'sammy' },
          },
        } as any
        await expect(validateByIdRequest(readModel)).to.be.eventually.fulfilled
      })
    })

    describe('the `findById` method', () => {
      let migratorStub: SinonStub
      beforeEach(() => {
        config.readModels['SomeReadModel'] = {
          before: [],
        } as any
        migratorStub = stub(ReadModelSchemaMigrator.prototype, 'migrate')
      })

      afterEach(() => {
        delete config.readModels['SomeReadModel']
        migratorStub.restore()
      })

      it('validates and uses the searcher to find a read model by id', async () => {
        const fakeValidateByIdRequest = fake()
        replace(readModelReader as any, 'validateByIdRequest', fakeValidateByIdRequest)
        const fakeSearcher = { findById: fake() }
        replace(Booster, 'readModel', fake.returns(fakeSearcher))

        const currentUser = {
          id: 'a user',
        } as any
        const sequenceKey = {
          name: 'salmon',
          value: 'sammy',
        }
        const readModelRequestEnvelope = {
          key: {
            id: '42',
            sequenceKey,
          },
          class: { name: 'SomeReadModel' },
          className: 'SomeReadModel',
          currentUser,
          version: 1,
          requestID: 'my request!',
        } as any

        await readModelReader.findById(readModelRequestEnvelope)

        expect(fakeValidateByIdRequest).to.have.been.calledOnceWith(readModelRequestEnvelope)
        expect(fakeSearcher.findById).to.have.been.calledOnceWith('42')
      })

      it('calls migrate after find a read model by id', async () => {
        const fakeValidateByIdRequest = fake()
        replace(readModelReader as any, 'validateByIdRequest', fakeValidateByIdRequest)

        const fakeSearcher = { findById: fake.returns(new TestReadModel()) }
        replace(Booster, 'readModel', fake.returns(fakeSearcher))

        const readModelRequestEnvelope = {
          key: {
            id: '42',
            sequenceKey: {
              name: 'salmon',
              value: 'sammy',
            },
          },
          class: { name: 'TestReadModel' },
          className: 'TestReadModel',
          currentUser: {
            id: 'a user',
          } as any,
          version: 1,
          requestID: 'my request!',
        } as any

        migratorStub.callsFake(async (readModel, readModelName) => readModel)

        await readModelReader.findById(readModelRequestEnvelope)

        expect(fakeValidateByIdRequest).to.have.been.calledOnceWith(readModelRequestEnvelope)
        expect(fakeSearcher.findById).to.have.been.calledOnceWith('42')
        expect(migratorStub).to.have.been.calledOnce
      })

      it('call migrate once the read model after find a read model by id and got an Array', async () => {
        const fakeValidateByIdRequest = fake()
        replace(readModelReader as any, 'validateByIdRequest', fakeValidateByIdRequest)
        const fakeSearcher = { findById: fake.returns([new TestReadModel(), new TestReadModel()]) }
        replace(Booster, 'readModel', fake.returns(fakeSearcher))

        const readModelRequestEnvelope = {
          key: {
            id: '42',
            sequenceKey: {
              name: 'salmon',
              value: 'sammy',
            },
          },
          class: { name: 'TestReadModel' },
          className: 'TestReadModel',
          currentUser: {
            id: 'a user',
          } as any,
          version: 1,
          requestID: 'my request!',
        } as any

        migratorStub.callsFake(async (readModel, readModelName) => readModel)

        await readModelReader.findById(readModelRequestEnvelope)

        expect(fakeValidateByIdRequest).to.have.been.calledOnceWith(readModelRequestEnvelope)
        expect(fakeSearcher.findById).to.have.been.calledOnceWith('42')
        expect(migratorStub).to.have.been.calledOnce
      })
    })
  })

  describe('the validation for methods `search` and `subscribe`', () => {
    beforeEach(() => {
      config.readModels[TestReadModel.name] = {
        class: TestReadModel,
        authorizer: BoosterAuthorizer.authorizeRoles.bind(null, [UserRole]),
        properties: [],
        before: [],
      }
    })

    afterEach(() => {
      delete config.readModels[TestReadModel.name]
    })

    it('throws the right error when request is missing "version"', async () => {
      const envelope = {
        class: { name: 'anyReadModel' },
        requestID: random.uuid(),
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
      } as any // To avoid the compilation failure of "missing version field"

      await expect(readModelReader.search(envelope)).to.eventually.be.rejectedWith(InvalidParameterError)
      await expect(
        readModelReader.subscribe(envelope.requestID, envelope, noopGraphQLOperation)
      ).to.eventually.be.rejectedWith(InvalidParameterError)
    })

    it('throws the right error when the read model does not exist', async () => {
      const envelope: ReadModelRequestEnvelope<any> = {
        class: { name: 'nonExistentReadModel' },
        filters: {},
        requestID: random.uuid(),
        version: 1,
      } as any
      await expect(readModelReader.search(envelope)).to.eventually.be.rejectedWith(NotFoundError)
      await expect(
        readModelReader.subscribe(envelope.requestID.toString(), envelope, noopGraphQLOperation)
      ).to.eventually.be.rejectedWith(NotFoundError)
    })

    it('throws the right error when the user is not authorized', async () => {
      const envelope: ReadModelRequestEnvelope<TestReadModel> = {
        class: TestReadModel,
        className: TestReadModel.name,
        requestID: random.uuid(),
        filters: {},
        version: 1,
        currentUser: {
          username: internet.email(),
          roles: [''],
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
      roles: [UserRole.name],
      claims: {},
    }

    const readModelRequestEnvelope: ReadModelRequestEnvelope<TestReadModel> = {
      class: TestReadModel,
      className: TestReadModel.name,
      requestID: random.uuid(),
      version: 1,
      filters,
      currentUser,
    } as any

    const beforeFn = async (request: ReadModelRequestEnvelope<any>): Promise<ReadModelRequestEnvelope<any>> => {
      return Promise.resolve({ ...request, filters: { id: { eq: request.filters.id } } })
    }

    const beforeFnV2 = async (request: ReadModelRequestEnvelope<any>): Promise<ReadModelRequestEnvelope<any>> => {
      return Promise.resolve({ ...request, filters: { id: { eq: request.currentUser?.username } } })
    }

    describe('the "search" method', () => {
      let migratorStub: SinonStub
      beforeEach(() => {
        config.readModels[TestReadModel.name] = {
          class: TestReadModel,
          authorizer: BoosterAuthorizer.authorizeRoles.bind(null, [UserRole]),
          properties: [],
          before: [],
        }
        migratorStub = stub(ReadModelSchemaMigrator.prototype, 'migrate')
      })

      afterEach(() => {
        delete config.readModels[TestReadModel.name]
        migratorStub.restore()
      })

      it('calls the provider search function and returns its results', async () => {
        const expectedReadModels = [new TestReadModel(), new TestReadModel()]
        const providerSearcherFunctionFake = fake.returns(expectedReadModels)
        replace(config.provider.readModels, 'search', providerSearcherFunctionFake)

        replace(Booster, 'config', config) // Needed because the function `Booster.readModel` references `this.config` from `searchFunction`

        migratorStub.callsFake(async (readModel, readModelName) => readModel)

        const result = await readModelReader.search(readModelRequestEnvelope)

        expect(providerSearcherFunctionFake).to.have.been.calledOnceWithExactly(
          match.any,
          TestReadModel.name,
          filters,
          {},
          undefined,
          undefined,
          false,
          undefined
        )
        expect(result).to.be.deep.equal(expectedReadModels)
      })

      it('calls migrates after search a read model with a simple array', async () => {
        const expectedReadModels = [new TestReadModel(), new TestReadModel()]
        const providerSearcherFunctionFake = fake.returns(expectedReadModels)
        replace(config.provider.readModels, 'search', providerSearcherFunctionFake)

        replace(Booster, 'config', config) // Needed because the function `Booster.readModel` references `this.config` from `searchFunction`

        migratorStub.callsFake(async (readModel, readModelName) => readModel)

        const result = await readModelReader.search(readModelRequestEnvelope)

        expect(providerSearcherFunctionFake).to.have.been.calledOnceWithExactly(
          match.any,
          TestReadModel.name,
          filters,
          {},
          undefined,
          undefined,
          false,
          undefined
        )
        expect(result).to.be.deep.equal(expectedReadModels)
        expect(migratorStub).to.have.been.calledTwice
      })

      it('calls migrates once after paginated search a read model', async () => {
        const expectedReadModels = [new TestReadModel(), new TestReadModel()]
        const searchResult: ReadModelListResult<TestReadModel> = {
          items: expectedReadModels,
          count: 2,
          cursor: {},
        }
        const providerSearcherFunctionFake = fake.returns(searchResult)
        replace(config.provider.readModels, 'search', providerSearcherFunctionFake)

        replace(Booster, 'config', config) // Needed because the function `Booster.readModel` references `this.config` from `searchFunction`

        migratorStub.callsFake(async (readModel, readModelName) => readModel)

        readModelRequestEnvelope.paginatedVersion = true
        const result = await readModelReader.search(readModelRequestEnvelope)

        expect(providerSearcherFunctionFake).to.have.been.calledOnceWithExactly(
          match.any,
          TestReadModel.name,
          filters,
          {},
          undefined,
          undefined,
          true,
          undefined
        )
        expect(result).to.be.deep.equal(searchResult)
        expect(migratorStub).to.have.been.calledTwice
      })

      context('when there are projections fields', () => {
        it('calls the provider search function with the right parameters', async () => {
          const readModelWithProjectionRequestEnvelope: ReadModelRequestEnvelope<TestReadModel> = {
            class: TestReadModel,
            className: TestReadModel.name,
            requestID: random.uuid(),
            version: 1,
            filters,
            currentUser,
            select: ['id'],
            skipInstance: false,
          } as any

          const expectedReadModels = [new TestReadModel(), new TestReadModel()]
          const providerSearcherFunctionFake = fake.returns(expectedReadModels)
          replace(config.provider.readModels, 'search', providerSearcherFunctionFake)

          replace(Booster, 'config', config) // Needed because the function `Booster.readModel` references `this.config` from `searchFunction`

          migratorStub.callsFake(async (readModel, readModelName) => readModel)

          const result = await readModelReader.search(readModelWithProjectionRequestEnvelope)

          expect(providerSearcherFunctionFake).to.have.been.calledOnceWithExactly(
            match.any,
            TestReadModel.name,
            filters,
            {},
            undefined,
            undefined,
            false,
            ['id']
          )
          expect(result).to.be.deep.equal(expectedReadModels)
        })

        it('do not  call migrates if select is set', async () => {
          const readModelWithProjectionRequestEnvelope: ReadModelRequestEnvelope<TestReadModel> = {
            class: TestReadModel,
            className: TestReadModel.name,
            requestID: random.uuid(),
            version: 1,
            filters,
            currentUser,
            select: ['id'],
            skipInstance: false,
          } as any

          const expectedResult = [new TestReadModel(), new TestReadModel()]
          const providerSearcherFunctionFake = fake.returns(expectedResult)
          replace(config.provider.readModels, 'search', providerSearcherFunctionFake)

          replace(Booster, 'config', config) // Needed because the function `Booster.readModel` references `this.config` from `searchFunction`

          migratorStub.callsFake(async (readModel, readModelName) => readModel)

          const result = await readModelReader.search(readModelWithProjectionRequestEnvelope)

          expect(providerSearcherFunctionFake).to.have.been.calledOnceWithExactly(
            match.any,
            TestReadModel.name,
            filters,
            {},
            undefined,
            undefined,
            false,
            ['id']
          )
          expect(result).to.be.deep.equal(expectedResult)
          expect(migratorStub).to.have.not.been.called
        })
      })

      context('when there is only one before hook function', () => {
        const fakeBeforeFn = fake(beforeFn)

        beforeEach(() => {
          const providerSearcherFunctionFake = fake.returns([])

          config.readModels[TestReadModel.name] = {
            class: TestReadModel,
            authorizer: BoosterAuthorizer.authorizeRoles.bind(null, [UserRole]),
            properties: [],
            before: [fakeBeforeFn],
          }

          replace(Booster, 'config', config) // Needed because the function `Booster.readModel` references `this.config` from `searchFunction`
          replace(config.provider.readModels, 'search', providerSearcherFunctionFake)
        })

        afterEach(() => {
          delete config.readModels[TestReadModel.name]
        })

        it('calls the before hook function', async () => {
          await readModelReader.search(readModelRequestEnvelope)

          const currentUser = readModelRequestEnvelope.currentUser
          expect(fakeBeforeFn).to.have.been.calledOnceWithExactly(readModelRequestEnvelope, currentUser)
          const expectedReturn = {
            ...readModelRequestEnvelope,
            filters: { id: { eq: readModelRequestEnvelope.filters.id } },
          }
          await expect(fakeBeforeFn.getCall(0).returnValue).to.have.eventually.become(expectedReturn)
        })
      })

      context('when there are more than one before hook functions', () => {
        const beforeFnSpy = fake(beforeFn)
        const beforeFnV2Spy = fake(beforeFnV2)

        beforeEach(() => {
          const providerSearcherFunctionFake = fake.returns([])

          config.readModels[TestReadModel.name] = {
            class: TestReadModel,
            authorizer: BoosterAuthorizer.authorizeRoles.bind(null, [UserRole]),
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
          await readModelReader.search(readModelRequestEnvelope)

          expect(beforeFnSpy).to.have.been.calledOnceWithExactly(
            readModelRequestEnvelope,
            readModelRequestEnvelope.currentUser
          )
          const expectedReturn = {
            ...readModelRequestEnvelope,
            filters: { id: { eq: readModelRequestEnvelope.filters.id } },
          }
          await expect(beforeFnSpy.getCall(0).returnValue).to.have.eventually.become(expectedReturn)

          const returnedEnvelope = await beforeFnSpy.returnValues[0]
          expect(beforeFnV2Spy).to.have.been.calledAfter(beforeFnSpy)
          expect(beforeFnV2Spy).to.have.been.calledOnceWithExactly(returnedEnvelope, returnedEnvelope.currentUser)
          const expectedReturnEnvelope = {
            ...returnedEnvelope,
            filters: { id: { eq: returnedEnvelope.currentUser?.username } },
          }
          await expect(beforeFnV2Spy.getCall(0).returnValue).to.have.eventually.become(expectedReturnEnvelope)
        })
      })
    })

    describe('the "subscribe" method', () => {
      context('with no before hooks defined', () => {
        const providerSubscribeFunctionFake = fake()

        beforeEach(() => {
          config.readModels[TestReadModel.name] = {
            class: TestReadModel,
            authorizer: BoosterAuthorizer.authorizeRoles.bind(null, [UserRole]),
            properties: [],
            before: [],
          }

          replace(config.provider.readModels, 'subscribe', providerSubscribeFunctionFake)
        })

        it('calls the provider subscribe function and returns its results', async () => {
          const connectionID = random.uuid()
          const expectedSubscriptionEnvelope: SubscriptionEnvelope = {
            ...readModelRequestEnvelope,
            connectionID,
            operation: noopGraphQLOperation,
            expirationTime: 1,
          }

          await readModelReader.subscribe(connectionID, readModelRequestEnvelope, noopGraphQLOperation)

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
            authorizer: BoosterAuthorizer.authorizeRoles.bind(null, [UserRole]),
            properties: [],
            before: [beforeFn, beforeFnV2],
          }

          replace(config.provider.readModels, 'subscribe', providerSubscribeFunctionFake)
        })

        it('calls the provider subscribe function when setting before hooks and returns the new filter in the result', async () => {
          const connectionID = random.uuid()
          readModelRequestEnvelope.filters = { id: { eq: currentUser?.username } } as Record<string, FilterFor<unknown>>
          const expectedSubscriptionEnvelope: SubscriptionEnvelope = {
            ...readModelRequestEnvelope,
            connectionID,
            operation: noopGraphQLOperation,
            expirationTime: 1,
          }

          await readModelReader.subscribe(connectionID, readModelRequestEnvelope, noopGraphQLOperation)

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

      expect(deleteSubscriptionFake).to.have.been.calledOnceWithExactly(match.any, connectionID, subscriptionID)
    })
  })

  describe("The 'unsubscribeAll' method", () => {
    it('calls the provider "deleteAllSubscription" method with the right data', async () => {
      const deleteAllSubscriptionsFake = fake()
      replace(config.provider.readModels, 'deleteAllSubscriptions', deleteAllSubscriptionsFake)
      const connectionID = random.uuid()
      await readModelReader.unsubscribeAll(connectionID)

      expect(deleteAllSubscriptionsFake).to.have.been.calledOnceWithExactly(match.any, connectionID)
    })
  })
})
