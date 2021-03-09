import { GraphQLFieldConfigMap, GraphQLID, GraphQLNonNull, GraphQLObjectType } from 'graphql'
import { ResolverBuilder, TargetTypesMap } from './common'
import { GraphQLTypeInformer } from './graphql-type-informer'
import { GraphQLQueryGenerator } from './graphql-query-generator'
import * as inflected from 'inflected'

export class GraphQLSubscriptionGenerator {
  public constructor(
    private readonly targetTypes: TargetTypesMap,
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly queryGenerator: GraphQLQueryGenerator,
    private readonly byIDResolverBuilder: ResolverBuilder,
    private readonly filterResolverBuilder: ResolverBuilder
  ) {}

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
        args: this.queryGenerator.generateFilterQueriesFields(`${name}Subscription`, type),
        resolve: (source) => source,
        subscribe: this.filterResolverBuilder(type.class),
      }
    }
    return subscriptions
  }
}
