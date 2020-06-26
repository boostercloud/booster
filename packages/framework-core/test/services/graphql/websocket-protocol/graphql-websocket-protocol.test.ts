import { fake, match, SinonStub, stub } from 'sinon'
import { random } from 'faker'
import {
  Logger,
  GraphQLServerMessage,
  GraphQLRequestEnvelope,
  GraphQLStart,
  MessageTypes,
} from '@boostercloud/framework-types'
import { GraphQLWebsocketHandler } from '../../../../src/services/graphql/websocket-protocol/graphql-websocket-protocol'
import { ExecutionResult } from 'graphql'
import { expect } from '../../../expect'

describe('the `GraphQLWebsocketHandler`', () => {
  let websocketHandler: GraphQLWebsocketHandler
  let sendToConnection: (connectionID: string, data: GraphQLServerMessage) => Promise<void>
  let onStartCallback: (
    envelope: GraphQLRequestEnvelope
  ) => Promise<AsyncIterableIterator<ExecutionResult> | ExecutionResult>
  let logger: Logger
  let envelope: GraphQLRequestEnvelope

  beforeEach(() => {
    sendToConnection = stub()
    onStartCallback = fake()
    logger = console
    websocketHandler = new GraphQLWebsocketHandler(logger, sendToConnection, {
      onStartOperation: onStartCallback,
      onStopOperation: undefined as any,
      onTerminateOperations: undefined as any,
    })
    envelope = {
      eventType: 'MESSAGE',
      requestID: random.alphaNumeric(10),
    }
  })

  describe('the "handle" method', () => {
    let resultPromise: Promise<void> | undefined
    beforeEach(() => {
      resultPromise = undefined
    })
    afterEach(async () => {
      // The handle method must never fail, just log or send the error to the connection ID.
      // We ensure here that the returned promise from the method is always fulfilled
      expect(resultPromise, "The test didn't set the 'resultPromise' variable with the result of 'handle' method").not
        .to.be.undefined
      await expect(resultPromise).to.eventually.be.fulfilled
    })

    describe('with an envelope with no connectionID', () => {
      beforeEach(() => {
        envelope.connectionID = undefined
      })

      it('just logs an error', async () => {
        logger.error = fake()
        resultPromise = websocketHandler.handle(envelope)
        await resultPromise
        expect(logger.error).to.be.calledOnceWithExactly('Missing websocket connectionID')
      })
    })

    describe('with an envelope with connectionID', () => {
      beforeEach(() => {
        envelope.connectionID = random.alphaNumeric(10)
      })

      describe('with an empty value', () => {
        beforeEach(() => {
          envelope.value = undefined
        })

        it('sends the right error', async () => {
          resultPromise = websocketHandler.handle(envelope)
          await resultPromise
          expect(sendToConnection).to.be.calledOnceWithExactly(
            envelope.connectionID,
            match({
              type: MessageTypes.GQL_CONNECTION_ERROR,
              payload: 'Received an empty GraphQL body',
            })
          )
        })
      })

      describe('with a value with GQL_CONNECTION_INIT message', () => {
        beforeEach(() => {
          envelope.value = {
            type: MessageTypes.GQL_CONNECTION_INIT,
            payload: {},
          }
        })

        it('sends back a GQL_CONNECTION_ACK', async () => {
          resultPromise = websocketHandler.handle(envelope)
          await resultPromise
          expect(sendToConnection).to.be.calledOnceWithExactly(
            envelope.connectionID,
            match({ type: MessageTypes.GQL_CONNECTION_ACK })
          )
        })
      })

      describe('with a value with GQL_START message', () => {
        beforeEach(() => {
          envelope.value = {
            id: random.alphaNumeric(10),
            type: MessageTypes.GQL_START,
            payload: {
              query: random.alphaNumeric(20),
              variables: { aField: random.alphaNumeric(5) },
              operationName: random.alphaNumeric(10),
            },
          }
        })

        it('fails if there is no "id"', async () => {
          const value = envelope.value as GraphQLStart
          value.id = undefined as any // Force "id" to be undefined
          resultPromise = websocketHandler.handle(envelope)
          await resultPromise
          expect(sendToConnection).to.be.calledOnceWithExactly(
            envelope.connectionID,
            match({
              type: MessageTypes.GQL_CONNECTION_ERROR,
              payload: `Missing "id" in ${MessageTypes.GQL_START} message`,
            })
          )
        })

        it('fails if there is no "payload"', async () => {
          const value = envelope.value as GraphQLStart
          value.payload = undefined as any
          resultPromise = websocketHandler.handle(envelope)
          await resultPromise
          expect(sendToConnection).to.be.calledOnceWithExactly(
            envelope.connectionID,
            match({
              type: MessageTypes.GQL_ERROR,
              id: value.id,
              payload: {
                errors: match.some(
                  match.has('message', 'Message payload is invalid it must contain at least the "query" property')
                ),
              },
            })
          )
        })

        it('fails if there is no "query"', async () => {
          const message = envelope.value as GraphQLStart
          message.payload.query = undefined as any
          resultPromise = websocketHandler.handle(envelope)
          await resultPromise
          expect(sendToConnection).to.be.calledOnceWithExactly(
            envelope.connectionID,
            match({
              type: MessageTypes.GQL_ERROR,
              id: message.id,
              payload: {
                errors: match.some(
                  match.has('message', 'Message payload is invalid it must contain at least the "query" property')
                ),
              },
            })
          )
        })

        it('calls "onStartOperation" with the right parameters', async () => {
          const message = envelope.value as GraphQLStart
          resultPromise = websocketHandler.handle(envelope)
          await resultPromise
          expect(onStartCallback).to.be.calledOnceWithExactly({
            ...envelope,
            value: {
              ...message.payload,
              id: message.id,
            },
          })
        })

        context('when "onStartOperation" returns the result of a subscription', () => {
          beforeEach(() => {
            onStartCallback = fake.returns({ next: () => {} })
            websocketHandler = new GraphQLWebsocketHandler(logger, sendToConnection, {
              onStartOperation: onStartCallback,
              onStopOperation: undefined as any,
              onTerminateOperations: undefined as any,
            })
          })

          it('does not send anything back', async () => {
            resultPromise = websocketHandler.handle(envelope)
            await resultPromise
            expect(sendToConnection).not.to.be.called
          })
        })

        context('when "onStartOperation" returns the result of a query or mutation', () => {
          const result = {
            data: 'The result',
          }
          beforeEach(() => {
            onStartCallback = fake.returns(result)
            websocketHandler = new GraphQLWebsocketHandler(logger, sendToConnection, {
              onStartOperation: onStartCallback,
              onStopOperation: undefined as any,
              onTerminateOperations: undefined as any,
            })
          })

          it('sends back the expected messages', async () => {
            resultPromise = websocketHandler.handle(envelope)
            await resultPromise
            const sendToConnectionFake: SinonStub = sendToConnection as any
            expect(sendToConnectionFake).to.be.calledTwice
            expect(sendToConnectionFake.getCall(0).args).to.be.deep.equal([
              envelope.connectionID,
              {
                type: MessageTypes.GQL_DATA,
                id: (envelope.value as GraphQLStart).id,
                payload: result,
              },
            ])
            expect(sendToConnectionFake.getCall(1).args).to.be.deep.equal([
              envelope.connectionID,
              {
                type: MessageTypes.GQL_COMPLETE,
                id: (envelope.value as GraphQLStart).id,
              },
            ])
          })
        })
      })
    })
  })
})
