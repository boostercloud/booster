import {
  AnyClass,
  BoosterConfig,
  PropertyMetadata,
  UUID,
  BooleanOperations,
  NumberOperations,
  StringOperations,
} from '@boostercloud/framework-types'
import {
  GraphQLBoolean,
  GraphQLEnumType,
  GraphQLFloat,
  GraphQLID,
  GraphQLInputObjectType,
  GraphQLObjectType,
  GraphQLScalarType,
  GraphQLSchema,
  GraphQLString,
} from 'graphql'
import { GraphQLJSONObject } from 'graphql-type-json'
import {
  GraphQLEnumValueConfigMap,
  GraphQLFieldConfigArgumentMap,
  GraphQLFieldConfigMap,
  GraphQLFieldResolver,
  GraphQLList,
  GraphQLNonNull,
} from 'graphql/type/definition'
import { Booster } from '../booster'
import * as inflection from 'inflection'

type TargetTypesMap = Record<string, TargetTypeMetadata>
interface TargetTypeMetadata {
  class: AnyClass
  properties: Array<PropertyMetadata>
}

export class GraphQLGenerator {
  private generatedFiltersByTypeName: Record<string, GraphQLInputObjectType> = {}
  private generatedOperationEnumsByTypeName: Record<string, GraphQLEnumType> = {}
  private targetTypes: TargetTypesMap

  public constructor(config: BoosterConfig) {
    this.targetTypes = config.readModels
  }

  public generateSchema(): GraphQLSchema {
    const typesByName = this.generateGraphQLTypesByName()
    const query = this.generateQuery(typesByName)
    return new GraphQLSchema({
      query,
      types: Object.values(typesByName),
    })
  }

  private generateGraphQLTypesByName(): Record<string, GraphQLObjectType> {
    const typesByName: Record<string, GraphQLObjectType> = {}
    for (const name in this.targetTypes) {
      const typeMetadata = this.targetTypes[name]
      typesByName[name] = this.generateType(typeMetadata)
    }
    return typesByName
  }

  private generateQuery(typesByName: Record<string, GraphQLObjectType>): GraphQLObjectType {
    const byIDQueries = this.generateByIDQueries(typesByName)
    const filterQueries = this.generateFilterQueries(typesByName)
    return new GraphQLObjectType({
      name: 'Query',
      fields: {
        ...byIDQueries,
        ...filterQueries,
      },
    })
  }

  private generateByIDQueries(typesByName: Record<string, GraphQLObjectType>): GraphQLFieldConfigMap<any, any> {
    const queries: GraphQLFieldConfigMap<any, any> = {}
    for (const name in this.targetTypes) {
      const graphQLType = typesByName[name]
      if (!graphQLType) {
        continue
      }
      const type = this.targetTypes[name]
      queries[name] = {
        type: graphQLType,
        args: {
          id: { type: GraphQLID },
        },
        resolve: readModelIDResolver(type.class),
      }
    }
    return queries
  }

  private generateFilterQueries(typesByName: Record<string, GraphQLObjectType>): GraphQLFieldConfigMap<any, any> {
    const queries: GraphQLFieldConfigMap<any, any> = {}
    for (const name in this.targetTypes) {
      const graphQLType = typesByName[name]
      if (!graphQLType) {
        continue
      }
      const type = this.targetTypes[name]
      queries[inflection.pluralize(name)] = {
        type: new GraphQLList(graphQLType),
        args: this.generateFilterArguments(type),
        resolve: readModelFilterResolver(type.class),
      }
    }
    return queries
  }

  private generateFilterArguments(typesMetadata: TargetTypeMetadata): GraphQLFieldConfigArgumentMap {
    const args: GraphQLFieldConfigArgumentMap = {}
    typesMetadata.properties.forEach((prop: PropertyMetadata) => {
      const graphQLPropType = graphQLTypeFor(prop.type)
      if (graphQLPropType == GraphQLJSONObject || graphQLPropType instanceof GraphQLList) {
        // TODO: We still don't handle filtering by complex properties
        return
      }
      args[prop.name] = {
        type: this.generateFilterFor(prop.type),
      }
    })
    return args
  }

  private generateFilterFor(type: AnyClass): GraphQLInputObjectType {
    const filterName = `${type.name}PropertyFilter`
    if (!this.generatedFiltersByTypeName[filterName]) {
      this.generatedFiltersByTypeName[filterName] = new GraphQLInputObjectType({
        name: filterName,
        fields: {
          operation: { type: new GraphQLNonNull(this.operationEnumFor(type)) },
          values: { type: new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(graphQLTypeFor(type)))) },
        },
      })
    }
    return this.generatedFiltersByTypeName[filterName]
  }

  private operationEnumFor(type: AnyClass): GraphQLEnumType {
    const operationEnumName = `${type.name}Operations`
    if (!this.generatedOperationEnumsByTypeName[operationEnumName]) {
      this.generatedOperationEnumsByTypeName[operationEnumName] = new GraphQLEnumType({
        name: operationEnumName,
        values: this.generateOperationEnumValuesFor(type),
      })
    }
    return this.generatedOperationEnumsByTypeName[operationEnumName]
  }

  private generateType(typeMetadata: TargetTypeMetadata): GraphQLObjectType {
    const fields: GraphQLFieldConfigMap<any, any> = {}
    for (const prop of typeMetadata.properties) {
      fields[prop.name] = { type: graphQLTypeFor(prop.type) }
    }
    return new GraphQLObjectType({
      name: typeMetadata.class.name,
      fields,
    })
  }

  private generateOperationEnumValuesFor(type: AnyClass): GraphQLEnumValueConfigMap {
    let operationsEnum: typeof StringOperations | typeof NumberOperations | typeof BooleanOperations
    switch (type) {
      case UUID:
      case String:
        operationsEnum = StringOperations
        break
      case Number:
        operationsEnum = NumberOperations
        break
      case Boolean:
        operationsEnum = BooleanOperations
        break
      default:
        throw new Error(`Type ${type.name} is not supported in search filters`)
    }

    const enumValuesConfig: GraphQLEnumValueConfigMap = {}
    for (const opSymbol in operationsEnum) {
      const opName = (operationsEnum as any)[opSymbol]
      enumValuesConfig[opName] = { value: opSymbol }
    }
    return enumValuesConfig
  }
}

function graphQLTypeFor(type: AnyClass): GraphQLScalarType | GraphQLList<any> {
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
    default:
      return GraphQLJSONObject
  }
}

// TODO: These functions should come from other place

function readModelFilterResolver(readModelClass: AnyClass): GraphQLFieldResolver<any, any, any> {
  return (parent, args, context, info) => {
    const searcher = Booster.readModel(readModelClass)
    for (const propName in args) {
      const filter = args[propName]
      searcher.filter(propName, filter.operation, ...filter.values)
    }
    return searcher.search()
  }
}

function readModelIDResolver(readModelClass: AnyClass): GraphQLFieldResolver<any, any, any> {
  return (parent, args, context, info) => {
    const searcher = Booster.readModel(readModelClass)
    searcher.filter('id', '=', args.id)
    return searcher.searchOne()
  }
}
