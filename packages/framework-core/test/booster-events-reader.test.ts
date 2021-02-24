import { describe } from 'mocha'
import { Logger, ProviderLibrary, BoosterConfig, EventSearchRequest } from '@boostercloud/framework-types'
import { restore } from 'sinon'
import { random } from 'faker'
import { BoosterEventsReader } from '../src/booster-events-reader'
import { expect } from './expect'

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

  let config: BoosterConfig
  let eventsReader: BoosterEventsReader

  beforeEach(() => {
    config = new BoosterConfig('test')

    config.provider = ({} as unknown) as ProviderLibrary
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
      await expect(eventsReader.fetch(request)).to.be.rejectedWith(
        /Invalid event search request/
      )
    })
    it('it is an invalid type of event search: it is both a "byEntity" and a "byType" search', async () => {
      const request: EventSearchRequest = {
        requestID: random.uuid(),
        filters: {
          entity: TestEntity.name,
          type: TestEvent.name,
        },
      }
      await expect(eventsReader.fetch(request)).to.be.rejectedWith(
        /Invalid event search request/
      )
    })
  })

  describe.skip("The logic of 'fetch' and 'subscribe'  methods", () => {

  })
})
