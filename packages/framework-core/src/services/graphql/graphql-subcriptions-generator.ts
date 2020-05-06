import { GraphQLFieldConfigMap, GraphQLID, GraphQLNonNull, GraphQLObjectType } from 'graphql'
import { ResolverBuilder, TargetTypesMap } from './common'
import { GraphQLTypeInformer } from './graphql-type-informer'
import { GraphQLQueryGenerator } from './graphql-query-generator'
import * as inflection from 'inflection'

export class GraphQLSubscriptionGenerator {
  public constructor(
    private readonly targetTypes: TargetTypesMap,
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly queryGenerator: GraphQLQueryGenerator,
    private readonly byIDResolverBuilder: ResolverBuilder,
    private readonly filterResolverBuilder: ResolverBuilder
  ) {}

  public generate(): GraphQLObjectType {
    const byIDSubscriptions = this.generateByIDSubscriptions()
    const filterSubscriptions = this.generateFilterSubscriptions()
    return new GraphQLObjectType({
      name: 'Subscription',
      fields: {
        ...byIDSubscriptions,
        ...filterSubscriptions,
      },
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
      subscriptions[inflection.pluralize(name)] = {
        type: graphQLType,
        args: this.queryGenerator.generateFilterArguments(type),
        resolve: (source) => source,
        subscribe: this.filterResolverBuilder(type.class),
      }
    }
    return subscriptions
  }
}
