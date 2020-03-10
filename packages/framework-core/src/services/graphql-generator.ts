import { BoosterConfig } from '@boostercloud/framework-types'
import {
  GraphQLBoolean,
  GraphQLFieldConfigArgumentMap,
  GraphQLFloat,
  GraphQLID,
  GraphQLObjectType,
  GraphQLScalarType,
  GraphQLSchema,
  GraphQLString,
} from 'graphql'
import { GraphQLJSONObject } from 'graphql-type-json'
import { GraphQLFieldConfigMap, GraphQLList } from 'graphql/type/definition'
import { AnyClass, EntityMetadata, UUID } from '@boostercloud/framework-types'
import { Booster } from '../booster'
import { PropertyMetadata } from '@boostercloud/framework-types/dist'

export class GraphqlGenerator {
  public constructor(private config: BoosterConfig) {}

  public generateSchema(): GraphQLSchema {
    const typesByName = this.generateTypesByName()
    const query = this.generateQuery(typesByName)
    return new GraphQLSchema({
      query,
      types: [...typesByName.values()],
    })
  }

  private generateTypesByName(): Map<string, GraphQLObjectType> {
    const typesByName = new Map<string, GraphQLObjectType>()
    for (const name in this.config.entities) {
      const entity = this.config.entities[name]
      typesByName.set(name, this.generateType(entity))
    }
    return typesByName
  }

  private generateQuery(typesByName: Map<string, GraphQLObjectType>): GraphQLObjectType {
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

  private generateEntityByIDQueries(typesByName: Map<string, GraphQLObjectType>): GraphQLFieldConfigMap<any, any> {
    const queries: GraphQLFieldConfigMap<any, any> = {}
    for (const name in this.config.entities) {
      const entityGraphQLType = typesByName.get(name)
      if (!entityGraphQLType) {
        continue
      }
      queries[name] = {
        type: entityGraphQLType,
        args: {
          id: { type: GraphQLID },
        },
        resolve: (parent, args, context, info) => {
          return Booster.fetchEntitySnapshot(info.fieldName, args.id) // TODO: WIP, This is for testing only
        },
      }
    }
    return queries
  }

  private generateEntityFilterQueries(typesByName: Map<string, GraphQLObjectType>): GraphQLFieldConfigMap<any, any> {
    const queries: GraphQLFieldConfigMap<any, any> = {}
    for (const name in this.config.entities) {
      const entityGraphQLType = typesByName.get(name)
      if (!entityGraphQLType) {
        continue
      }
      const entity = this.config.entities[name]
      queries[`${name}s`] = {
        type: new GraphQLList(entityGraphQLType),
        args: this.generateEntityFilterArguments(entity),
        resolve: (parent, args, context, info) => {
          return [] // TODO: WIP
        },
      }
    }
    return queries
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

  private generateEntityFilterArguments(entityMetadata: EntityMetadata): GraphQLFieldConfigArgumentMap {
    const args: GraphQLFieldConfigArgumentMap = {}
    entityMetadata.properties.forEach((prop: PropertyMetadata) => {
      args[prop.name] = {
        type: graphQLTypeFor(prop.type),
      }
    })
    return args
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
