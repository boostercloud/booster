/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { fake, match, replace, restore, spy } from 'sinon'
import * as chai from 'chai'
import { expect } from 'chai'
import { BoosterConfig, Logger, GraphQLRequestEnvelope } from '@boostercloud/framework-types'
import { BoosterGraphQLDispatcher } from '../src/booster-graphql-dispatcher'
import * as gqlParser from 'graphql/language/parser'
import * as gqlValidator from 'graphql/validation/validate'
import * as gqlExecutor from 'graphql/execution/execute'
import { GraphQLResolverContext } from '../src/services/graphql/common'
import { NoopReadModelPubSub } from '../src/services/pub-sub/noop-read-model-pub-sub'

chai.use(require('sinon-chai'))

const logger: Logger = console

describe('the `BoosterGraphQLDispatcher`', () => {
  afterEach(() => {
    restore()
  })

  describe('the `dispatchGraphQL` method', () => {
    it('calls the provider "handleGraphQLResult" with an error when there is an empty body', async () => {
      const config = mockConfigForGraphQLEnvelope({
        requestID: '123',
        eventType: 'MESSAGE',
        value: undefined,
      })
      const dispatcher = new BoosterGraphQLDispatcher(config, logger)

      await dispatcher.dispatch({})

      expect(config.provider.handleGraphQLResult).to.have.been.calledOnce
    })

    it('calls the provider "handleGraphQLResult" when there is an error with the graphql execution', async () => {
      const config = mockConfigForGraphQLEnvelope({
        requestID: '123',
        eventType: 'MESSAGE',
        value: 'query { a { x }}',
      })
      const dispatcher = new BoosterGraphQLDispatcher(config, logger)
      const errorTextOne = 'graphql error 1'
      const errorTextTwo = 'graphql error 2'
      replace(gqlValidator, 'validate', fake())
      const graphQLError = {
        errors: [new Error(errorTextOne), new Error(errorTextTwo)],
      }
      replace(gqlExecutor, 'execute', fake.returns(graphQLError))

      await dispatcher.dispatch({})

      // Check that the handled error includes all the errors that GraphQL reported
      expect(config.provider.handleGraphQLResult).to.have.been.calledWithExactly(graphQLError)
    })

    it('calls the the GraphQL engine with the passed envelope and handles the result', async () => {
      const graphQLBody = 'query { a { x }}'
      const graphQLResult = { data: 'the result' }
      const graphQLEnvelope: GraphQLRequestEnvelope = {
        requestID: '123',
        eventType: 'MESSAGE',
        value: graphQLBody,
      }
      const resolverContext: GraphQLResolverContext = {
        requestID: graphQLEnvelope.requestID,
        operation: {
          query: graphQLBody,
        },
        pubSub: new NoopReadModelPubSub(),
        storeSubscriptions: true,
      }
      const config = mockConfigForGraphQLEnvelope(graphQLEnvelope)
      const dispatcher = new BoosterGraphQLDispatcher(config, logger)
      const executeFake = fake.returns(graphQLResult)
      const parseSpy = spy(gqlParser.parse)
      replace(gqlParser, 'parse', parseSpy)
      replace(gqlValidator, 'validate', fake())
      replace(gqlExecutor, 'execute', executeFake)

      await dispatcher.dispatch({})

      expect(parseSpy).to.have.been.calledWithExactly(graphQLBody)
      expect(executeFake).to.have.been.calledWithExactly({
        schema: match.any,
        document: match.any,
        contextValue: match(resolverContext),
      })
      expect(config.provider.handleGraphQLResult).to.have.been.calledWithExactly(graphQLResult)
    })
  })
})

function mockConfigForGraphQLEnvelope(envelope: GraphQLRequestEnvelope): BoosterConfig {
  const config = new BoosterConfig('test')
  config.provider = {
    rawGraphQLRequestToEnvelope: fake.resolves(envelope),
    handleGraphQLError: fake(),
    handleGraphQLResult: fake(),
  } as any
  return config
}
