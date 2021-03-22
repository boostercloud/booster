import { ResolverBuilder, TargetTypesMap } from './common'
import { GraphQLTypeInformer } from './graphql-type-informer'
import { GraphQLBoolean, GraphQLFieldConfigMap, GraphQLObjectType, GraphQLNonNull } from 'graphql'

export class GraphQLMutationGenerator {
  public constructor(
    private readonly targetTypes: TargetTypesMap,
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly mutationResolver: ResolverBuilder
  ) {}

  public generate(): GraphQLObjectType | undefined {
    const mutations = this.generateMutations()
    if (Object.keys(mutations).length === 0) {
      return undefined
    }
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
            type: new GraphQLNonNull(this.typeInformer.getGraphQLInputTypeFor(type.class)),
          },
        },
        resolve: this.mutationResolver(type.class),
      }
    }
    return mutations
  }
}
