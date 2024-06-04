import { GraphQLFieldConfig, GraphQLFieldConfigMap, GraphQLID, GraphQLList, GraphQLNonNull } from 'graphql'
import { GraphQLResolverContext, ResolverBuilder } from '../common'
import { AnyClass, BoosterConfig } from '@boostercloud/framework-types'
import { GraphQLTypeInformer } from '../graphql-type-informer'

export class GraphqlQueryByKeysGenerator {
  public constructor(
    private readonly config: BoosterConfig,
    private readonly readModels: AnyClass[],
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly byIDResolverBuilder: ResolverBuilder
  ) {}

  public generateByKeysQueries(): GraphQLFieldConfigMap<unknown, GraphQLResolverContext> {
    const queries: GraphQLFieldConfigMap<unknown, GraphQLResolverContext> = {}
    for (const readModel of this.readModels) {
      const readModelName = readModel.name
      const sequenceKeyName = this.config.readModelSequenceKeys[readModelName]
      if (sequenceKeyName) {
        queries[readModelName] = this.generateByIdAndSequenceKeyQuery(readModel, sequenceKeyName)
      } else {
        queries[readModelName] = this.generateByIdQuery(readModel)
      }
    }
    return queries
  }

  private generateByIdQuery(readModel: AnyClass): GraphQLFieldConfig<unknown, GraphQLResolverContext> {
    const graphQLType = this.typeInformer.generateGraphQLTypeForClass(
      readModel,
      this.config.nonExposedGraphQLMetadataKey[readModel.name]
    )
    return {
      type: graphQLType,
      args: {
        id: { type: new GraphQLNonNull(GraphQLID) },
      },
      resolve: this.byIDResolverBuilder(readModel),
    }
  }

  private generateByIdAndSequenceKeyQuery(
    readModel: AnyClass,
    sequenceKeyName: string
  ): GraphQLFieldConfig<unknown, GraphQLResolverContext> {
    const graphQLType = this.typeInformer.generateGraphQLTypeForClass(
      readModel,
      this.config.nonExposedGraphQLMetadataKey[readModel.name]
    )
    return {
      type: new GraphQLList(graphQLType),
      args: {
        id: { type: new GraphQLNonNull(GraphQLID) },
        [sequenceKeyName]: { type: GraphQLID },
      },
      resolve: this.byIDResolverBuilder(readModel),
    }
  }
}
