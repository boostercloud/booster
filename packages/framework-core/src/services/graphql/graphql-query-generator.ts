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
  GraphQLScalarType,
  GraphQLString,
  GraphQLBoolean,
  GraphQLFloat,
  Thunk,
} from 'graphql'
import {
  GraphQLNonInputType,
  GraphQLResolverContext,
  ResolverBuilder,
  TargetTypeMetadata,
  TargetTypesMap,
} from './common'
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
    const filterQueries = this.generateFilterQueries()
    const eventQueries = this.generateEventQueries()
    const fields = { ...byIDQueries, ...filterQueries, ...eventQueries }
    if (Object.keys(fields).length === 0) {
      return new GraphQLObjectType({
        name: 'Query',
        fields: {
          NoQueriesDefined: { type: GraphQLString },
        },
      })
    }
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
        args: this.generateFilterArguments(type),
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

  public generateFilterArguments(typeMetadata: TargetTypeMetadata): GraphQLFieldConfigArgumentMap {
    const args: GraphQLFieldConfigArgumentMap = {}
    typeMetadata.properties.forEach((prop: PropertyMetadata) => {
      const primitiveType = this.typeInformer.getPrimitiveExtendedType(prop.typeInfo.type)

      args[prop.name] = {
        type: primitiveType === Array ? this.generateArrayFilterFor(prop) : this.generateFilterFor(primitiveType),
      }
    })
    return args
  }

  private generateArrayFilterFor(property: PropertyMetadata): GraphQLInputObjectType {
    const filterName = `${property.name}PropertyFilter`

    if (!this.generatedFiltersByTypeName[filterName]) {
      const propFilters: GraphQLInputFieldConfigMap = {}
      property.typeInfo.parameters.forEach((param) => {
        const paramPrimitiveType = this.typeInformer.getPrimitiveExtendedType(param.type)
        const graphQLPropType = this.typeInformer.getGraphQLTypeFor(paramPrimitiveType)

        propFilters.includes = {
          ...propFilters.includes,
          ...{
            type: graphQLPropType as GraphQLScalarType,
          },
        }
      })

      this.generatedFiltersByTypeName[filterName] = new GraphQLInputObjectType({
        name: filterName,
        fields: propFilters,
      })
    }
    return this.generatedFiltersByTypeName[filterName]
  }

  private generateFilterFor(type: AnyClass): GraphQLInputObjectType {
    const filterName = `${type.name}PropertyFilter`
    if (!this.generatedFiltersByTypeName[filterName]) {
      const primitiveType = this.typeInformer.getPrimitiveExtendedType(type)
      const graphQLPropType = this.typeInformer.getGraphQLTypeFor(primitiveType)
      let fields: Thunk<GraphQLInputFieldConfigMap> = {}

      if (!this.typeInformer.canFilter(graphQLPropType)) {
        const properties = getPropertiesMetadata(type)
        this.typeInformer.generateGraphQLTypeFromMetadata({ class: type, properties })

        let nestedProperties: GraphQLInputFieldConfigMap = {}
        for (const prop of properties) {
          const propPrimitiveType = this.typeInformer.getPrimitiveExtendedType(prop.typeInfo.type)
          const property = { [prop.name]: { type: this.generateFilterFor(propPrimitiveType) } }
          nestedProperties = { ...nestedProperties, ...property }
        }

        fields = () => ({
          ...nestedProperties,
          and: { type: new GraphQLList(this.generatedFiltersByTypeName[filterName]) },
          or: { type: new GraphQLList(this.generatedFiltersByTypeName[filterName]) },
          not: { type: this.generatedFiltersByTypeName[filterName] },
        })
      } else {
        fields = this.generateFilterInputTypes(type)
      }
      this.generatedFiltersByTypeName[filterName] = new GraphQLInputObjectType({ name: filterName, fields })
    }
    return this.generatedFiltersByTypeName[filterName]
  }

  private generateFilterInputTypes(type: AnyClass): GraphQLInputFieldConfigMap {
    const primitiveType = this.typeInformer.getPrimitiveExtendedType(type)
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
          le: { type: GraphQLFloat },
          lt: { type: GraphQLFloat },
          ge: { type: GraphQLFloat },
          gt: { type: GraphQLFloat },
          in: { type: GraphQLList(GraphQLFloat) },
        }
      case Date:
      case String:
        return {
          eq: { type: GraphQLString },
          ne: { type: GraphQLString },
          le: { type: GraphQLString },
          lt: { type: GraphQLString },
          ge: { type: GraphQLString },
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
