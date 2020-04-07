import { TargetTypesMap } from './common'
import { GraphQLTypeInformer } from './graphql-type-informer'
import { GraphQLBoolean, GraphQLFieldConfigMap, GraphQLFieldResolver, GraphQLObjectType } from 'graphql'
import { AnyClass, GraphQLRequestEnvelope } from '@boostercloud/framework-types'

export type MutationResolver = (commandClass: AnyClass) => GraphQLFieldResolver<any, GraphQLRequestEnvelope, any>

export class GraphQLMutationGenerator {
  public constructor(
    private readonly targetTypes: TargetTypesMap,
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly mutationResolver: MutationResolver
  ) {}

  public generate(): GraphQLObjectType {
    const mutations = this.generateMutations()
    return new GraphQLObjectType({
      name: 'Mutation',
      fields: mutations,
    })
  }

  private generateMutations(): GraphQLFieldConfigMap<any, any> {
    const mutations: GraphQLFieldConfigMap<any, any> = {}
    for (const name in this.targetTypes) {
      const type = this.targetTypes[name]
      mutations[name] = {
        type: GraphQLBoolean, // TODO: Return the request ID an useful information
        args: {
          input: {
            type: this.typeInformer.getGraphQLInputTypeFor(type.class),
          },
        },
        resolve: this.mutationResolver(type.class),
      }
    }
    return mutations
  }
}
