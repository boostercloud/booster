import { GraphQLEnumType, GraphQLFieldConfigArgumentMap, GraphQLInputObjectType, ThunkObjMap } from 'graphql'
import { PropertyMetadata } from '@boostercloud/metadata-booster'
import { getClassMetadata } from '../../../decorators/metadata'
import { buildGraphqlSimpleEnumFor, isExternalType, nonExcludedFields } from '../common'
import { GraphQLInputFieldConfig } from 'graphql/type/definition'
import { GraphQLTypeInformer } from '../graphql-type-informer'
import { AnyClass, BoosterConfig } from '@boostercloud/framework-types'

export class GraphqlQuerySortBuilder {
  private generatedSortByByTypeName: Record<string, GraphQLInputObjectType> = {}
  private orderType = buildGraphqlSimpleEnumFor('orderProperty', ['ASC', 'DESC'])

  constructor(protected readonly typeInformer: GraphQLTypeInformer, protected readonly config: BoosterConfig) {}

  public generateSortArguments(type: AnyClass, excludeProps: Array<string>): GraphQLFieldConfigArgumentMap {
    const metadata = getClassMetadata(type)
    const args: GraphQLFieldConfigArgumentMap = {}
    const finalFields: Array<PropertyMetadata> = nonExcludedFields(metadata.fields, excludeProps)
    finalFields
      .filter((field: PropertyMetadata) => !field.typeInfo.isGetAccessor)
      .forEach((prop: PropertyMetadata) => {
        args[prop.name] = {
          type: this.generateSortFor(prop),
        }
      })
    return args
  }

  private generateSortFor(prop: PropertyMetadata): GraphQLInputObjectType | GraphQLEnumType {
    let sortByName = `${prop.typeInfo.name}PropertySortBy`
    sortByName = sortByName.charAt(0).toUpperCase() + sortByName.substring(1).replace(/\[]/g, '')

    if (this.generatedSortByByTypeName[sortByName]) return this.generatedSortByByTypeName[sortByName]
    if (!prop.typeInfo.type || typeof prop.typeInfo.type === 'object') return this.orderType
    if (prop.typeInfo.typeGroup === 'Array') return this.orderType
    if (prop.typeInfo.name === 'UUID' || prop.typeInfo.name === 'Date') return this.orderType

    let fields: ThunkObjMap<GraphQLInputFieldConfig> = {}

    if (prop.typeInfo.type && prop.typeInfo.typeGroup === 'Class') {
      if (isExternalType(prop.typeInfo)) return this.orderType
      let nestedProperties: ThunkObjMap<GraphQLInputFieldConfig> = {}
      const metadata = getClassMetadata(prop.typeInfo.type)
      if (metadata.fields.length === 0) return this.orderType
      const excludeProps = this.config.nonExposedGraphQLMetadataKey[prop.name]
      this.typeInformer.generateGraphQLTypeForClass(prop.typeInfo.type, excludeProps, true)

      for (const prop of metadata.fields) {
        const property = { [prop.name]: { type: this.generateSortFor(prop) } }
        nestedProperties = { ...nestedProperties, ...property }
      }
      fields = () => ({
        ...nestedProperties,
      })
      this.generatedSortByByTypeName[sortByName] = new GraphQLInputObjectType({ name: sortByName, fields })
      return this.generatedSortByByTypeName[sortByName]
    }

    return this.orderType
  }
}
