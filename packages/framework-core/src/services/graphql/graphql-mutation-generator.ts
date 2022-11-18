import { ResolverBuilder, TargetTypeMetadata, TargetTypesMap } from './common'
import { GraphQLTypeInformer } from './graphql-type-informer'
import { GraphQLFieldConfigMap, GraphQLNonNull, GraphQLObjectType } from 'graphql'
import { TypeMetadata } from '@boostercloud/metadata-booster'

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
      const handleMethodMetadata = GraphQLMutationGenerator.getHandleMethodMetadata(metadata)
      const returnMetadata = GraphQLMutationGenerator.getReturnMetadata(handleMethodMetadata)
      const type = this.typeInformer.getOrCreateGraphQLType(returnMetadata, false)
      mutations[name] = {
        type: type,
        resolve: this.mutationResolver(metadata.class),
      }
      const input = this.generateInputForType(metadata)
      if (input) {
        mutations[name].args = { ...input }
      }
    }
    return mutations
  }

  private generateInputForType(metadata: TargetTypeMetadata): any {
    if (metadata.properties.length === 0) return undefined
    return {
      input: {
        type: new GraphQLNonNull(this.typeInformer.generateGraphQLTypeForClass(metadata.class, true)),
      },
    }
  }

  private static getHandleMethodMetadata(metadata: TargetTypeMetadata): TypeMetadata | undefined {
    let handleMethodMetadata = metadata.methods.find((m) => m.name === 'handle')?.typeInfo
    if (handleMethodMetadata && handleMethodMetadata.typeName === 'Promise') {
      // If async function, return type is wrapped in a Promise
      handleMethodMetadata = handleMethodMetadata.parameters[0]
    }
    return handleMethodMetadata
  }

  private static getReturnMetadata(handleMethodMetadata: TypeMetadata | undefined): TypeMetadata {
    // `never` means the return type is `void`, otherwise we've returned something ourselves
    if (!handleMethodMetadata || handleMethodMetadata.name === 'never') {
      return {
        name: 'Boolean',
        typeGroup: 'Boolean',
        typeName: 'Boolean',
        isNullable: false,
        isGetAccessor: false,
        parameters: [],
      } as TypeMetadata
    }
    return handleMethodMetadata
  }
}
