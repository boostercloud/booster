/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { fake, match, replace, restore, spy } from 'sinon'
import { random, lorem, internet } from 'faker'
import { expect } from './expect'
import {
  BoosterConfig,
  GraphQLRequestEnvelope,
  GraphQLRequestEnvelopeError,
  UserEnvelope,
} from '@boostercloud/framework-types'
import { BoosterGraphQLDispatcher } from '../src/booster-graphql-dispatcher'
import * as gqlParser from 'graphql/language/parser'
import * as gqlValidator from 'graphql/validation/validate'
import * as gqlExecutor from 'graphql/execution/execute'
import * as gqlSubscriptor from 'graphql/execution/subscribe'
import { GraphQLResolverContext } from '../src/services/graphql/common'
import { NoopReadModelPubSub } from '../src/services/pub-sub/noop-read-model-pub-sub'
import { GraphQLWebsocketHandler } from '../src/services/graphql/websocket-protocol/graphql-websocket-protocol'
import { ExecutionResult } from 'graphql/execution/execute'
import { GraphQLError } from 'graphql'
import { BoosterTokenVerifier } from '../src/booster-token-verifier'

describe('the `BoosterGraphQLDispatcher`', () => {
  afterEach(() => {
    restore()
  })

  describe('Introspection in graphQL API', () => {
    context('on introspection message', () => {
      it('with default config introspection is enabled', async () => {
        const graphQLResult = { data: 'the result' }
        const messageEnvelope: GraphQLRequestEnvelope = {
          requestID: random.uuid(),
          eventType: 'MESSAGE',
          value: {
            query: '{__schema {queryType {name},mutationType { name }  }}',
            variables: {},
            operationName: undefined,
          },
        }

        const config = mockConfigForGraphQLEnvelope(messageEnvelope)
        const dispatcher = new BoosterGraphQLDispatcher(config)
        const parseSpy = spy(gqlParser.parse)
        replace(gqlParser, 'parse', parseSpy)
        replace(gqlValidator, 'validate', fake.returns([]))
        const executeFake = fake.returns(graphQLResult as any)
        replace(gqlExecutor, 'execute', executeFake)

        await dispatcher.dispatch({})

        expect(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly(graphQLResult, {})
      })

      it('override the introspection configuration and disable it', async () => {
        const graphQLResult = { data: 'the result' }
        const messageEnvelope: GraphQLRequestEnvelope = {
          requestID: random.uuid(),
          eventType: 'MESSAGE',
          value: {
            query: '{__schema {queryType {name},mutationType { name }  }}',
            variables: {},
            operationName: undefined,
          },
        }

        const config = mockConfigForGraphQLEnvelope(messageEnvelope)
        config.enableGraphQLIntrospection = false
        const dispatcher = new BoosterGraphQLDispatcher(config)
        const parseSpy = spy(gqlParser.parse)
        replace(gqlParser, 'parse', parseSpy)
        replace(gqlValidator, 'validate', fake.returns([]))
        const executeFake = fake.returns(graphQLResult as any)
        replace(gqlExecutor, 'execute', executeFake)

        await dispatcher.dispatch({})

        expect(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly(
          match((result) => {
            return (
              result.errors[0].message ==
              'Instrospection queries are disabled. Check the configuration if you want to enable them.'
            )
          }),
          {}
        )
      })
    })
  })

  describe('the `dispatch` method', () => {
    context('on CONNECT message', () => {
      it('calls the provider "handleGraphQLResult" with the GraphQL websocket subprotocol headers', async () => {
        const config = mockConfigForGraphQLEnvelope({
          requestID: random.uuid(),
          eventType: 'CONNECT',
        })
        const dispatcher = new BoosterGraphQLDispatcher(config)
        await dispatcher.dispatch({})

        expect(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly(null, {
          'Sec-WebSocket-Protocol': 'graphql-ws',
        })
      })
    })

    context('on DISCONNECT message', () => {
      it('does does not delete connection or subscription data when there is no connection ID', async () => {
        const config = mockConfigForGraphQLEnvelope({
          requestID: random.uuid(),
          eventType: 'DISCONNECT',
          connectionID: undefined,
        })
        const dispatcher = new BoosterGraphQLDispatcher(config)
        await dispatcher.dispatch({})

        expect(config.provider.readModels.deleteAllSubscriptions).not.to.have.been.called
        expect(config.provider.connections.deleteData).not.to.have.been.called
        expect(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly(undefined)
      })

      it('calls deletes connection and subscription data', async () => {
        const mockConnectionID = random.uuid()
        const config = mockConfigForGraphQLEnvelope({
          requestID: random.uuid(),
          eventType: 'DISCONNECT',
          connectionID: mockConnectionID,
        })
        const dispatcher = new BoosterGraphQLDispatcher(config)
        await dispatcher.dispatch({})

        expect(config.provider.connections.deleteData).to.have.been.calledOnceWithExactly(config, mockConnectionID)
        expect(config.provider.readModels.deleteAllSubscriptions).to.have.been.calledOnceWithExactly(
          config,
          mockConnectionID
        )
        expect(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly(undefined)
      })
    })

    context('on MESSAGE message', () => {
      describe('when the message came through socket', () => {
        it('calls the websocket handler', async () => {
          const messageEnvelope: GraphQLRequestEnvelope = {
            requestID: random.uuid(),
            eventType: 'MESSAGE',
            connectionID: random.uuid(), // A non-null connectionID means it came through socket
          }
          const config = mockConfigForGraphQLEnvelope(messageEnvelope)

          const dispatcher = new BoosterGraphQLDispatcher(config)

          const fakeWebsocketHandleMethod = fake()
          replace(GraphQLWebsocketHandler.prototype, 'handle', fakeWebsocketHandleMethod)

          await dispatcher.dispatch({})

          expect(fakeWebsocketHandleMethod).to.be.calledOnceWithExactly(messageEnvelope)
        })
      })

      describe('when the message came through HTTP', () => {
        it('does not call the websocket handler', async () => {
          const config = mockConfigForGraphQLEnvelope({
            requestID: random.uuid(),
            eventType: 'MESSAGE',
          })
          const dispatcher = new BoosterGraphQLDispatcher(config)
          const fakeWebsocketHandleMethod = fake()
          replace(GraphQLWebsocketHandler.prototype, 'handle', fakeWebsocketHandleMethod)

          await dispatcher.dispatch({})

          expect(fakeWebsocketHandleMethod).not.to.be.called
        })

        it('calls the provider "handleGraphQLResult" when the envelope contains errors', async () => {
          const errorMessage = lorem.sentences(1)
          const config = mockConfigForGraphQLEnvelope({
            requestID: random.uuid(),
            eventType: 'MESSAGE',
            error: new Error(errorMessage),
          })
          const dispatcher = new BoosterGraphQLDispatcher(config)

          await dispatcher.dispatch({})

          expect(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly(
            match((result) => {
              return result.errors[0].message == errorMessage
            }),
            {}
          )
        })

        it('calls the provider "handleGraphQLResult" with an error when there is an empty query', async () => {
          const config = mockConfigForGraphQLEnvelope({
            requestID: random.uuid(),
            eventType: 'MESSAGE',
          })
          const dispatcher = new BoosterGraphQLDispatcher(config)

          await dispatcher.dispatch({})

          expect(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly(
            match((result) => {
              return result.errors[0].message == 'Received an empty GraphQL body'
            }),
            {}
          )
        })

        it('calls the provider "handleGraphQLResult" with an error when there is an empty body', async () => {
          const config = mockConfigForGraphQLEnvelope({
            requestID: random.uuid(),
            eventType: 'MESSAGE',
            value: {
              query: undefined,
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
            } as any, // If not, the compiler does not allow us to provide an empty query
          })
          const dispatcher = new BoosterGraphQLDispatcher(config)

          await dispatcher.dispatch({})

          expect(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly(
            match((result) => {
              return result.errors[0].message == 'Received an empty GraphQL query'
            }),
            {}
          )
        })

        it('calls the provider "handleGraphQLResult" with an error when a subscription operation is used', async () => {
          const errorRegex = /This API and protocol does not support "subscription" operations/
          const config = mockConfigForGraphQLEnvelope({
            requestID: random.uuid(),
            eventType: 'MESSAGE',
            value: {
              query: 'subscription { a { x }}',
            },
          })
          const dispatcher = new BoosterGraphQLDispatcher(config)
          replace(gqlValidator, 'validate', fake.returns([]))

          //await expect(dispatcher.dispatch({})).to.be.rejectedWith(errorRegex)
          await dispatcher.dispatch({})

          expect(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly(
            match((result) => {
              return new RegExp(errorRegex).test(result.errors[0].message)
            }),
            {}
          )
        })

        it('calls the the GraphQL engine with the passed envelope and handles the result', async () => {
          const graphQLBody = 'query { a { x }}'
          const graphQLResult = { data: 'the result' }
          const graphQLVariables = { productId: 'productId' }
          const graphQLEnvelope: GraphQLRequestEnvelope = {
            requestID: random.uuid(),
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
            responseHeaders: {},
          }
          const config = mockConfigForGraphQLEnvelope(graphQLEnvelope)
          const dispatcher = new BoosterGraphQLDispatcher(config)
          const executeFake = fake.returns(graphQLResult as any)
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
          expect(config.provider.graphQL.handleResult).to.have.been.calledWithExactly(graphQLResult, {})
        })

        it('calls the the GraphQL engine with the passed envelope and handles the result including the `responseHeaders`', async () => {
          const graphQLBody = 'query { a { x }}'
          const graphQLResult = { data: 'the result' }
          const graphQLVariables = { productId: 'productId' }
          const graphQLEnvelope: GraphQLRequestEnvelope = {
            requestID: random.uuid(),
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
            responseHeaders: {},
          }
          const config = mockConfigForGraphQLEnvelope(graphQLEnvelope)
          const dispatcher = new BoosterGraphQLDispatcher(config)
          const parseSpy = spy(gqlParser, 'parse')
          replace(gqlValidator, 'validate', fake.returns([]))

          const executeFake = fake((params: any) => {
            // Simulates that the handler has added the `responseHeaders`
            params.contextValue.responseHeaders['Test-Header'] = 'Test-Value'
            return graphQLResult as any
          })
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
          expect(config.provider.graphQL.handleResult).to.have.been.calledWithExactly(graphQLResult, {
            'Test-Header': 'Test-Value',
          })
        })

        it('calls the the GraphQL engine with the passed envelope with an authorization token and handles the result', async () => {
          const graphQLBody = 'query { a { x }}'
          const graphQLResult = { data: 'the result' }
          const graphQLVariables = { productId: 'productId' }
          const graphQLEnvelope: GraphQLRequestEnvelope = {
            requestID: random.uuid(),
            eventType: 'MESSAGE',
            value: {
              query: graphQLBody,
              variables: graphQLVariables,
            },
            currentUser: undefined,
            token: random.uuid(),
          }
          const resolverContext: GraphQLResolverContext = {
            requestID: graphQLEnvelope.requestID,
            operation: {
              query: graphQLBody,
              variables: graphQLVariables,
            },
            pubSub: new NoopReadModelPubSub(),
            storeSubscriptions: true,
            responseHeaders: {},
          }

          const currentUser: UserEnvelope = {
            username: internet.email(),
            roles: [random.word()],
            claims: {},
          }

          const config = mockConfigForGraphQLEnvelope(graphQLEnvelope)
          const dispatcher = new BoosterGraphQLDispatcher(config)
          const executeFake = fake.returns(graphQLResult as any)
          const parseSpy = spy(gqlParser.parse)
          replace(gqlParser, 'parse', parseSpy)
          replace(gqlValidator, 'validate', fake.returns([]))
          replace(gqlExecutor, 'execute', executeFake)

          const fakeVerifier = fake.returns(currentUser as any)
          replace(BoosterTokenVerifier.prototype, 'verify', fakeVerifier)
          resolverContext.user = currentUser

          await dispatcher.dispatch({})

          expect(fakeVerifier).to.have.been.calledWithExactly(graphQLEnvelope.token)
          expect(parseSpy).to.have.been.calledWithExactly(graphQLBody)
          expect(executeFake).to.have.been.calledWithExactly({
            schema: match.any,
            document: match.any,
            contextValue: match(resolverContext),
            variableValues: match(graphQLVariables),
            operationName: match.any,
          })
          expect(config.provider.graphQL.handleResult).to.have.been.calledWithExactly(graphQLResult, {})
        })

        context('with graphql execution returning errors', () => {
          let graphQLErrorResult: ExecutionResult
          beforeEach(() => {
            replace(gqlValidator, 'validate', fake.returns([]))
            graphQLErrorResult = {
              errors: [new GraphQLError('graphql error 1'), new GraphQLError('graphql error 2')],
            }
            replace(gqlExecutor, 'execute', fake.returns(graphQLErrorResult))
            replace(gqlSubscriptor, 'subscribe', fake.resolves(graphQLErrorResult))
          })

          it('calls the provider "handleGraphQLResult" with the error with a query', async () => {
            const config = mockConfigForGraphQLEnvelope({
              requestID: random.uuid(),
              eventType: 'MESSAGE',
              value: {
                query: 'query { a { x }}',
              },
            })

            const dispatcher = new BoosterGraphQLDispatcher(config)
            await dispatcher.dispatch({})

            // Check that the handled error includes all the errors that GraphQL reported
            expect(config.provider.graphQL.handleResult).to.have.been.calledWithExactly(graphQLErrorResult, {})
          })

          it('calls the provider "handleGraphQLResult" with the error with a mutation', async () => {
            const config = mockConfigForGraphQLEnvelope({
              requestID: random.uuid(),
              eventType: 'MESSAGE',
              value: {
                query: 'mutation { a { x }}',
              },
            })

            const dispatcher = new BoosterGraphQLDispatcher(config)
            await dispatcher.dispatch({})

            // Check that the handled error includes all the errors that GraphQL reported
            expect(config.provider.graphQL.handleResult).to.have.been.calledWithExactly(graphQLErrorResult, {})
          })
        })
      })
    })
  })
})

function mockConfigForGraphQLEnvelope(envelope: GraphQLRequestEnvelope | GraphQLRequestEnvelopeError): BoosterConfig {
  const config = new BoosterConfig('test')
  config.provider = {
    graphQL: {
      rawToEnvelope: fake.resolves(envelope),
      handleResult: fake(),
    },
    readModels: {
      notifySubscription: fake(),
      deleteAllSubscriptions: fake(),
    },
    connections: {
      storeData: fake(),
      fetchData: fake(),
      deleteData: fake(),
      sendMessage: fake(),
    },
  } as any
  return config
}
