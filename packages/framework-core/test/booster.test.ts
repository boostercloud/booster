/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from './expect'
import { Booster, boosterEventDispatcher, boosterServeGraphQL } from '../src/booster'
import { replace, fake, restore, match, replaceGetter } from 'sinon'
import { Importer } from '../src/importer'
import {
  BoosterConfig,
  EventParametersFilterByType,
  EventInterface,
  EventSearchResponse,
  ProviderLibrary,
  UUID,
  NotificationInterface,
} from '@boostercloud/framework-types'
import { EventStore } from '../src/services/event-store'
import { random } from 'faker'
import { JwksUriTokenVerifier } from '../src/services/token-verifiers'
import { afterEach } from 'mocha'

describe('the `Booster` class', () => {
  afterEach(() => {
    restore()
    Booster.configure('test', (config) => {
      config.appName = ''
      for (const propName in config.commandHandlers) {
        delete config.commandHandlers[propName]
      }
    })
  })

  describe('the `configure` method', () => {
    it('can be used to configure the app', () => {
      const booster = Booster as any

      Booster.configure('test', (config) => {
        config.appName = 'test-app-name'
        config.providerPackage = 'some-provider'
      })

      Booster.configure('another-environment', (config) => {
        config.appName = 'this-shouldnt-be-set'
      })

      expect(booster.configuredEnvironments).to.have.lengthOf(2)
      expect(booster.configuredEnvironments).to.include.keys(['test', 'another-environment'])
      expect(booster.config.appName).to.equal('test-app-name')
      expect(booster.config.providerPackage).to.equal('some-provider')
    })
  })

  describe('the `start` method', () => {
    it('imports all the user files', () => {
      const fakeImporter = fake()
      replace(Importer, 'importUserProjectFiles', fakeImporter)
      Booster.start('path/to/code')
      expect(fakeImporter).to.have.been.calledOnce
    })
  })

  describe('the `readModel` method', () => {
    class TestReadModel {
      constructor(public id: string) {}
      public getId() {
        return this.id
      }
    }
    it('returns a properly configured Searcher', async () => {
      const searcherFunctionFake = fake.resolves([])
      Booster.configureCurrentEnv((config) => {
        replaceGetter(config, 'provider', () => {
          return {
            readModels: {
              search: searcherFunctionFake,
            },
          } as any
        })
      })
      await Booster.readModel(TestReadModel).search()
      expect(searcherFunctionFake).to.have.been.calledOnceWithExactly(
        match.any,
        TestReadModel.name,
        match.any,
        {},
        undefined,
        undefined,
        false
      )
    })
    it('has an instance method', async () => {
      const searcherFunctionFake = fake.returns([{ id: '42' }])
      Booster.configureCurrentEnv((config) => {
        replaceGetter(config, 'provider', () => {
          return {
            readModels: {
              search: searcherFunctionFake,
            },
          } as any
        })
      })
      const readModels = (await Booster.readModel(TestReadModel).search()) as Array<TestReadModel>
      for (const readModel of readModels) {
        expect(readModel.getId()).to.not.throw
      }
      expect(searcherFunctionFake).to.have.been.calledOnce
    })
  })
  describe('the `entitiesIDs` method', () => {
    it('has an instance method', async () => {
      const providerSearchEntitiesIds = fake.returns([])
      Booster.configureCurrentEnv((config) => {
        config.provider = {
          events: {
            searchEntitiesIDs: providerSearchEntitiesIds,
          },
        } as unknown as ProviderLibrary
      })
      await Booster.entitiesIDs('TestEvent', 1, undefined)
      expect(providerSearchEntitiesIds).to.have.been.calledOnce
    })
  })
  describe('the `event` method', () => {
    class TestEvent {
      public constructor(readonly id: UUID) {}
      public entityID(): UUID {
        return this.id
      }
      public getId(): UUID {
        return this.id
      }
    }
    class BestEvent {
      public constructor(readonly id: UUID) {}
      public entityID(): UUID {
        return this.id
      }
      public getId(): UUID {
        return this.id
      }
    }

    afterEach(() => {
      restore()
      Booster.configureCurrentEnv((config) => {
        config.appName = ''
        for (const propName in config.events) {
          delete config.events[propName]
        }
        for (const propName in config.notifications) {
          delete config.notifications[propName]
        }
      })
    })

    it('has an instance method', async () => {
      const searchResult: EventSearchResponse[] = [
        {
          requestID: random.uuid(),
          type: TestEvent.name,
          entity: random.alpha(),
          entityID: random.uuid(),
          createdAt: random.alphaNumeric(),
          value: {
            id: '1',
            entityID: () => UUID.generate(),
          } as EventInterface,
        },
        {
          requestID: random.uuid(),
          type: BestEvent.name,
          entity: random.alpha(),
          entityID: random.uuid(),
          createdAt: random.alphaNumeric(),
          value: {
            id: '1',
            entityID: () => UUID.generate(),
          } as EventInterface,
        },
      ]
      const providerEventsSearch = fake.returns(searchResult)
      Booster.configureCurrentEnv((config) => {
        config.provider = {
          events: {
            search: providerEventsSearch,
          },
        } as unknown as ProviderLibrary
        config.events[TestEvent.name] = { class: TestEvent }
        config.events[BestEvent.name] = { class: BestEvent }
      })

      const eventFilterByType: EventParametersFilterByType = {
        type: TestEvent.name,
      }

      const events = await Booster.events(eventFilterByType)

      for (const event of events) {
        let eventValue
        switch (event.type) {
          case TestEvent.name:
            eventValue = event.value as TestEvent
            expect(eventValue.getId()).to.not.throw
            break
          case BestEvent.name:
            eventValue = event.value as BestEvent
            expect(eventValue.getId()).to.not.throw
            break
          default:
            break
        }
      }

      expect(providerEventsSearch).to.have.been.calledOnce
    })

    it('has a plain object if event class does not exist', async () => {
      const searchResult: EventSearchResponse[] = [
        {
          requestID: random.uuid(),
          type: TestEvent.name,
          entity: random.alpha(),
          entityID: random.uuid(),
          createdAt: random.alphaNumeric(),
          value: {
            id: '1',
            entityID: () => UUID.generate(),
          } as EventInterface,
        },
        {
          requestID: random.uuid(),
          type: BestEvent.name,
          entity: random.alpha(),
          entityID: random.uuid(),
          createdAt: random.alphaNumeric(),
          value: {
            id: '1',
            entityID: () => UUID.generate(),
          } as EventInterface,
        },
      ]
      const providerEventsSearch = fake.returns(searchResult)
      Booster.configureCurrentEnv((config) => {
        config.provider = {
          events: {
            search: providerEventsSearch,
          },
        } as unknown as ProviderLibrary
        config.events[TestEvent.name] = { class: TestEvent }
      })

      const eventFilterByType: EventParametersFilterByType = {
        type: TestEvent.name,
      }

      const events = await Booster.events(eventFilterByType)

      for (const event of events) {
        let eventValue
        switch (event.type) {
          case TestEvent.name:
            eventValue = event.value as TestEvent
            expect(eventValue.getId()).to.not.throw
            break
          case BestEvent.name:
            eventValue = event.value as BestEvent
            expect(eventValue.getId).to.be.undefined
            break
          default:
            break
        }
      }

      expect(providerEventsSearch).to.have.been.calledOnce
    })

    it('has a plain object if notification class does not exist', async () => {
      const searchResult: EventSearchResponse[] = [
        {
          requestID: random.uuid(),
          type: TestEvent.name,
          entity: random.alpha(),
          entityID: random.uuid(),
          createdAt: random.alphaNumeric(),
          value: {
            id: '1',
            entityID: () => UUID.generate(),
          } as NotificationInterface,
        },
      ]
      const providerEventsSearch = fake.returns(searchResult)
      Booster.configureCurrentEnv((config) => {
        config.provider = {
          events: {
            search: providerEventsSearch,
          },
        } as unknown as ProviderLibrary
        config.notifications[TestEvent.name] = { class: TestEvent }
      })

      const eventFilterByType: EventParametersFilterByType = {
        type: TestEvent.name,
      }

      const events = await Booster.events(eventFilterByType)

      for (const event of events) {
        let eventValue
        switch (event.type) {
          case TestEvent.name:
            eventValue = event.value as TestEvent
            expect(eventValue.getId()).to.not.throw
            break
          default:
            break
        }
      }

      expect(providerEventsSearch).to.have.been.calledOnce
    })
  })

  describe('the public static function `boosterEventDispatcher`', () => {
    it('calls `Booster.dispatchEvent` passing the rawEvent', async () => {
      replace(Booster, 'dispatchEvent', fake())
      const message = { body: 'Test body' }

      await boosterEventDispatcher(message)

      expect(Booster.dispatchEvent).to.have.been.calledOnceWith(message)
    })
  })

  describe('the public static function `boosterServeGraphQL`', () => {
    it('calls `Booster.serveGraphQL` passing the rawMessage', async () => {
      replace(Booster, 'serveGraphQL', fake())
      const message = { body: 'Test body' }

      await boosterServeGraphQL(message)

      expect(Booster.serveGraphQL).to.have.been.calledOnceWith(message)
    })
  })

  describe('The `entity` method', () => {
    context('given a BoosterConfig', () => {
      const config = new BoosterConfig('test')
      config.provider = {} as ProviderLibrary

      it('the `entity` function calls to the `fetchEntitySnapshot` method in the EventStore', async () => {
        replace(EventStore.prototype, 'fetchEntitySnapshot', fake.returns({ value: { id: '42' } }))

        class SomeEntity {
          public constructor(readonly id: UUID) {}
        }
        const snapshot = await Booster.entity(SomeEntity, '42')

        expect(snapshot).to.be.deep.equal({ id: '42' })
        expect(EventStore.prototype.fetchEntitySnapshot).to.have.been.calledOnceWith('SomeEntity', '42')
      })

      it('the entity function has an instance method', async () => {
        replace(EventStore.prototype, 'fetchEntitySnapshot', fake.returns({ id: '42' }))

        class SomeEntity {
          public constructor(readonly id: UUID) {}
          public getId(): UUID {
            return this.id
          }
        }
        const snapshot = await Booster.entity(SomeEntity, '42')
        snapshot?.getId()
        if (snapshot) {
          expect(snapshot?.getId()).to.not.throw
        }
      })
    })
  })

  describe('The `loadTokenVerifierFromEnv` function', () => {
    context('when the JWT_ENV_VARS are set', () => {
      beforeEach(() => {
        process.env.BOOSTER_JWT_ISSUER = 'BOOSTER_JWT_ISSUER_VALUE'
        process.env.BOOSTER_JWKS_URI = 'BOOSTER_JWKS_URI_VALUE'
        process.env.BOOSTER_ROLES_CLAIM = 'BOOSTER_ROLES_CLAIM_VALUE'
      })

      afterEach(() => {
        delete process.env.BOOSTER_JWT_ISSUER
        delete process.env.BOOSTER_JWKS_URI
        delete process.env.BOOSTER_ROLES_CLAIM

        Booster.config.tokenVerifiers = []
      })

      it('does alter the token verifiers config', () => {
        expect(Booster.config.tokenVerifiers).to.be.empty

        const booster = Booster as any
        booster.loadTokenVerifierFromEnv()

        const tokenVerifierConfig = Booster.config.tokenVerifiers
        expect(tokenVerifierConfig.length).to.be.equal(1)
        expect(tokenVerifierConfig[0]).to.be.an.instanceOf(JwksUriTokenVerifier)
        expect((tokenVerifierConfig[0] as JwksUriTokenVerifier).issuer).to.be.equal('BOOSTER_JWT_ISSUER_VALUE')
        expect((tokenVerifierConfig[0] as JwksUriTokenVerifier).jwksUri).to.be.equal('BOOSTER_JWKS_URI_VALUE')
        expect((tokenVerifierConfig[0] as JwksUriTokenVerifier).rolesClaim).to.be.equal('BOOSTER_ROLES_CLAIM_VALUE')
      })
    })

    context('when the JWT_ENV_VARS are not set', () => {
      it('does not alter the token verifiers config', () => {
        expect(Booster.config.tokenVerifiers).to.be.empty

        const booster = Booster as any
        booster.loadTokenVerifierFromEnv()

        expect(Booster.config.tokenVerifiers).to.be.empty
      })
    })
  })
})
