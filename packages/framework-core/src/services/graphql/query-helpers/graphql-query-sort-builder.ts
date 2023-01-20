import { GraphQLEnumType, GraphQLFieldConfigArgumentMap, GraphQLInputObjectType, Thunk } from 'graphql'
import { PropertyMetadata } from '@boostercloud/metadata-booster'
import { getClassMetadata } from '../../../decorators/metadata'
import { buildGraphqlSimpleEnumFor, isExternalType } from '../common'
import { GraphQLInputFieldConfigMap } from 'graphql/type/definition'
import { GraphQLTypeInformer } from '../graphql-type-informer'
import { AnyClass } from '@boostercloud/framework-types'

export class GraphqlQuerySortBuilder {
  private generatedSortByByTypeName: Record<string, GraphQLInputObjectType> = {}
  private orderType = buildGraphqlSimpleEnumFor('orderProperty', ['ASC', 'DESC'])

  constructor(protected readonly typeInformer: GraphQLTypeInformer) {}

  public generateSortArguments(type: AnyClass): GraphQLFieldConfigArgumentMap {
    const metadata = getClassMetadata(type)
    const args: GraphQLFieldConfigArgumentMap = {}
    metadata.fields
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

    let fields: Thunk<GraphQLInputFieldConfigMap> = {}

    if (prop.typeInfo.type && prop.typeInfo.typeGroup === 'Class') {
      if (isExternalType(prop.typeInfo)) return this.orderType
      let nestedProperties: GraphQLInputFieldConfigMap = {}
      const metadata = getClassMetadata(prop.typeInfo.type)
      if (metadata.fields.length === 0) return this.orderType

      this.typeInformer.generateGraphQLTypeForClass(prop.typeInfo.type, true)

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
