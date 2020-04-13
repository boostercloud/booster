/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { fake, match, replace, restore } from 'sinon'
import * as chai from 'chai'
import { expect } from 'chai'
import { BoosterConfig, Logger, GraphQLRequestEnvelope, InvalidParameterError } from '@boostercloud/framework-types'
import { BoosterGraphQLDispatcher } from '../src/booster-graphql-dispatcher'
import * as GraphQL from 'graphql/graphql'

chai.use(require('sinon-chai'))

const logger: Logger = console

describe('the `BoosterGraphQLDispatcher`', () => {
  afterEach(() => {
    restore()
  })

  describe('the `dispatchGraphQL` method', () => {
    it('calls the provider "handleGraphQLError" when there is an empty body', async () => {
      const config = mockConfigForGraphQLEnvelope({
        requestID: '123',
        eventType: 'MESSAGE',
        value: undefined,
      })
      const dispatcher = new BoosterGraphQLDispatcher(config, logger)

      await dispatcher.dispatch({})

      expect(config.provider.handleGraphQLResult).to.not.have.been.called
      expect(config.provider.handleGraphQLError).to.have.been.calledOnceWithExactly(
        match.instanceOf(InvalidParameterError)
      )
    })

    it('calls the provider "handleGraphQLError" when there is an error with the graphql execution', async () => {
      const config = mockConfigForGraphQLEnvelope({
        requestID: '123',
        eventType: 'MESSAGE',
        value: 'a graphql query',
      })
      const dispatcher = new BoosterGraphQLDispatcher(config, logger)
      const errorTextOne = 'graphql error 1'
      const errorTextTwo = 'graphql error 2'
      replace(
        GraphQL,
        'graphql',
        fake.returns({
          errors: [new Error(errorTextOne), new Error(errorTextTwo)],
        })
      )

      await dispatcher.dispatch({})

      // Check that the handled error includes all the errors that GraphQL reported
      const errorOneRegExp = new RegExp(errorTextOne)
      const errorTwoRegExp = new RegExp(errorTextOne)
      expect(config.provider.handleGraphQLResult).to.not.have.been.called
      expect(config.provider.handleGraphQLError).to.have.been.calledWithExactly(
        match((value: Error): boolean => {
          return errorOneRegExp.test(value.message) && errorTwoRegExp.test(value.message)
        }, `an error with a message including the substrings "${errorTextOne}" and "${errorTextTwo}"`)
      )
    })

    it('calls the the GraphQL engine with the passed envelope and handles the result', async () => {
      const graphQLBody = 'a graphql query'
      const graphQLResult = 'the result'
      const graphQLEnvelope: GraphQLRequestEnvelope = {
        requestID: '123',
        eventType: 'MESSAGE',
        value: graphQLBody,
      }
      const config = mockConfigForGraphQLEnvelope(graphQLEnvelope)
      const dispatcher = new BoosterGraphQLDispatcher(config, logger)
      const graphqlFake = fake.returns({ data: graphQLResult })
      replace(GraphQL, 'graphql', graphqlFake)

      await dispatcher.dispatch({})

      expect(config.provider.handleGraphQLError).to.not.have.been.called
      expect(graphqlFake).to.have.been.calledWithExactly({
        schema: match.any,
        source: graphQLBody,
        contextValue: match(graphQLEnvelope),
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
