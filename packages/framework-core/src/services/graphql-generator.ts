import {
  AnyClass,
  BoosterConfig,
  EntityMetadata,
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
} from 'graphql/type/definition'
import { Booster } from '../booster'
import * as inflection from 'inflection'

export class GraphQLGenerator {
  private generatedFiltersByTypeName: Record<string, GraphQLInputObjectType> = {}
  private generatedOperationEnumsByTypeName: Record<string, GraphQLEnumType> = {}
  public constructor(private config: BoosterConfig) {}

  public generateSchema(): GraphQLSchema {
    const typesByName = this.generateTypesByName()
    const query = this.generateQuery(typesByName)
    return new GraphQLSchema({
      query,
      types: Object.values(typesByName),
    })
  }

  private generateTypesByName(): Record<string, GraphQLObjectType> {
    const typesByName: Record<string, GraphQLObjectType> = {}
    for (const name in this.config.entities) {
      const entity = this.config.entities[name]
      typesByName[name] = this.generateType(entity)
    }
    return typesByName
  }

  private generateQuery(typesByName: Record<string, GraphQLObjectType>): GraphQLObjectType {
    const entityByIDQueries = this.generateEntityByIDQueries(typesByName)
    const entityFilterQueries = this.generateEntityFilterQueries(typesByName)
    return new GraphQLObjectType({
      name: 'Query',
      fields: {
        ...entityByIDQueries,
        ...entityFilterQueries,
      },
    })
  }

  private generateEntityByIDQueries(typesByName: Record<string, GraphQLObjectType>): GraphQLFieldConfigMap<any, any> {
    const queries: GraphQLFieldConfigMap<any, any> = {}
    for (const name in this.config.entities) {
      const entityGraphQLType = typesByName[name]
      if (!entityGraphQLType) {
        continue
      }
      queries[name] = {
        type: entityGraphQLType,
        args: {
          id: { type: GraphQLID },
        },
        resolve: (parent, args, context, info) => {
          return Booster.fetchEntitySnapshot(info.fieldName, args.id)
        },
      }
    }
    return queries
  }

  private generateEntityFilterQueries(typesByName: Record<string, GraphQLObjectType>): GraphQLFieldConfigMap<any, any> {
    const queries: GraphQLFieldConfigMap<any, any> = {}
    for (const name in this.config.entities) {
      const entityGraphQLType = typesByName[name]
      if (!entityGraphQLType) {
        continue
      }
      const entity = this.config.entities[name]
      queries[inflection.pluralize(name)] = {
        type: new GraphQLList(entityGraphQLType),
        args: this.generateEntityFilterArguments(entity),
        resolve: entityFilterResolver(entity.class),
      }
    }
    return queries
  }

  private generateEntityFilterArguments(entityMetadata: EntityMetadata): GraphQLFieldConfigArgumentMap {
    const args: GraphQLFieldConfigArgumentMap = {}
    entityMetadata.properties.forEach((prop: PropertyMetadata) => {
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
          operation: { type: this.operationEnumFor(type) },
          values: { type: new GraphQLList(graphQLTypeFor(type)) },
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

  private generateType(entity: EntityMetadata): GraphQLObjectType {
    const fields: GraphQLFieldConfigMap<any, any> = {}
    for (const prop of entity.properties) {
      fields[prop.name] = { type: graphQLTypeFor(prop.type) }
    }
    return new GraphQLObjectType({
      name: entity.class.name,
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
    for (const op in operationsEnum) {
      enumValuesConfig[op] = {}
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
function entityFilterResolver(entity: AnyClass): GraphQLFieldResolver<any, any, any> {
  return (parent, args, context, info) => {
    const searcher = Booster.entity(entity)
    for (const propName in args) {
      const filter = args[propName]
      searcher.filter(propName, filter.operation, filter.value)
    }
    return searcher.search()
  }
}
