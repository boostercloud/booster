import { AnyClass } from '@boostercloud/framework-types'
import {
  GraphQLFieldConfigArgumentMap,
  GraphQLInputFieldConfigMap,
  GraphQLInputObjectType,
  GraphQLNonNull,
  GraphQLScalarType,
  Thunk,
} from 'graphql'
import { PropertyMetadata } from 'metadata-booster'
import { buildGraphqlSimpleEnumFor, isExternalType } from '../common'
import { GraphQLTypeInformer } from '../graphql-type-informer'
import { getClassMetadata } from '../../../decorators/metadata'
import { GraphQLJSONObject } from 'graphql-type-json'

export class GraphqlQuerySortBuilder {
  private generatedSortByByTypeName: Record<string, GraphQLInputObjectType> = {}
  private orderType = buildGraphqlSimpleEnumFor('orderProperty', ['ASC', 'DESC'])

  constructor(private readonly typeInformer: GraphQLTypeInformer) {}

  public generateSortArguments(type: AnyClass): GraphQLFieldConfigArgumentMap {
    const metadata = getClassMetadata(type)
    const args: GraphQLFieldConfigArgumentMap = {}
    metadata.fields.forEach((prop: PropertyMetadata) => {
      args[prop.name] = {
        type: this.generateSortFor(prop),
      }
    })
    return args
  }

  private generateSortFor(prop: PropertyMetadata): GraphQLInputObjectType | GraphQLScalarType {
    let sortByName = `${prop.typeInfo.name}PropertySortBy`
    sortByName = sortByName.charAt(0).toUpperCase() + sortByName.substr(1).replace(/\[]/g, '')

    if (this.generatedSortByByTypeName[sortByName]) return this.generatedSortByByTypeName[sortByName]
    let fields: Thunk<GraphQLInputFieldConfigMap> = {}
    //     checks: {
    //       checks: ASC
    //     },
    // TODO debe ser checks: ASC solamente

    if (prop.typeInfo.name === 'UUID' || prop.typeInfo.name === 'Date' || prop.typeInfo.typeGroup === 'Array') {
      fields = { [prop.name]: { type: this.orderType } }
    } else if (prop.typeInfo.type && prop.typeInfo.typeGroup === 'Class') {
      if (isExternalType(prop.typeInfo)) return GraphQLJSONObject // TODO ?
      let nestedProperties: GraphQLInputFieldConfigMap = {}
      const metadata = getClassMetadata(prop.typeInfo.type)
      if (metadata.fields.length === 0) return GraphQLJSONObject

      this.typeInformer.generateGraphQLTypeForClass(prop.typeInfo.type, true)

      for (const prop of metadata.fields) {
        const property = { [prop.name]: { type: this.orderType } }
        nestedProperties = { ...nestedProperties, ...property }
      }
      fields = () => ({
        ...nestedProperties,
      })
    } else if (prop.typeInfo.type && prop.typeInfo.type.name !== 'Object') {
      fields = { [prop.name]: { type: this.orderType } }
    } else {
      return GraphQLJSONObject
    }

    this.generatedSortByByTypeName[sortByName] = new GraphQLInputObjectType({ name: sortByName, fields })
    return this.generatedSortByByTypeName[sortByName]
  }
}
