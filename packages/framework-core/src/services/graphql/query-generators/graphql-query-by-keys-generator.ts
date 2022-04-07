import { GraphQLFieldConfig, GraphQLFieldConfigMap, GraphQLID, GraphQLList, GraphQLNonNull } from 'graphql'
import { GraphQLResolverContext, ResolverBuilder, TargetTypesMap } from '../common'
import { GraphQLTypeInformer } from '../graphql-type-informer'
import { BoosterConfig } from '@boostercloud/framework-types'

export class GraphqlQueryByKeysGenerator {
  public constructor(
    private readonly config: BoosterConfig,
    private readonly readModelsMetadata: TargetTypesMap,
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly byIDResolverBuilder: ResolverBuilder
  ) {}

  public generateByKeysQueries(): GraphQLFieldConfigMap<unknown, GraphQLResolverContext> {
    const queries: GraphQLFieldConfigMap<unknown, GraphQLResolverContext> = {}
    for (const readModelName in this.readModelsMetadata) {
      const sequenceKeyName = this.config.readModelSequenceKeys[readModelName]
      if (sequenceKeyName) {
        queries[readModelName] = this.generateByIdAndSequenceKeyQuery(readModelName, sequenceKeyName)
      } else {
        queries[readModelName] = this.generateByIdQuery(readModelName)
      }
    }
    return queries
  }

  private generateByIdQuery(readModelName: string): GraphQLFieldConfig<unknown, GraphQLResolverContext> {
    const readModelMetadata = this.readModelsMetadata[readModelName]
    const graphQLType = this.typeInformer.getGraphQLTypeFor(readModelMetadata.class)
    return {
      type: graphQLType,
      args: {
        id: { type: new GraphQLNonNull(GraphQLID) },
      },
      resolve: this.byIDResolverBuilder(readModelMetadata.class),
    }
  }

  private generateByIdAndSequenceKeyQuery(
    readModelName: string,
    sequenceKeyName: string
  ): GraphQLFieldConfig<unknown, GraphQLResolverContext> {
    const readModelMetadata = this.readModelsMetadata[readModelName]
    const graphQLType = this.typeInformer.getGraphQLTypeFor(readModelMetadata.class)
    return {
      type: new GraphQLList(graphQLType),
      args: {
        id: { type: new GraphQLNonNull(GraphQLID) },
        [sequenceKeyName]: { type: GraphQLID },
      },
      resolve: this.byIDResolverBuilder(readModelMetadata.class),
    }
  }
}
