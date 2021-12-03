import { ResolverBuilder, TargetTypesMap } from './common'
import { GraphQLTypeInformer } from './graphql-type-informer'
import { GraphQLFieldConfigMap, GraphQLObjectType, GraphQLNonNull } from 'graphql'
import { TypeGroup, TypeMetadata } from '../../metadata-types'

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
      const metadata = this.targetTypes[name]
      let handleMethodMetadata = metadata.methods.find((m) => m.name === 'handle')?.typeInfo
      let returnMetadata: TypeMetadata = {
        name: 'Boolean',
        typeGroup: TypeGroup.Boolean,
        typeName: 'Boolean',
        isNullable: false,
        parameters: [],
      }
      if (handleMethodMetadata) {
        if (handleMethodMetadata.typeName === 'Promise') {
          // If async function, return type is wrapped in a Promise
          handleMethodMetadata = handleMethodMetadata.parameters[0]
        }
        if (handleMethodMetadata.name !== 'never') {
          // `never` means the return type is `void`, otherwise we've returned something ourselves
          returnMetadata = handleMethodMetadata
        }
      }
      mutations[name] = {
        type: this.typeInformer.getOrCreateGraphQLType(returnMetadata, false),
        args: {
          input: {
            type: new GraphQLNonNull(this.typeInformer.generateGraphQLTypeForClass(metadata.class, true)),
          },
        },
        resolve: this.mutationResolver(metadata.class),
      }
    }
    return mutations
  }
}
