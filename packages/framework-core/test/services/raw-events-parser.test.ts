import { describe } from 'mocha'
import { fake, restore, SinonSpy } from 'sinon'
import {
  ProviderLibrary,
  BoosterConfig,
  EventEnvelope,
  UUID,
  EntitySnapshotEnvelope,
} from '@boostercloud/framework-types'
import { RawEventsParser } from '../../src/services/raw-events-parser'
import { expect } from '../expect'
import { random } from 'faker'

describe('RawEventsParser', () => {
  afterEach(() => {
    restore()
  })

  const rawEvents = {} // This value doesn't matter, because we are going to fake 'rawToEnvelopes'
  const entityAName = 'EntityA'
  const entityAID = 'EntityAID'
  const entityBName = 'EntityB'
  const entityBID = 'EntityBID'
  const snapshottedEntityName = 'SnapshottedEntity'
  let persistedEventEnvelopeForEntityA1: EventEnvelope
  let persistedEventEnvelopeForEntityA2: EventEnvelope
  let persistedEventEnvelopeForEntityA3: EventEnvelope

  let persistedEventEnvelopeForEntityB1: EventEnvelope
  let persistedEventEnvelopeForEntityB2: EventEnvelope
  let persistedEventEnvelopeForEntityB3: EventEnvelope
  let persistedEventEnvelopeForEntityB4: EventEnvelope

  let eventSource: Array<EventEnvelope | EntitySnapshotEnvelope>
  let fakeRawToEnvelopes: SinonSpy
  let config: BoosterConfig

  beforeEach(() => {
    persistedEventEnvelopeForEntityA1 = createPersistedEventEnvelope(entityAName, entityAID)
    persistedEventEnvelopeForEntityA2 = createPersistedEventEnvelope(entityAName, entityAID)
    persistedEventEnvelopeForEntityA3 = createPersistedEventEnvelope(entityAName, entityAID)

    persistedEventEnvelopeForEntityB1 = createPersistedEventEnvelope(entityBName, entityBID)
    persistedEventEnvelopeForEntityB2 = createPersistedEventEnvelope(entityBName, entityBID)
    persistedEventEnvelopeForEntityB3 = createPersistedEventEnvelope(entityBName, entityBID)
    persistedEventEnvelopeForEntityB4 = createPersistedEventEnvelope(entityBName, entityBID)

    eventSource = [
      persistedEventEnvelopeForEntityA1,
      createEntitySnapshotEnvelope(snapshottedEntityName, random.uuid()),
      persistedEventEnvelopeForEntityA2,
      persistedEventEnvelopeForEntityA3,
      createEntitySnapshotEnvelope(snapshottedEntityName, random.uuid()),
      createEntitySnapshotEnvelope(snapshottedEntityName, random.uuid()),
      persistedEventEnvelopeForEntityB1,
      persistedEventEnvelopeForEntityB2,
      persistedEventEnvelopeForEntityB3,
      createEntitySnapshotEnvelope(snapshottedEntityName, random.uuid()),
      createEntitySnapshotEnvelope(snapshottedEntityName, random.uuid()),
      persistedEventEnvelopeForEntityB4,
      createEntitySnapshotEnvelope(snapshottedEntityName, random.uuid()),
    ]

    fakeRawToEnvelopes = fake.returns(eventSource)
    config = new BoosterConfig('test')
    config.provider = {
      events: {
        rawToEnvelopes: fakeRawToEnvelopes,
      },
    } as unknown as ProviderLibrary
    config.logger = {
      error: fake(),
      info: fake(),
      debug: fake(),
      warn: fake(),
    }
  })

  describe('streamPerEntityEvents', () => {
    it('strips all snapshots', async () => {
      const callbackFunction = fake()
      const events = config.provider.events.rawToEnvelopes(rawEvents)
      await RawEventsParser.streamPerEntityEvents(config, events, callbackFunction)
      expect(callbackFunction).not.to.have.been.calledWith(snapshottedEntityName)
    })

    it('calls the callback function with ordered groups of event envelopes per entity name and ID', async () => {
      const callbackFunction = fake()
      const events = config.provider.events.rawToEnvelopes(rawEvents)
      await RawEventsParser.streamPerEntityEvents(config, events, callbackFunction)
      expect(callbackFunction).to.have.been.calledTwice
      expect(callbackFunction).to.have.been.calledWithExactly(
        entityAName,
        entityAID,
        [persistedEventEnvelopeForEntityA1, persistedEventEnvelopeForEntityA2, persistedEventEnvelopeForEntityA3],
        config
      )
      expect(callbackFunction).to.have.been.calledWithExactly(
        entityBName,
        entityBID,
        [
          persistedEventEnvelopeForEntityB1,
          persistedEventEnvelopeForEntityB2,
          persistedEventEnvelopeForEntityB3,
          persistedEventEnvelopeForEntityB4,
        ],
        config
      )
    })

    it('calls the callback function for all the events per entity even if for some it throws', async () => {
      const error = new Error('Wow, such error, many failures!')
      const events = [] as Array<EventEnvelope>
      const callbackFunction = fake(
        async (entityName: string, entityId: UUID, eventEnvelopes: Array<EventEnvelope>): Promise<void> => {
          if (entityName === entityAName) {
            throw error
          }
          events.push(...eventEnvelopes)
        }
      )
      const eventsEnvelopes = config.provider.events.rawToEnvelopes(rawEvents)
      await expect(RawEventsParser.streamPerEntityEvents(config, eventsEnvelopes, callbackFunction)).to.be.eventually
        .fulfilled

      expect(callbackFunction).to.have.been.calledTwice
      expect(callbackFunction).to.have.been.calledWithExactly(
        entityAName,
        entityAID,
        [persistedEventEnvelopeForEntityA1, persistedEventEnvelopeForEntityA2, persistedEventEnvelopeForEntityA3],
        config
      )
      const entityBEvents = [
        persistedEventEnvelopeForEntityB1,
        persistedEventEnvelopeForEntityB2,
        persistedEventEnvelopeForEntityB3,
        persistedEventEnvelopeForEntityB4,
      ]
      expect(callbackFunction).to.have.been.calledWithExactly(entityBName, entityBID, entityBEvents, config)
      expect(events).to.deep.equal(entityBEvents)

      expect(config.logger?.error).to.have.been.calledWithExactly(
        '[Booster]|RawEventsParser#streamPerEntityEvents: ',
        `An error occurred while processing events for entity ${entityAName} with ID ${entityAID}`,
        error
      )
    })
  })
})

function createPersistedEventEnvelope(entityTypeName: string, entityID: string): EventEnvelope {
  const createdAt = random.alpha()
  return {
    entityID: entityID,
    entityTypeName: entityTypeName,
    kind: 'event',
    superKind: 'domain',
    version: 1,
    value: { id: random.uuid() },
    requestID: random.uuid(),
    typeName: 'Event' + random.alpha(),
    createdAt,
  }
}

function createEntitySnapshotEnvelope(entityTypeName: string, entityID: string): EntitySnapshotEnvelope {
  const snapshottedEventCreatedAt = random.alpha()
  return {
    entityID: entityID,
    entityTypeName: entityTypeName,
    kind: 'snapshot',
    superKind: 'domain',
    version: 1,
    value: { id: random.uuid() },
    requestID: random.uuid(),
    typeName: 'Snapshot' + random.alpha(),
    createdAt: snapshottedEventCreatedAt,
    persistedAt: random.alpha(),
    snapshottedEventCreatedAt,
  }
}
