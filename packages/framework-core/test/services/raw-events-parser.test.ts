import { describe } from 'mocha'
import { fake, restore, SinonSpy } from 'sinon'
import { ProviderLibrary, BoosterConfig, EventEnvelope, Logger } from '@boostercloud/framework-types'
import { RawEventsParser } from '../../src/services/raw-events-parser'
import { expect } from '../expect'
import { random } from 'faker'

const logger: Logger = console

describe('RawEventsParser', () => {
  afterEach(() => {
    restore()
  })

  const rawEvents = {} // This value doesn't matter, because we are going to fake 'rawToEnvelopes"
  const entityAName = 'EntityA'
  const entityAID = 'EntityAID'
  const entityBName = 'EntityB'
  const entityBID = 'EntityBID'
  const snapshottedEntityName = 'SnapshottedEntity'
  let eventEnvelopeForEntityA1: EventEnvelope
  let eventEnvelopeForEntityA2: EventEnvelope
  let eventEnvelopeForEntityA3: EventEnvelope

  let eventEnvelopeForEntityB1: EventEnvelope
  let eventEnvelopeForEntityB2: EventEnvelope
  let eventEnvelopeForEntityB3: EventEnvelope
  let eventEnvelopeForEntityB4: EventEnvelope

  let allEventEnvelopes: Array<EventEnvelope>
  let fakeRawToEnvelopes: SinonSpy
  let config: BoosterConfig

  beforeEach(() => {
    eventEnvelopeForEntityA1 = createEventEnvelope(entityAName, entityAID)
    eventEnvelopeForEntityA2 = createEventEnvelope(entityAName, entityAID)
    eventEnvelopeForEntityA3 = createEventEnvelope(entityAName, entityAID)

    eventEnvelopeForEntityB1 = createEventEnvelope(entityBName, entityBID)
    eventEnvelopeForEntityB2 = createEventEnvelope(entityBName, entityBID)
    eventEnvelopeForEntityB3 = createEventEnvelope(entityBName, entityBID)
    eventEnvelopeForEntityB4 = createEventEnvelope(entityBName, entityBID)

    allEventEnvelopes = [
      eventEnvelopeForEntityA1,
      createEventEnvelope(snapshottedEntityName, random.uuid(), 'snapshot'),
      eventEnvelopeForEntityA2,
      eventEnvelopeForEntityA3,
      createEventEnvelope(snapshottedEntityName, random.uuid(), 'snapshot'),
      createEventEnvelope(snapshottedEntityName, random.uuid(), 'snapshot'),
      eventEnvelopeForEntityB1,
      eventEnvelopeForEntityB2,
      eventEnvelopeForEntityB3,
      createEventEnvelope(snapshottedEntityName, random.uuid(), 'snapshot'),
      createEventEnvelope(snapshottedEntityName, random.uuid(), 'snapshot'),
      eventEnvelopeForEntityB4,
      createEventEnvelope(snapshottedEntityName, random.uuid(), 'snapshot'),
    ]

    fakeRawToEnvelopes = fake.returns(allEventEnvelopes)
    config = new BoosterConfig('test')
    config.provider = ({
      events: {
        rawToEnvelopes: fakeRawToEnvelopes,
      },
    } as unknown) as ProviderLibrary
  })

  describe('streamPerEntityEvents', () => {
    it('strips all snapshots', async () => {
      const callbackFunction = fake()
      await RawEventsParser.streamPerEntityEvents(logger, config, rawEvents, callbackFunction)
      expect(callbackFunction).not.to.have.been.calledWith(snapshottedEntityName)
    })

    it('calls the callback function with ordered groups of event envelopes per entity name and ID', async () => {
      const callbackFunction = fake()
      await RawEventsParser.streamPerEntityEvents(logger, config, rawEvents, callbackFunction)
      expect(callbackFunction).to.have.been.calledTwice
      expect(callbackFunction).to.have.been.calledWithExactly(
        entityAName,
        entityAID,
        [eventEnvelopeForEntityA1, eventEnvelopeForEntityA2, eventEnvelopeForEntityA3],
        config
      )
      expect(callbackFunction).to.have.been.calledWithExactly(
        entityBName,
        entityBID,
        [eventEnvelopeForEntityB1, eventEnvelopeForEntityB2, eventEnvelopeForEntityB3, eventEnvelopeForEntityB4],
        config
      )
    })
  })
})

function createEventEnvelope(
  entityTypeName: string,
  entityID: string,
  kind: EventEnvelope['kind'] = 'event'
): EventEnvelope {
  return {
    entityID: entityID,
    entityTypeName: entityTypeName,
    kind,
    createdAt: random.alpha(),
    version: 1,
    value: { id: random.uuid() },
    requestID: random.uuid(),
    typeName: 'Event' + random.alpha(),
  }
}
