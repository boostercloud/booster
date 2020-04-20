import { GraphQLFieldConfigMap, GraphQLObjectType } from 'graphql'
import { ResolverBuilder, TargetTypesMap } from './common'
import { GraphQLTypeInformer } from './graphql-type-informer'
import { GraphQLQueryGenerator } from './graphql-query-generator'

export class GraphQLSubscriptionGenerator {
  public constructor(
    private readonly targetTypes: TargetTypesMap,
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly queryGenerator: GraphQLQueryGenerator,
    private readonly resolverBuilder: ResolverBuilder
  ) {}

  public generate(): GraphQLObjectType {
    return new GraphQLObjectType({
      name: 'Subscription',
      fields: this.generateSubscriptions(),
    })
  }

  private generateSubscriptions(): GraphQLFieldConfigMap<any, any> {
    const subscriptions: GraphQLFieldConfigMap<any, any> = {}
    for (const name in this.targetTypes) {
      const type = this.targetTypes[name]
      const graphQLType = this.typeInformer.getGraphQLTypeFor(type.class)
      subscriptions[name] = {
        type: graphQLType,
        args: this.queryGenerator.generateFilterArguments(type),
        resolve: (source, args, context, info) => {
          console.log('Resolve method from a subscription called')
          console.log('source: ', source)
          console.log('args: ', args)
          console.log('context: ', context)
          console.log('info: ', info)
        },
        subscribe: this.resolverBuilder(type.class),
      }
    }
    return subscriptions
  }
}
