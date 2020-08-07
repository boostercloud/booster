/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
import { EventEnvelope } from '@boostercloud/framework-types'
import { random, date } from 'faker'
import { Context, ExecutionContext, Logger as AzureLogger, TraceContext } from '@azure/functions'

export function createMockEventEnvelopes(numOfEvents = 1): Array<EventEnvelope> {
  return new Array(numOfEvents).fill(
    {
      version: random.number(),
      entityID: random.uuid(),
      kind: 'event',
      value: {
        id: random.uuid(),
      },
      typeName: random.word(),
      entityTypeName: random.word(),
      requestID: random.uuid(),
      createdAt: date.past().toISOString(),
    },
    0,
    numOfEvents
  )
}

export function addMockSystemGeneratedProperties(eventEnvelopes: Array<EventEnvelope>): Array<EventEnvelope> {
  return eventEnvelopes.map((eventEnvelope: EventEnvelope) => {
    return {
      ...eventEnvelope,
      id: random.uuid(),
      _rid: random.alphaNumeric(24),
      _self: `dbs/${random.alphaNumeric(8)}/colls/${random.alphaNumeric(12)}/docs/${random.alphaNumeric(24)}/`,
      _etag: `"${random.uuid()}"`,
      _attachments: 'attachments/',
      _ts: ~~(date.past().getTime() / 1000),
    }
  })
}

export function wrapEventEnvelopesForCosmosDB(eventEnvelopes: Array<EventEnvelope>): Context {
  return {
    bindingData: {},
    bindingDefinitions: [],
    executionContext: {} as ExecutionContext,
    invocationId: '',
    log: {} as AzureLogger,
    traceContext: {} as TraceContext,
    done(err?: Error | string | null, result?: any): void {},
    bindings: { rawEvent: eventEnvelopes },
  }
}
