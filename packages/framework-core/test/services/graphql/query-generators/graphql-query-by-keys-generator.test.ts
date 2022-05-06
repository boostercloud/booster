/* eslint-disable @typescript-eslint/no-explicit-any,@typescript-eslint/ban-ts-comment */
import { replace, restore, fake } from 'sinon'
import { expect } from '../../../expect'
import { GraphQLTypeInformer } from '../../../../src/services/graphql/graphql-type-informer'
import { BoosterConfig, UUID, TimeKey, Level, AnyClass, Logger } from '@boostercloud/framework-types'
import { GraphqlQueryByKeysGenerator } from '../../../../src/services/graphql/query-generators/graphql-query-by-keys-generator'
import { getLogger } from '../../../../src/booster-logger'
import { random } from 'faker'

class AnotherReadModel {
  public constructor(readonly id: UUID, readonly otherField: string) {}
}

class ASequencedReadModel {
  public constructor(readonly id: UUID, readonly timestamp: TimeKey) {}
}

class ARegularReadModel {
  readonly id: string = '∫'
}

class AnotherSequencedReadModel {
  readonly id: string = 'µ'
  readonly timestamp: string = '™'
}

describe('GraphQLQueryGenerator', () => {
  let mockEnvironmentName: string
  let mockConfig: BoosterConfig
  let mockLogger: Logger

  beforeEach(() => {
    mockEnvironmentName = random.alphaNumeric(10)
    mockConfig = new BoosterConfig(mockEnvironmentName)
    mockConfig.logLevel = Level.error
    mockLogger = getLogger(mockConfig)
  })

  afterEach(() => {
    restore()
  })

  describe('the `generateByKeysQueries` private method', () => {
    let graphqlQueryByKeysGenerator: any
    beforeEach(() => {
      const fakeReadModels = [AnotherReadModel, ASequencedReadModel] as AnyClass[]
      const typeInformer = new GraphQLTypeInformer(mockLogger)
      mockConfig.readModelSequenceKeys['ASequencedReadModel'] = 'timestamp'
      graphqlQueryByKeysGenerator = new GraphqlQueryByKeysGenerator(mockConfig, fakeReadModels, typeInformer, () =>
        fake()
      ) as any // So we can see private methods
    })

    afterEach(() => {
      restore()
    })

    it('generates by ID and sequenced queries', () => {
      const fakeGenerateByIdQuery = fake()
      replace(graphqlQueryByKeysGenerator, 'generateByIdQuery', fakeGenerateByIdQuery)
      const fakeGenerateByIdAndSequenceKeyQuery = fake()
      replace(graphqlQueryByKeysGenerator, 'generateByIdAndSequenceKeyQuery', fakeGenerateByIdAndSequenceKeyQuery)

      graphqlQueryByKeysGenerator.generateByKeysQueries()

      expect(fakeGenerateByIdQuery).to.have.been.calledOnceWith(AnotherReadModel)
      expect(fakeGenerateByIdAndSequenceKeyQuery).to.have.been.calledOnceWith(ASequencedReadModel, 'timestamp')
    })
  })

  describe('the `generateByIdQuery` private method', () => {
    let graphQLQueryGenerator: any
    beforeEach(() => {
      const fakeReadModels = [ARegularReadModel] as AnyClass[]
      const typeInformer = new GraphQLTypeInformer(mockLogger)
      graphQLQueryGenerator = new GraphqlQueryByKeysGenerator(mockConfig, fakeReadModels, typeInformer, () =>
        fake()
      ) as any // So we can see private methods
    })

    afterEach(() => {
      restore()
    })

    it('generates a query named after the read model class that accepts a unique ID', () => {
      const fakeByIdResolverBuilder = fake.returns(fake())
      replace(graphQLQueryGenerator, 'byIDResolverBuilder', fakeByIdResolverBuilder)

      const query = graphQLQueryGenerator.generateByIdQuery(ARegularReadModel)

      expect(query.type).to.has.a.property('name', 'ARegularReadModel')
      expect(query.args).to.have.a.property('id')
      expect(query.resolve).to.be.a('Function')
      expect(fakeByIdResolverBuilder).to.have.been.calledWith(ARegularReadModel)
    })
  })

  describe('the `generateByIdAndSequenceKeyQuery` private method', () => {
    let graphQLQueryGenerator: any
    beforeEach(() => {
      const fakeReadModels = [AnotherSequencedReadModel] as AnyClass[]
      const typeInformer = new GraphQLTypeInformer(mockLogger)

      graphQLQueryGenerator = new GraphqlQueryByKeysGenerator(mockConfig, fakeReadModels, typeInformer, () =>
        fake()
      ) as any // So we can see private methods
    })

    afterEach(() => {
      restore()
    })

    it('generates a query named after the read model class that accepts an ID and a sequence key', () => {
      const fakeByIdResolverBuilder = fake.returns(fake())
      replace(graphQLQueryGenerator, 'byIDResolverBuilder', fakeByIdResolverBuilder)

      const query = graphQLQueryGenerator.generateByIdAndSequenceKeyQuery(AnotherSequencedReadModel, 'timestamp')

      expect(query.type).to.be.a('GraphQLList')
      expect(query.type.ofType).to.have.a.property('name', 'AnotherSequencedReadModel')
      expect(query.args).to.have.a.property('id')
      expect(query.args).to.have.a.property('timestamp')
      expect(query.resolve).to.be.a('Function')
      expect(fakeByIdResolverBuilder).to.have.been.calledWith(AnotherSequencedReadModel)
    })
  })
})
