/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { fake, match, replace, restore, spy } from 'sinon'
import { random } from 'faker'
import { expect } from './expect'
import { BoosterConfig, Logger, GraphQLRequestEnvelope } from '@boostercloud/framework-types'
import { BoosterGraphQLDispatcher } from '../src/booster-graphql-dispatcher'
import * as gqlParser from 'graphql/language/parser'
import * as gqlValidator from 'graphql/validation/validate'
import * as gqlExecutor from 'graphql/execution/execute'
import * as gqlSubscriptor from 'graphql/subscription/subscribe'
import { GraphQLResolverContext } from '../src/services/graphql/common'
import { NoopReadModelPubSub } from '../src/services/pub-sub/noop-read-model-pub-sub'
import { GraphQLWebsocketHandler } from '../src/services/graphql/websocket-protocol/graphql-websocket-protocol'
import { ExecutionResult } from 'graphql/execution/execute'
import { GraphQLError } from 'graphql'

const logger: Logger = console

describe('the `BoosterGraphQLDispatcher`', () => {
  afterEach(() => {
    restore()
  })

  describe('the `dispatch` method', () => {
    context('on CONNECT message', () => {
      it('calls the provider "handleGraphQLResult" with the GraphQL websocket subprotocol headers', async () => {
        const config = mockConfigForGraphQLEnvelope({
          requestID: '123',
          eventType: 'CONNECT',
        })
        const dispatcher = new BoosterGraphQLDispatcher(config, logger)
        await dispatcher.dispatch({})

        expect(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly(null, {
          'Sec-WebSocket-Protocol': 'graphql-ws',
        })
      })
    })

    context('on MESSAGE message', () => {
      describe('when the message came through socket', () => {
        it('calls the websocket handler', async () => {
          const messageEnvelope: GraphQLRequestEnvelope = {
            requestID: '123',
            eventType: 'MESSAGE',
            connectionID: random.word(), // A non-null connectionID means it came through socket
          }
          const config = mockConfigForGraphQLEnvelope(messageEnvelope)

          const dispatcher = new BoosterGraphQLDispatcher(config, logger)

          const fakeWebsocketHandleMethod = fake()
          replace(GraphQLWebsocketHandler.prototype, 'handle', fakeWebsocketHandleMethod)

          await dispatcher.dispatch({})

          expect(fakeWebsocketHandleMethod).to.be.calledOnceWithExactly(messageEnvelope)
        })
      })

      describe('when the message came through HTTP', () => {
        it('does not call the websocket handler', async () => {
          const config = mockConfigForGraphQLEnvelope({
            requestID: '123',
            eventType: 'MESSAGE',
          })
          const dispatcher = new BoosterGraphQLDispatcher(config, logger)
          const fakeWebsocketHandleMethod = fake()
          replace(GraphQLWebsocketHandler.prototype, 'handle', fakeWebsocketHandleMethod)

          await dispatcher.dispatch({})

          expect(fakeWebsocketHandleMethod).not.to.be.called
        })

        it('calls the provider "handleGraphQLResult" with an error when there is an empty query', async () => {
          const config = mockConfigForGraphQLEnvelope({
            requestID: '123',
            eventType: 'MESSAGE',
          })
          const dispatcher = new BoosterGraphQLDispatcher(config, logger)

          await dispatcher.dispatch({})

          expect(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly(
            match((result) => {
              return result.errors[0].message == 'Received an empty GraphQL body'
            })
          )
        })

        it('calls the provider "handleGraphQLResult" with an error when there is an empty body', async () => {
          const config = mockConfigForGraphQLEnvelope({
            requestID: '123',
            eventType: 'MESSAGE',
            value: {
              query: undefined,
            } as any, // If not, the compiler does not allow us to provide an empty query
          })
          const dispatcher = new BoosterGraphQLDispatcher(config, logger)

          await dispatcher.dispatch({})

          expect(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly(
            match((result) => {
              return result.errors[0].message == 'Received an empty GraphQL query'
            })
          )
        })

        it('calls the provider "handleGraphQLResult" with an error when a subscription operation is used', async () => {
          const config = mockConfigForGraphQLEnvelope({
            requestID: '123',
            eventType: 'MESSAGE',
            value: {
              query: 'subscription { a { x }}',
            },
          })
          const dispatcher = new BoosterGraphQLDispatcher(config, logger)
          replace(gqlValidator, 'validate', fake.returns([]))

          await dispatcher.dispatch({})

          expect(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly(
            match((result) => {
              return new RegExp(/This API and protocol does not support "subscription" operations/).test(
                result.errors[0].message
              )
            })
          )
        })

        context('with graphql execution returning errors', () => {
          let graphQLErrorResult: ExecutionResult
          beforeEach(() => {
            replace(gqlValidator, 'validate', fake.returns([]))
            graphQLErrorResult = {
              errors: [new GraphQLError('graphql error 1'), new GraphQLError('graphql error 2')],
            }
            replace(gqlExecutor, 'execute', fake.returns(graphQLErrorResult))
            replace(gqlSubscriptor, 'subscribe', fake.returns(graphQLErrorResult))
          })

          it('calls the provider "handleGraphQLResult" with the error with a query', async () => {
            const config = mockConfigForGraphQLEnvelope({
              requestID: '123',
              eventType: 'MESSAGE',
              value: {
                query: 'query { a { x }}',
              },
            })

            const dispatcher = new BoosterGraphQLDispatcher(config, logger)
            await dispatcher.dispatch({})

            // Check that the handled error includes all the errors that GraphQL reported
            expect(config.provider.graphQL.handleResult).to.have.been.calledWithExactly(graphQLErrorResult)
          })

          it('calls the provider "handleGraphQLResult" with the error with a mutation', async () => {
            const config = mockConfigForGraphQLEnvelope({
              requestID: '123',
              eventType: 'MESSAGE',
              value: {
                query: 'mutation { a { x }}',
              },
            })

            const dispatcher = new BoosterGraphQLDispatcher(config, logger)
            await dispatcher.dispatch({})

            // Check that the handled error includes all the errors that GraphQL reported
            expect(config.provider.graphQL.handleResult).to.have.been.calledWithExactly(graphQLErrorResult)
          })
        })

        it('calls the the GraphQL engine with the passed envelope and handles the result', async () => {
          const graphQLBody = 'query { a { x }}'
          const graphQLResult = { data: 'the result' }
          const graphQLVariables = { productId: 'productId' }
          const graphQLEnvelope: GraphQLRequestEnvelope = {
            requestID: '123',
            eventType: 'MESSAGE',
            value: {
              query: graphQLBody,
              variables: graphQLVariables,
            },
          }
          const resolverContext: GraphQLResolverContext = {
            requestID: graphQLEnvelope.requestID,
            operation: {
              query: graphQLBody,
              variables: graphQLVariables,
            },
            pubSub: new NoopReadModelPubSub(),
            storeSubscriptions: true,
          }
          const config = mockConfigForGraphQLEnvelope(graphQLEnvelope)
          const dispatcher = new BoosterGraphQLDispatcher(config, logger)
          const executeFake = fake.returns(graphQLResult)
          const parseSpy = spy(gqlParser.parse)
          replace(gqlParser, 'parse', parseSpy)
          replace(gqlValidator, 'validate', fake.returns([]))
          replace(gqlExecutor, 'execute', executeFake)

          await dispatcher.dispatch({})

          expect(parseSpy).to.have.been.calledWithExactly(graphQLBody)
          expect(executeFake).to.have.been.calledWithExactly({
            schema: match.any,
            document: match.any,
            contextValue: match(resolverContext),
            variableValues: match(graphQLVariables),
            operationName: match.any,
          })
          expect(config.provider.graphQL.handleResult).to.have.been.calledWithExactly(graphQLResult)
        })
      })
    })
  })
})

function mockConfigForGraphQLEnvelope(envelope: GraphQLRequestEnvelope): BoosterConfig {
  const config = new BoosterConfig('test')
  config.provider = {
    graphQL: {
      rawToEnvelope: fake.resolves(envelope),
      handleResult: fake(),
    },
    readModels: {
      notifySubscription: fake(),
    },
  } as any
  return config
}
