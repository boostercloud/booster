import { TargetTypeMetadata } from '../common'
import {
  GraphQLBoolean,
  GraphQLFieldConfigArgumentMap,
  GraphQLFloat,
  GraphQLInputFieldConfigMap,
  GraphQLInputObjectType,
  GraphQLList,
  GraphQLScalarType,
  GraphQLString,
  Thunk,
} from 'graphql'
import { getPropertiesMetadata } from '../../../decorators/metadata'
import { PropertyMetadata } from 'metadata-booster'
import { GraphQLJSONObject } from 'graphql-type-json'
import { AnyClass } from '@boostercloud/framework-types'
import { GraphQLTypeInformer } from '../graphql-type-informer'

export class GraphqlQueryFilterArgumentsBuilder {
  constructor(
    private readonly typeInformer: GraphQLTypeInformer,
    protected generatedFiltersByTypeName: Record<string, GraphQLInputObjectType> = {}
  ) {}

  public generateFilterArguments(typeMetadata: TargetTypeMetadata): GraphQLFieldConfigArgumentMap {
    const args: GraphQLFieldConfigArgumentMap = {}
    typeMetadata.properties.forEach((prop: PropertyMetadata) => {
      args[prop.name] = {
        type: this.generateFilterFor(prop),
      }
    })
    return args
  }

  private generateFilterFor(prop: PropertyMetadata): GraphQLInputObjectType | GraphQLScalarType {
    const filterName = `${prop.typeInfo.name}PropertyFilter`
    if (!prop.typeInfo.type || typeof prop.typeInfo.type === 'object') return GraphQLJSONObject

    if (!this.generatedFiltersByTypeName[filterName]) {
      const primitiveType = this.typeInformer.getOriginalAncestor(prop.typeInfo.type)
      if (primitiveType === Array) return this.generateArrayFilterFor(prop)
      const graphQLPropType = this.typeInformer.getGraphQLTypeFor(primitiveType)
      let fields: Thunk<GraphQLInputFieldConfigMap> = {}

      if (!this.typeInformer.isGraphQLScalarType(graphQLPropType)) {
        let nestedProperties: GraphQLInputFieldConfigMap = {}
        const properties = getPropertiesMetadata(prop.typeInfo.type)
        if (properties.length > 0) {
          this.typeInformer.generateGraphQLTypeFromMetadata({ class: prop.typeInfo.type, properties })

          for (const prop of properties) {
            const property = { [prop.name]: { type: this.generateFilterFor(prop) } }
            nestedProperties = { ...nestedProperties, ...property }
          }
        } else {
          return GraphQLJSONObject
        }
        fields = () => ({
          ...nestedProperties,
          and: { type: new GraphQLList(this.generatedFiltersByTypeName[filterName]) },
          or: { type: new GraphQLList(this.generatedFiltersByTypeName[filterName]) },
          not: { type: this.generatedFiltersByTypeName[filterName] },
          isDefined: { type: GraphQLBoolean },
        })
      } else {
        fields = this.generateFilterInputTypes(prop.typeInfo.type)
      }
      this.generatedFiltersByTypeName[filterName] = new GraphQLInputObjectType({ name: filterName, fields })
    }
    return this.generatedFiltersByTypeName[filterName]
  }

  private generateArrayFilterFor(property: PropertyMetadata): GraphQLInputObjectType {
    const filterName = `${property.name}PropertyFilter`

    if (!this.generatedFiltersByTypeName[filterName]) {
      const propFilters: GraphQLInputFieldConfigMap = {}
      property.typeInfo.parameters.forEach((param) => {
        const primitiveType = this.typeInformer.getOriginalAncestor(param.type)
        let graphqlType: GraphQLScalarType
        switch (primitiveType) {
          case Boolean:
            graphqlType = GraphQLBoolean
            break
          case String:
            graphqlType = GraphQLString
            break
          case Number:
            graphqlType = GraphQLFloat
            break
          default:
            graphqlType = GraphQLJSONObject
            break
        }
        propFilters.includes = { type: graphqlType }
      })
      propFilters.isDefined = { type: GraphQLBoolean }

      this.generatedFiltersByTypeName[filterName] = new GraphQLInputObjectType({
        name: filterName,
        fields: propFilters,
      })
    }
    return this.generatedFiltersByTypeName[filterName]
  }

  private generateFilterInputTypes(type: AnyClass): GraphQLInputFieldConfigMap {
    const primitiveType = this.typeInformer.getOriginalAncestor(type)
    switch (primitiveType) {
      case Boolean:
        return {
          eq: { type: GraphQLBoolean },
          ne: { type: GraphQLBoolean },
          isDefined: { type: GraphQLBoolean },
        }
      case Number:
        return {
          eq: { type: GraphQLFloat },
          ne: { type: GraphQLFloat },
          lte: { type: GraphQLFloat },
          lt: { type: GraphQLFloat },
          gte: { type: GraphQLFloat },
          gt: { type: GraphQLFloat },
          in: { type: GraphQLList(GraphQLFloat) },
          isDefined: { type: GraphQLBoolean },
        }
      case String:
        return {
          eq: { type: GraphQLString },
          ne: { type: GraphQLString },
          lte: { type: GraphQLString },
          lt: { type: GraphQLString },
          gte: { type: GraphQLString },
          gt: { type: GraphQLString },
          in: { type: GraphQLList(GraphQLString) },
          beginsWith: { type: GraphQLString },
          contains: { type: GraphQLString },
          isDefined: { type: GraphQLBoolean },
        }
      default:
        throw new Error(`Type ${type.name} is not supported in search filters`)
    }
  }
}
