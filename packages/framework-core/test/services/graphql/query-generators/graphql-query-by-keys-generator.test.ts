/* eslint-disable @typescript-eslint/no-explicit-any,@typescript-eslint/ban-ts-comment */
import { replace, restore, fake } from 'sinon'
import { expect } from '../../../expect'
import { GraphQLTypeInformer } from '../../../../src/services/graphql/graphql-type-informer'
import { TargetTypesMap } from '../../../../src/services/graphql/common'
import { BoosterConfig, UUID, TimeKey } from '@boostercloud/framework-types'
import { GraphqlQueryByKeysGenerator } from '../../../../src/services/graphql/query-generators/graphql-query-by-keys-generator'

describe('GraphQLQueryGenerator', () => {
  afterEach(() => {
    restore()
  })

  describe('the `generateByKeysQueries` private method', () => {
    class AnotherReadModel {
      public constructor(readonly id: UUID, readonly otherField: string) {}
    }

    class ASequencedReadModel {
      public constructor(readonly id: UUID, readonly timestamp: TimeKey) {}
    }

    const fakeReadModelsMetadata: TargetTypesMap = {
      AnotherReadModel: {
        class: AnotherReadModel,
        properties: [],
      },
      ASequencedReadModel: {
        class: ASequencedReadModel,
        properties: [],
      },
    }

    const typeInformer = new GraphQLTypeInformer({
      ...fakeReadModelsMetadata,
    })

    const config = new BoosterConfig('test')
    config.readModelSequenceKeys['ASequencedReadModel'] = 'timestamp'

    const graphqlQueryByKeysGenerator = new GraphqlQueryByKeysGenerator(
      config,
      fakeReadModelsMetadata,
      typeInformer,
      () => fake()
    ) as any // So we can see private methods

    it('generates by ID and sequenced queries', () => {
      const fakeGenerateByIdQuery = fake()
      replace(graphqlQueryByKeysGenerator, 'generateByIdQuery', fakeGenerateByIdQuery)
      const fakeGenerateByIdAndSequenceKeyQuery = fake()
      replace(graphqlQueryByKeysGenerator, 'generateByIdAndSequenceKeyQuery', fakeGenerateByIdAndSequenceKeyQuery)

      graphqlQueryByKeysGenerator.generateByKeysQueries()

      expect(fakeGenerateByIdQuery).to.have.been.calledOnceWith('AnotherReadModel')
      expect(fakeGenerateByIdAndSequenceKeyQuery).to.have.been.calledOnceWith('ASequencedReadModel', 'timestamp')
    })
  })

  describe('the `generateByIdQuery` private method', () => {
    class ARegularReadModel {
      readonly id: string = '∫'
    }

    const fakeReadModelsMetadata: TargetTypesMap = {
      ARegularReadModel: {
        class: ARegularReadModel,
        properties: [],
      },
    }

    const typeInformer = new GraphQLTypeInformer({
      ...fakeReadModelsMetadata,
    })

    const config = new BoosterConfig('test')

    const graphQLQueryGenerator = new GraphqlQueryByKeysGenerator(config, fakeReadModelsMetadata, typeInformer, () =>
      fake()
    ) as any // So we can see private methods

    it('generates a query named after the read model class that accepts a unique ID', () => {
      const fakeByIdResolverBuilder = fake.returns(fake())
      replace(graphQLQueryGenerator, 'byIDResolverBuilder', fakeByIdResolverBuilder)

      const query = graphQLQueryGenerator.generateByIdQuery('ARegularReadModel')

      expect(query.type).to.has.a.property('name', 'ARegularReadModel')
      expect(query.args).to.have.a.property('id')
      expect(query.resolve).to.be.a('Function')
      expect(fakeByIdResolverBuilder).to.have.been.calledWith(ARegularReadModel)
    })
  })

  describe('the `generateByIdAndSequenceKeyQuery` private method', () => {
    class AnotherSequencedReadModel {
      readonly id: string = 'µ'
      readonly timestamp: string = '™'
    }

    const fakeReadModelsMetadata: TargetTypesMap = {
      AnotherSequencedReadModel: {
        class: AnotherSequencedReadModel,
        properties: [],
      },
    }

    const typeInformer = new GraphQLTypeInformer({
      ...fakeReadModelsMetadata,
    })

    const config = new BoosterConfig('test')

    const graphQLQueryGenerator = new GraphqlQueryByKeysGenerator(config, fakeReadModelsMetadata, typeInformer, () =>
      fake()
    ) as any // So we can see private methods
    it('generates a query named after the read model class that accepts an ID and a sequence key', () => {
      const fakeByIdResolverBuilder = fake.returns(fake())
      replace(graphQLQueryGenerator, 'byIDResolverBuilder', fakeByIdResolverBuilder)

      const query = graphQLQueryGenerator.generateByIdAndSequenceKeyQuery('AnotherSequencedReadModel', 'timestamp')

      expect(query.type).to.be.a('GraphQLList')
      expect(query.type.ofType).to.have.a.property('name', 'AnotherSequencedReadModel')
      expect(query.args).to.have.a.property('id')
      expect(query.args).to.have.a.property('timestamp')
      expect(query.resolve).to.be.a('Function')
      expect(fakeByIdResolverBuilder).to.have.been.calledWith(AnotherSequencedReadModel)
    })
  })
})
