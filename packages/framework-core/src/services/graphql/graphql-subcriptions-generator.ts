import { GraphQLFieldConfigMap, GraphQLID, GraphQLInputObjectType, GraphQLNonNull, GraphQLObjectType } from 'graphql'
import { ResolverBuilder } from './common'
import { GraphQLTypeInformer } from './graphql-type-informer'
import * as inflected from 'inflected'
import { AnyClass, BoosterConfig } from '@boostercloud/framework-types'
import { GraphqlQueryFilterFieldsBuilder } from './query-helpers/graphql-query-filter-fields-builder'

export class GraphQLSubscriptionGenerator {
  private graphqlQueryFilterFieldsBuilder: GraphqlQueryFilterFieldsBuilder

  public constructor(
    private readonly readModels: AnyClass[],
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly byIDResolverBuilder: ResolverBuilder,
    private readonly filterResolverBuilder: ResolverBuilder,
    protected generatedFiltersByTypeName: Record<string, GraphQLInputObjectType> = {},
    private readonly config: BoosterConfig
  ) {
    this.graphqlQueryFilterFieldsBuilder = new GraphqlQueryFilterFieldsBuilder(
      typeInformer,
      generatedFiltersByTypeName,
      config
    )
  }

  public generate(): GraphQLObjectType | undefined {
    const byIDSubscriptions = this.generateByIDSubscriptions()
    const filterSubscriptions = this.generateFilterSubscriptions()
    const fields = { ...byIDSubscriptions, ...filterSubscriptions }
    if (Object.keys(fields).length === 0) {
      return undefined
    }
    return new GraphQLObjectType({
      name: 'Subscription',
      fields: fields,
    })
  }

  private generateByIDSubscriptions(): GraphQLFieldConfigMap<any, any> {
    const subscriptions: GraphQLFieldConfigMap<any, any> = {}
    for (const readModel of this.readModels) {
      const excludeProps = this.config.nonExposedGraphQLMetadataKey[readModel.name]
      const graphQLType = this.typeInformer.generateGraphQLTypeForClass(readModel, excludeProps)
      subscriptions[readModel.name] = {
        type: graphQLType,
        args: {
          id: { type: new GraphQLNonNull(GraphQLID) },
        },
        resolve: (source) => source,
        subscribe: this.byIDResolverBuilder(readModel),
      }
    }
    return subscriptions
  }

  private generateFilterSubscriptions(): GraphQLFieldConfigMap<any, any> {
    const subscriptions: GraphQLFieldConfigMap<any, any> = {}
    for (const readModel of this.readModels) {
      const excludeProps = this.config.nonExposedGraphQLMetadataKey[readModel.name]
      const graphQLType = this.typeInformer.generateGraphQLTypeForClass(readModel, excludeProps)
      subscriptions[inflected.pluralize(readModel.name)] = {
        type: graphQLType,
        args: this.graphqlQueryFilterFieldsBuilder.generateFilterQueriesFields(
          `${readModel.name}Subscription`,
          readModel,
          excludeProps
        ),
        resolve: (source) => source,
        subscribe: this.filterResolverBuilder(readModel),
      }
    }
    return subscriptions
  }
}
