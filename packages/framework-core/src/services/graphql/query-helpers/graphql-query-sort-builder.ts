import { GraphQLEnumType, GraphQLFieldConfigArgumentMap, GraphQLInputObjectType, Thunk } from 'graphql'
import { PropertyMetadata } from 'metadata-booster'
import { getPropertiesMetadata } from '../../../decorators/metadata'
import { buildGraphqlSimpleEnumFor, TargetTypeMetadata } from '../common'
import { GraphQLInputFieldConfigMap } from 'graphql/type/definition'
import { GraphQLTypeInformer } from '../graphql-type-informer'

export class GraphqlQuerySortBuilder {
  private generatedSortByByTypeName: Record<string, GraphQLInputObjectType> = {}
  private orderType = buildGraphqlSimpleEnumFor('orderProperty', ['ASC', 'DESC'])

  constructor(protected readonly typeInformer: GraphQLTypeInformer) {}

  public generateSortArguments(typeMetadata: TargetTypeMetadata): GraphQLFieldConfigArgumentMap {
    const args: GraphQLFieldConfigArgumentMap = {}
    typeMetadata.properties.forEach((prop: PropertyMetadata) => {
      args[prop.name] = {
        type: this.generateSortFor(prop),
      }
    })
    return args
  }

  private generateSortFor(prop: PropertyMetadata): GraphQLInputObjectType | GraphQLEnumType {
    let sortByName = `${prop.typeInfo.name}PropertySortBy`
    sortByName = sortByName.charAt(0).toUpperCase() + sortByName.substr(1).replace(/\[]/g, '')

    if (!prop.typeInfo.type || typeof prop.typeInfo.type === 'object') return this.orderType
    if (this.generatedSortByByTypeName[sortByName]) return this.generatedSortByByTypeName[sortByName]

    const primitiveType = this.typeInformer.getOriginalAncestor(prop.typeInfo.type)
    if (primitiveType === Array) return this.orderType
    const graphQLPropType = this.typeInformer.getGraphQLTypeFor(primitiveType)
    let fields: Thunk<GraphQLInputFieldConfigMap> = {}

    if (!this.typeInformer.isGraphQLScalarType(graphQLPropType)) {
      let nestedProperties: GraphQLInputFieldConfigMap = {}
      const properties = getPropertiesMetadata(prop.typeInfo.type)
      if (properties.length === 0) return this.orderType

      this.typeInformer.generateGraphQLTypeFromMetadata({ class: prop.typeInfo.type, properties })

      for (const prop of properties) {
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
