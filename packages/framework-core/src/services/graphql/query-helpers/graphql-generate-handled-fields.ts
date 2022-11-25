import { GraphQLFieldConfigMap, GraphQLNonNull } from 'graphql'
import { ResolverBuilder, TargetTypeMetadata, TargetTypesMap } from '../common'
import { TypeMetadata } from '@boostercloud/metadata-booster'
import { GraphQLTypeInformer } from '../graphql-type-informer'

export class GraphqlGenerateHandledFields {
  constructor(
    private readonly targetTypes: TargetTypesMap,
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly resolver: ResolverBuilder
  ) {}

  public generateFields(): GraphQLFieldConfigMap<any, any> {
    const fields: GraphQLFieldConfigMap<any, any> = {}
    for (const name in this.targetTypes) {
      const metadata = this.targetTypes[name]
      const handleMethodMetadata = GraphqlGenerateHandledFields.getHandleMethodMetadata(metadata)
      const returnMetadata = GraphqlGenerateHandledFields.getReturnMetadata(handleMethodMetadata)
      const type = this.typeInformer.getOrCreateGraphQLType(returnMetadata, false)
      fields[name] = {
        type: type,
        resolve: this.resolver(metadata.class),
      }
      const input = this.generateInputForType(metadata)
      if (input) {
        fields[name].args = { ...input }
      }
    }
    return fields
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
        parameters: [],
      } as TypeMetadata
    }
    return handleMethodMetadata
  }
}