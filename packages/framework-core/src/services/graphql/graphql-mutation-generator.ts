import { ResolverBuilder, TargetTypeMetadata, TargetTypesMap } from './common'
import { GraphQLTypeInformer } from './graphql-type-informer'
import { GraphQLFieldConfigMap, GraphQLObjectType, GraphQLNonNull } from 'graphql'

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
      const input = this.generateInputForType(type)
      mutations[name] = {
        type: this.typeInformer.getGraphQLTypeFor(type.returnClass ?? Boolean),
        args: {
          ...input,
        },
        resolve: this.mutationResolver(type.class),
      }
    }
    return mutations
  }

  private generateInputForType(type: TargetTypeMetadata): any {
    if (type.properties.length === 0) return {}
    return {
      input: {
        type: new GraphQLNonNull(this.typeInformer.getGraphQLInputTypeFor(type.class)),
      },
    }
  }
}
