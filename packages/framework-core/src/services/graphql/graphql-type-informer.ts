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
      fields[prop.name] = { type: this.getGraphQLTypeFor(prop.typeInfo.type) }
    }
    return fields
  }

  public canFilter(graphQLType: GraphQLNonInputType): boolean {
    return graphQLType instanceof GraphQLScalarType && graphQLType != GraphQLJSONObject
  }

  public getPrimitiveExtendedType(type: AnyClass): AnyClass {
    if (!type.prototype) return type
    const parentType = Object.getPrototypeOf(type.prototype)?.constructor
    return parentType === Object ? type : parentType
  }

  public getGraphQLTypeFor(type: AnyClass): GraphQLNonInputType {
    switch (type) {
      case UUID:
        return GraphQLID
      case String:
        return GraphQLString
      case Number:
        return GraphQLFloat
      case Boolean:
        return GraphQLBoolean
      case Array:
        return new GraphQLList(GraphQLJSONObject)
      case Object:
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

  private generateOperationEnumValuesFor(operationsEnum: AnyClass): GraphQLEnumValueConfigMap {
    const enumValuesConfig: GraphQLEnumValueConfigMap = {}
    for (const opSymbol in operationsEnum) {
      const opName = (operationsEnum as any)[opSymbol]
      enumValuesConfig[opName] = { value: opSymbol }
    }
    return enumValuesConfig
  }
}
