import { GraphQLJSONObject } from 'graphql-type-json'
import { GraphQLNonInputType, TargetTypeMetadata, TargetTypesMap } from './common'
import { AnyClass, UUID } from '@boostercloud/framework-types'
import {
  GraphQLFieldConfigMap,
  GraphQLList,
  GraphQLObjectType,
  GraphQLBoolean,
  GraphQLFloat,
  GraphQLID,
  GraphQLString,
  GraphQLInputType,
  GraphQLScalarType,
  GraphQLInputObjectType,
  GraphQLOutputType,
  GraphQLNonNull,
  GraphQLEnumType,
  GraphQLInterfaceType,
} from 'graphql'
import { GraphQLFieldMap, GraphQLInputFieldConfigMap } from 'graphql/type/definition'
import { PropertyMetadata } from 'metadata-booster'
import { getPropertiesMetadata } from './../../decorators/metadata'

export class GraphQLTypeInformer {
  private graphQLTypesByName: Record<string, GraphQLNonInputType> = {}

  public constructor(private readonly typesByName: TargetTypesMap) {
    for (const name in this.typesByName) {
      this.generateGraphQLTypeFromMetadata(this.typesByName[name])
    }
  }

  public generateGraphQLTypeFromMetadata(typeMetadata: TargetTypeMetadata): void {
    const name = typeMetadata.class.name
    if (!this.graphQLTypesByName[name]) {
      this.graphQLTypesByName[name] = new GraphQLObjectType({
        name: typeMetadata.class.name,
        fields: () => this.metadataPropertiesToGraphQLFields(typeMetadata.properties),
      })
    }
  }

  private metadataPropertiesToGraphQLFields(properties: Array<PropertyMetadata>): GraphQLFieldConfigMap<any, any> {
    const fields: GraphQLFieldConfigMap<any, any> = {}
    for (const prop of properties) {
      const primitiveType = this.getOriginalAncestor(prop.typeInfo.type)

      if (primitiveType === Array) {
        const param = prop.typeInfo.parameters[0]
        let graphQLPropType = this.getGraphQLTypeFor(param.type)

        if (!this.isGraphQLScalarType(graphQLPropType) && param.type) {
          const properties = getPropertiesMetadata(param.type)
          this.generateGraphQLTypeFromMetadata({ class: param.type, properties })
          graphQLPropType = this.getGraphQLTypeFor(param.type)
        }

        fields[prop.name] = {
          type: GraphQLList(graphQLPropType),
        }
      } else {
        fields[prop.name] = { type: this.getGraphQLTypeFor(prop.typeInfo.type) }
      }
    }
    return fields
  }

  public isGraphQLScalarType(graphQLType: GraphQLNonInputType): boolean {
    return graphQLType instanceof GraphQLScalarType && graphQLType != GraphQLJSONObject
  }

  public getOriginalAncestor(type: AnyClass): AnyClass {
    if (!type) return type

    const typePrototype = Object.getPrototypeOf(type)
    if (typePrototype === Function.prototype) {
      return type // End of the prototype chain, return the type
    }
    return this.getOriginalAncestor(typePrototype)
  }

  public getGraphQLTypeFor(type: AnyClass): GraphQLNonInputType {
    if (type === UUID) return GraphQLID
    const primitiveType = this.getOriginalAncestor(type)
    switch (primitiveType) {
      case String:
        return GraphQLString
      case Number:
        return GraphQLFloat
      case Boolean:
        return GraphQLBoolean
      case undefined:
        return GraphQLJSONObject
      default:
        if (this.graphQLTypesByName[type.name]) {
          return this.graphQLTypesByName[type.name]
        }
        if (this.typesByName[type.name]) {
          this.generateGraphQLTypeFromMetadata(this.typesByName[type.name])
          return this.graphQLTypesByName[type.name]
        }
        return GraphQLJSONObject
    }
  }

  public getGraphQLInputTypeFor(type: AnyClass): GraphQLInputType {
    return this.toInputType(this.getGraphQLTypeFor(type))
  }

  public toInputType(graphQLType: GraphQLOutputType): GraphQLInputType {
    if (graphQLType instanceof GraphQLScalarType || graphQLType instanceof GraphQLEnumType) {
      return graphQLType
    }
    if (graphQLType instanceof GraphQLList) {
      return new GraphQLList(this.toInputType(graphQLType.ofType))
    }
    if (graphQLType instanceof GraphQLNonNull) {
      return new GraphQLNonNull(graphQLType.ofType)
    }
    if (graphQLType instanceof GraphQLObjectType) {
      return new GraphQLInputObjectType({
        name: `${graphQLType.name}Input`,
        fields: () => this.toInputFields(graphQLType.getFields()),
      })
    }
    throw new Error(
      `Types '${GraphQLEnumType.name}' and '${GraphQLInterfaceType}' are not allowed as input type, ` +
        `and '${graphQLType.name}' was found`
    )
  }

  private toInputFields(fields: GraphQLFieldMap<any, any>): GraphQLInputFieldConfigMap {
    const inputFields: GraphQLInputFieldConfigMap = {}
    for (const fieldName in fields) {
      inputFields[fieldName] = {
        type: this.toInputType(fields[fieldName].type),
      }
    }
    return inputFields
  }
}
