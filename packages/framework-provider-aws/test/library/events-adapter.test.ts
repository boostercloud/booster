/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */
import { expect } from 'chai'
import * as chai from 'chai'
import * as Library from '../../src/library/events-adapter'
import { restore } from 'sinon'
import { EventEnvelope } from '@boostercloud/framework-types'
import { KinesisStreamEvent } from 'aws-lambda'

chai.use(require('sinon-chai'))

describe('the events-adapter', () => {
  afterEach(() => {
    restore()
  })

  describe('the `rawEventsToEnvelopes` method', () => {
    it('generates envelopes correctly from an AWS Kinesis event', async () => {
      const expectedEnvelopes = buildEventEnvelopes()
      const kinesisMessage = wrapEventEnvelopesForKinesis(expectedEnvelopes)

      const gotEnvelopes = await Library.rawEventsToEnvelopes(kinesisMessage)

      expect(gotEnvelopes).to.be.deep.equal(expectedEnvelopes)
    })
  })
})

function buildEventEnvelopes(): Array<EventEnvelope> {
  return [
    {
      version: 1,
      entityID: 'id',
      kind: 'event',
      value: {
        id: 'id',
      },
      typeName: 'EventName',
      entityTypeName: 'EntityName',
      requestID: 'requestID',
      createdAt: 'once',
    },
    {
      version: 1,
      entityID: 'id2',
      kind: 'event',
      value: {
        id: 'id2',
      },
      typeName: 'EventName2',
      entityTypeName: 'EntityName2',
      requestID: 'requestID2',
      createdAt: 'once upon a time',
    },
  ]
}

function wrapEventEnvelopesForKinesis(eventEnvelopes: Array<EventEnvelope>): KinesisStreamEvent {
  const kinesisMessage = {
    Records: eventEnvelopes.map((envelope) => {
      return {
        kinesis: {
          data: Buffer.from(JSON.stringify(envelope)).toString('base64'),
        },
      }
    }),
  }
  return kinesisMessage as KinesisStreamEvent
}
