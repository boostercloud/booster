import {
  GraphQLFieldConfigArgumentMap,
  GraphQLFieldConfigMap,
  GraphQLFieldResolver,
  GraphQLID,
  GraphQLInputObjectType,
  GraphQLInputFieldConfigMap,
  GraphQLList,
  GraphQLNonNull,
  GraphQLObjectType,
  GraphQLOutputType,
  GraphQLString,
  GraphQLBoolean,
  GraphQLFloat,
  Thunk,
  GraphQLEnumType,
  GraphQLEnumValueConfigMap,
  GraphQLScalarType,
  GraphQLInt,
} from 'graphql'
import { GraphQLResolverContext, ResolverBuilder, TargetTypeMetadata, TargetTypesMap } from './common'
import { GraphQLTypeInformer } from './graphql-type-informer'
import * as inflected from 'inflected'
import { GraphQLJSONObject } from 'graphql-type-json'
import { AnyClass, BoosterConfig } from '@boostercloud/framework-types'
import { PropertyMetadata } from 'metadata-booster'
import { getPropertiesMetadata } from './../../decorators/metadata'

export class GraphQLQueryGenerator {
  private generatedFiltersByTypeName: Record<string, GraphQLInputObjectType> = {}

  public constructor(
    private readonly config: BoosterConfig,
    private readonly targetTypes: TargetTypesMap,
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly byIDResolverBuilder: ResolverBuilder,
    private readonly filterResolverBuilder: ResolverBuilder,
    private readonly eventsResolver: GraphQLFieldResolver<unknown, GraphQLResolverContext, any>
  ) {}

  public generate(): GraphQLObjectType {
    const byIDQueries = this.generateByIDQueries()
    const filterQueries = { ...this.generateFilterQueries(), ...this.generateListedQueries() }
    const eventQueries = this.generateEventQueries()
    const fields = { ...byIDQueries, ...filterQueries, ...eventQueries }
    return new GraphQLObjectType({
      name: 'Query',
      fields: fields,
    })
  }

  private generateByIDQueries(): GraphQLFieldConfigMap<unknown, GraphQLResolverContext> {
    const queries: GraphQLFieldConfigMap<unknown, GraphQLResolverContext> = {}
    for (const name in this.targetTypes) {
      const type = this.targetTypes[name]
      const graphQLType = this.typeInformer.getGraphQLTypeFor(type.class)
      queries[name] = {
        type: graphQLType,
        args: {
          id: { type: new GraphQLNonNull(GraphQLID) },
        },
        resolve: this.byIDResolverBuilder(type.class),
      }
    }
    return queries
  }

  private generateFilterQueries(): GraphQLFieldConfigMap<unknown, GraphQLResolverContext> {
    const queries: GraphQLFieldConfigMap<unknown, GraphQLResolverContext> = {}
    for (const name in this.targetTypes) {
      const type = this.targetTypes[name]
      const graphQLType = this.typeInformer.getGraphQLTypeFor(type.class)
      queries[inflected.pluralize(name)] = {
        type: new GraphQLList(graphQLType),
        args: this.generateFilterQueriesFields(name, type),
        resolve: this.filterResolverBuilder(type.class),
      }
    }
    return queries
  }

  private generateListedQueries(): GraphQLFieldConfigMap<unknown, GraphQLResolverContext> {
    const queries: GraphQLFieldConfigMap<unknown, GraphQLResolverContext> = {}
    for (const name in this.targetTypes) {
      const type = this.targetTypes[name]
      const graphQLType = this.typeInformer.getGraphQLTypeFor(type.class)
      queries[`List${inflected.pluralize(name)}`] = {
        type: new GraphQLObjectType({
          name: `${name}Connection`,
          fields: {
            items: { type: new GraphQLList(graphQLType) },
            cursor: { type: GraphQLJSONObject },
          },
        }),
        args: this.generateListedQueriesFields(name, type),
        resolve: this.filterResolverBuilder(type.class),
      }
    }
    return queries
  }

