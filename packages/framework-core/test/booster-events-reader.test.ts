import { describe } from 'mocha'
import { Logger, ProviderLibrary, EventSearchRequest, EventSearchResponse, UUID } from '@boostercloud/framework-types'
import { restore, fake, SinonSpy, match } from 'sinon'
import { random, internet } from 'faker'
import { BoosterEventsReader } from '../src/booster-events-reader'
import { expect } from './expect'
import { Booster } from '../src'

const logger: Logger = console

describe('BoosterEventsReader', () => {
  class TestEntity {
    public id = 'testID'
  }
  class NonRegisteredTestEntity {
    public id = 'testID'
  }
  class TestEvent {}
  class TestEventReducedByNonRegisteredEntity {}
  class CanReadEventsRole {}

  let eventsReader: BoosterEventsReader
  let providerEventsSearch: SinonSpy
  const searchResult: EventSearchResponse = {
    requestID: random.uuid(),
    type: random.alpha(),
    entity: random.alpha(),
    entityID: random.uuid(),
    createdAt: random.alphaNumeric(),
    value: {
      entityID: () => UUID.generate(),
    },
  }

  beforeEach(() => {
    Booster.configureCurrentEnv((config) => {
      providerEventsSearch = fake.returns(searchResult)

      config.provider = ({
        events: {
          search: providerEventsSearch,
        },
      } as unknown) as ProviderLibrary

      config.entities[TestEntity.name] = {
        class: TestEntity,
        authorizeReadEvents: [CanReadEventsRole],
      }
      config.reducers[TestEvent.name] = {
        class: TestEntity,
        methodName: 'testReducerMethod',
      }
      config.reducers[TestEventReducedByNonRegisteredEntity.name] = {
        class: NonRegisteredTestEntity,
        methodName: 'testReducerMethod',
      }
      eventsReader = new BoosterEventsReader(config, logger)
    })
  })

  afterEach(() => {
    restore()
  })

  describe('the validation for the method `fetch` throws the right error when', () => {
    it('it is a "byEntity" search and entity metadata is not found', async () => {
      const request: EventSearchRequest = {
        requestID: random.uuid(),
        filters: {
          entity: 'NonExistingEntity',
        },
      }
      await expect(eventsReader.fetch(request)).to.be.rejectedWith(
        /Could not find entity metadata for "NonExistingEntity"/
      )
    })

    it('it is a "byType" search and the associated entity is not found', async () => {
      const request: EventSearchRequest = {
        requestID: random.uuid(),
        filters: {
          type: 'NonExistingEventType',
        },
      }
      await expect(eventsReader.fetch(request)).to.be.rejectedWith(
        /Could not find the entity associated to event type "NonExistingEventType"/
      )
    })

    it('it is a "byEvent" search and the associated entity metadata is not found', async () => {
      const request: EventSearchRequest = {
        requestID: random.uuid(),
        filters: {
          type: TestEventReducedByNonRegisteredEntity.name,
        },
      }
      await expect(eventsReader.fetch(request)).to.be.rejectedWith(
        /Could not find entity metadata for "NonRegisteredTestEntity"/
      )
    })

    it('it is an invalid type of event search: it is not a "byEntity" or a "byType" search', async () => {
      const request: EventSearchRequest = {
        requestID: random.uuid(),
        filters: {} as never,
      }
      await expect(eventsReader.fetch(request)).to.be.rejectedWith(/Invalid event search request/)
    })

    it('it is an invalid type of event search: it is both a "byEntity" and a "byType" search', async () => {
      const request: EventSearchRequest = {
        requestID: random.uuid(),
        filters: {
          entity: TestEntity.name,
          type: TestEvent.name,
        },
      }
      await expect(eventsReader.fetch(request)).to.be.rejectedWith(/Invalid event search request/)
    })

    it('user has no permissions', async () => {
      const request: EventSearchRequest = {
        currentUser: {
          role: 'NonValidRole',
          username: internet.email(),
        },
        requestID: random.uuid(),
        filters: {
          entity: TestEntity.name,
        },
      }
      await expect(eventsReader.fetch(request)).to.be.rejectedWith(/Access denied/)
    })
  })

  describe("The logic of 'fetch' method", () => {
    context('for a "byEntity" search', () => {
      const request: EventSearchRequest = {
        currentUser: {
          role: CanReadEventsRole.name,
          username: internet.email(),
        },
        requestID: random.uuid(),
        filters: {
          entity: TestEntity.name,
          from: 'fromTime',
          to: 'toTime',
        },
      }

      it('calls the provider search function with the right parameters and returns correctly', async () => {
        const result = await eventsReader.fetch(request)
        expect(providerEventsSearch).to.have.been.calledWith(match.any, match.any, request.filters)
        expect(result).to.be.deep.equal(searchResult)
      })
    })
  })
})
