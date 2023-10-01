import { GraphQLFieldConfigMap, GraphQLNonNull } from 'graphql'
import { ResolverBuilder, TargetTypeMetadata, TargetTypesMap } from '../common'
import { TypeMetadata } from '@boostercloud/metadata-booster'
import { GraphQLTypeInformer } from '../graphql-type-informer'
import { BoosterConfig } from '@boostercloud/framework-types'

export class GraphQLHandledFieldsGenerator {
  constructor(
    private readonly targetTypes: TargetTypesMap,
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly resolver: ResolverBuilder,
    private readonly config: BoosterConfig
  ) {}

  public generateFields(allowVoidReturn = true): GraphQLFieldConfigMap<any, any> {
    const fields: GraphQLFieldConfigMap<any, any> = {}
    for (const name in this.targetTypes) {
      const metadata = this.targetTypes[name]
      const handleMethodMetadata = GraphQLHandledFieldsGenerator.getHandleMethodMetadata(metadata)
      const returnMetadata = GraphQLHandledFieldsGenerator.getReturnMetadata(handleMethodMetadata, allowVoidReturn)
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
        type: new GraphQLNonNull(
          this.typeInformer.generateGraphQLTypeForClass(
            metadata.class,
            this.config.nonExposedGraphQLMetadataKey[metadata.class.name],
            true
          )
        ),
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

  private static getReturnMetadata(
    handleMethodMetadata: TypeMetadata | undefined,
    allowVoidReturn: boolean
  ): TypeMetadata {
    if (!handleMethodMetadata || handleMethodMetadata.name === 'never') {
      if (!allowVoidReturn) {
        throw Error('Unexpected void return type for Query')
      }
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
