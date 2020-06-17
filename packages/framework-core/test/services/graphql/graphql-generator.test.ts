import { SinonStubbedInstance, replace, fake, SinonSpy, restore } from 'sinon'
import { BoosterCommandDispatcher } from '../../../src/booster-command-dispatcher'
import sinon = require('sinon')
import { BoosterReadModelDispatcher } from '../../../src/booster-read-model-dispatcher'
import { GraphQLGenerator } from '../../../src/services/graphql/graphql-generator'
import { BoosterConfig } from '@boostercloud/framework-types/dist'
import { expect } from '../../expect'
import { GraphQLQueryGenerator } from '../../../src/services/graphql/graphql-query-generator'
import { GraphQLMutationGenerator } from '../../../src/services/graphql/graphql-mutation-generator'
import { GraphQLSubscriptionGenerator } from '../../../src/services/graphql/graphql-subcriptions-generator'

describe('GraphQL generator', () => {
  let configStub: SinonStubbedInstance<BoosterConfig>
  let commandDispatcherStub: SinonStubbedInstance<BoosterCommandDispatcher>
  let readModelDispatcherStub: SinonStubbedInstance<BoosterReadModelDispatcher>

  let sut: GraphQLGenerator

  beforeEach(() => {
    configStub = sinon.createStubInstance(BoosterConfig)
    commandDispatcherStub = sinon.createStubInstance(BoosterCommandDispatcher)
    readModelDispatcherStub = sinon.createStubInstance(BoosterReadModelDispatcher)

    sut = new GraphQLGenerator(configStub as any, commandDispatcherStub, readModelDispatcherStub as any)
  })

  afterEach(() => {
    restore()
  })

  describe('generateSchema', () => {
    let fakeQueryGenerator: SinonSpy
    let fakeMutationGenerator: SinonSpy
    let fakeSubscriptionGenerator: SinonSpy

    beforeEach(() => {
      fakeQueryGenerator = fake()
      fakeMutationGenerator = fake()
      fakeSubscriptionGenerator = fake()

      replace(GraphQLQueryGenerator.prototype, 'generate', fakeQueryGenerator)
      replace(GraphQLMutationGenerator.prototype, 'generate', fakeMutationGenerator)
      replace(GraphQLSubscriptionGenerator.prototype, 'generate', fakeSubscriptionGenerator)
    })

    it('should call QueryGenerator', () => {
      sut.generateSchema()

      expect(fakeQueryGenerator).to.have.been.calledOnceWithExactly()
    })

    it('should call MutationGenerator', () => {
      sut.generateSchema()

      expect(fakeMutationGenerator).to.have.been.calledOnceWithExactly()
    })

    it('should call SubscriptionGenerator', () => {
      sut.generateSchema()

      expect(fakeSubscriptionGenerator).to.have.been.calledOnceWithExactly()
    })

    it('should return a GraphQL schema', () => {
      // TODO
    })
  })
})
