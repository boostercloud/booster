/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
import { EventEnvelope } from '@boostercloud/framework-types'
import { date, random } from 'faker'
import { InvocationContext } from '@azure/functions'
import { AzureCosmosDBFunctionInput } from '../../src'

export function createMockEventEnvelopes(numOfEvents = 1): Array<EventEnvelope> {
  return new Array(numOfEvents).fill(
    {
      version: random.number(),
      entityID: random.uuid(),
      kind: 'event',
      superKind: 'domain',
      value: {
        id: random.uuid(),
      },
      typeName: random.word(),
      entityTypeName: random.word(),
      requestID: random.uuid(),
      createdAt: date.past().toISOString(),
      id: random.uuid(),
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

/**
 * Wraps event envelopes in an AzureCosmosDBFunctionInput structure for v4 programming model
 * @param eventEnvelopes
 */
export function wrapEventEnvelopesForCosmosDB(eventEnvelopes: Array<EventEnvelope>): AzureCosmosDBFunctionInput {
  return {
    documents: eventEnvelopes,
    context: {
      invocationId: random.uuid(),
      functionName: 'eventHandler',
      extraInputs: { get: () => undefined },
      extraOutputs: {
        set: () => {},
      },
      log: () => {},
      trace: () => {},
      debug: () => {},
      info: () => {},
      warn: () => {},
      error: () => {},
      options: {} as any,
      triggerMetadata: {},
    } as unknown as InvocationContext,
  }
}
