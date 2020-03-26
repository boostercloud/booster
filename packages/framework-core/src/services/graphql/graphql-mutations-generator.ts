import { TargetTypeMetadata, TargetTypesMap } from './common'
import { GraphQLTypeInformer } from './graphql-type-informer'
import { GraphQLBoolean, GraphQLFieldConfigMap, GraphQLObjectType, GraphQLString } from 'graphql'
import { GraphQLArgumentConfig } from 'graphql/type/definition'

type MutationResolver = (input: Record<string, any>) => Promise<void>

export class GraphQLMutationGenerator {
  public constructor(
    private readonly targetTypes: TargetTypesMap,
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly mutationResolver: MutationResolver
  ) {}

  public generate(): GraphQLObjectType {
    console.log(this.typeInformer) // TODO: remove this
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
          input: this.generateInputArgumentsFor(type),
        },
        resolve: (parent, args) => {
          return this.mutationResolver(args.input)
        },
      }
    }
    return mutations
  }

  private generateInputArgumentsFor(type: TargetTypeMetadata): GraphQLArgumentConfig {
    // TODO: Generate the input with the command data
    // const inputTypeName = `${type.class.name}Input`
    return {
      type: GraphQLString,
    }
  }
}
