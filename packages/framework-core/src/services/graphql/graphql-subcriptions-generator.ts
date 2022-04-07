import { GraphQLFieldConfigMap, GraphQLID, GraphQLInputObjectType, GraphQLNonNull, GraphQLObjectType } from 'graphql'
import { ResolverBuilder, TargetTypesMap } from './common'
import { GraphQLTypeInformer } from './graphql-type-informer'
import * as inflected from 'inflected'
import { GraphqlQueryFilterFieldsBuilder } from './query-helpers/graphql-query-filter-fields-builder'

export class GraphQLSubscriptionGenerator {
  private graphqlQueryFilterFieldsBuilder: GraphqlQueryFilterFieldsBuilder

  public constructor(
    private readonly targetTypes: TargetTypesMap,
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly byIDResolverBuilder: ResolverBuilder,
    private readonly filterResolverBuilder: ResolverBuilder,
    protected generatedFiltersByTypeName: Record<string, GraphQLInputObjectType> = {}
  ) {
    this.graphqlQueryFilterFieldsBuilder = new GraphqlQueryFilterFieldsBuilder(typeInformer, generatedFiltersByTypeName)
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
    for (const name in this.targetTypes) {
      const type = this.targetTypes[name]
      const graphQLType = this.typeInformer.getGraphQLTypeFor(type.class)
      subscriptions[name] = {
        type: graphQLType,
        args: {
          id: { type: new GraphQLNonNull(GraphQLID) },
        },
        resolve: (source) => source,
        subscribe: this.byIDResolverBuilder(type.class),
      }
    }
    return subscriptions
  }

  private generateFilterSubscriptions(): GraphQLFieldConfigMap<any, any> {
    const subscriptions: GraphQLFieldConfigMap<any, any> = {}
    for (const name in this.targetTypes) {
      const type = this.targetTypes[name]
      const graphQLType = this.typeInformer.getGraphQLTypeFor(type.class)
      subscriptions[inflected.pluralize(name)] = {
        type: graphQLType,
        args: this.graphqlQueryFilterFieldsBuilder.generateFilterQueriesFields(`${name}Subscription`, type),
        resolve: (source) => source,
        subscribe: this.filterResolverBuilder(type.class),
      }
    }
    return subscriptions
  }
}