  private generateEventQueries(): GraphQLFieldConfigMap<unknown, GraphQLResolverContext> {
    const eventQueryResponse = this.buildEventQueryResponse()
    return {
      eventsByEntity: {
        type: eventQueryResponse,
        args: {
          entity: {
            type: new GraphQLNonNull(this.buildGraphqlSimpleEnumFor('EntityType', Object.keys(this.config.entities))),
          },
          entityID: { type: GraphQLID },
          from: { type: GraphQLString },
          to: { type: GraphQLString },
        },
        resolve: this.eventsResolver,
      },
      eventsByType: {
        type: eventQueryResponse,
        args: {
          type: {
            type: new GraphQLNonNull(this.buildGraphqlSimpleEnumFor('EventType', Object.keys(this.config.reducers))),
          },
          from: { type: GraphQLString },
          to: { type: GraphQLString },
        },
        resolve: this.eventsResolver,
      },
    }
  }

  private buildEventQueryResponse(): GraphQLOutputType {
    return new GraphQLList(
      new GraphQLObjectType({
        name: 'EventQueryResponse',
        fields: {
          type: { type: new GraphQLNonNull(GraphQLString) },
          entity: { type: new GraphQLNonNull(GraphQLString) },
          entityID: { type: new GraphQLNonNull(GraphQLID) },
          requestID: { type: new GraphQLNonNull(GraphQLID) },
          user: {
            type: new GraphQLObjectType({
              name: 'User',
              fields: {
                id: { type: GraphQLString },
                username: { type: new GraphQLNonNull(GraphQLString) },
                role: { type: new GraphQLNonNull(GraphQLString) },
              },
            }),
          },
          createdAt: { type: new GraphQLNonNull(GraphQLString) },
          value: { type: new GraphQLNonNull(GraphQLJSONObject) },
        },
      })
    )
  }
  public generateFilterQueriesFields(name: string, type: TargetTypeMetadata): GraphQLFieldConfigArgumentMap {
    const filterArguments = this.generateFilterArguments(type)
    const filter: GraphQLInputObjectType = new GraphQLInputObjectType({
      name: `${name}Filter`,
      fields: () => ({
        ...filterArguments,
        and: { type: new GraphQLList(filter) },
        or: { type: new GraphQLList(filter) },
        not: { type: filter },
      }),
    })
    return { filter: { type: filter } }
  }
  public generateListedQueriesFields(name: string, type: TargetTypeMetadata): GraphQLFieldConfigArgumentMap {
    const filterArguments = this.generateFilterArguments(type)
    const filter: GraphQLInputObjectType = new GraphQLInputObjectType({
      name: `List${name}Filter`,
      fields: () => ({
        ...filterArguments,
        and: { type: new GraphQLList(filter) },
        or: { type: new GraphQLList(filter) },
        not: { type: filter },
      }),
    })
    return {
      filter: { type: filter },
      limit: { type: GraphQLInt },
      afterCursor: { type: GraphQLJSONObject },
    }
  }

  public generateFilterArguments(typeMetadata: TargetTypeMetadata): GraphQLFieldConfigArgumentMap {
    const args: GraphQLFieldConfigArgumentMap = {}
    typeMetadata.properties.forEach((prop: PropertyMetadata) => {
      args[prop.name] = {
        type: this.generateFilterFor(prop),
      }
    })
    return args
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

      this.generatedFiltersByTypeName[filterName] = new GraphQLInputObjectType({
        name: filterName,
        fields: propFilters,
      })
    }
    return this.generatedFiltersByTypeName[filterName]
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
        })
      } else {
        fields = this.generateFilterInputTypes(prop.typeInfo.type)
      }
      this.generatedFiltersByTypeName[filterName] = new GraphQLInputObjectType({ name: filterName, fields })
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
        }
      default:
        throw new Error(`Type ${type.name} is not supported in search filters`)
    }
  }

  private buildGraphqlSimpleEnumFor(enumName: string, values: Array<string>): GraphQLEnumType {
    return new GraphQLEnumType({
      name: enumName,
      values: values.reduce((valuesRecord, value) => {
        valuesRecord[value] = { value }
        return valuesRecord
      }, {} as GraphQLEnumValueConfigMap),
    })
  }
}
