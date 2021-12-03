import { AnyClass, BoosterConfig, UUID } from '@boostercloud/framework-types'
import {
  GraphQLBoolean,
  GraphQLEnumType,
  GraphQLEnumValueConfigMap,
  GraphQLFieldConfig,
  GraphQLFieldConfigArgumentMap,
  GraphQLFieldConfigMap,
  GraphQLFieldResolver,
  GraphQLFloat,
  GraphQLID,
  GraphQLInputFieldConfigMap,
  GraphQLInputObjectType,
  GraphQLInt,
  GraphQLList,
  GraphQLNonNull,
  GraphQLObjectType,
  GraphQLOutputType,
  GraphQLScalarType,
  GraphQLString,
  Thunk,
} from 'graphql'
import { GraphQLJSONObject } from 'graphql-type-json'
import * as inflected from 'inflected'
import { PropertyMetadata, TypeGroup, TypeMetadata } from '../../metadata-types'
import { getClassMetadata } from './../../decorators/metadata'
import { DateScalar, GraphQLResolverContext, isExternalType, ResolverBuilder } from './common'
import { GraphQLTypeInformer } from './graphql-type-informer'

export class GraphQLQueryGenerator {
  private generatedFiltersByTypeName: Record<string, GraphQLInputObjectType> = {}

  public constructor(
    private readonly config: BoosterConfig,
    private readonly readModels: AnyClass[],
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly byIDResolverBuilder: ResolverBuilder,
    private readonly filterResolverBuilder: ResolverBuilder,
    private readonly eventsResolver: GraphQLFieldResolver<unknown, GraphQLResolverContext, any>
  ) {}

  public generate(): GraphQLObjectType {
    const byIDQueries = this.generateByKeysQueries()
    const filterQueries = this.generateFilterQueries()
    const listedQueries = this.generateListedQueries()
    const eventQueries = this.generateEventQueries()
    return new GraphQLObjectType({
      name: 'Query',
      fields: { ...byIDQueries, ...filterQueries, ...listedQueries, ...eventQueries },
    })
  }

  private generateByKeysQueries(): GraphQLFieldConfigMap<unknown, GraphQLResolverContext> {
    const queries: GraphQLFieldConfigMap<unknown, GraphQLResolverContext> = {}
    for (const readModel of this.readModels) {
      const readModelName = readModel.name
      const sequenceKeyName = this.config.readModelSequenceKeys[readModelName]
      if (sequenceKeyName) {
        queries[readModelName] = this.generateByIdAndSequenceKeyQuery(readModel, sequenceKeyName)
      } else {
        queries[readModelName] = this.generateByIdQuery(readModel)
      }
    }
    return queries
  }

  private generateByIdQuery(readModel: AnyClass): GraphQLFieldConfig<unknown, GraphQLResolverContext> {
    const graphQLType = this.typeInformer.generateGraphQLTypeForClass(readModel)
    return {
      type: graphQLType,
      args: {
        id: { type: new GraphQLNonNull(GraphQLID) },
      },
      resolve: this.byIDResolverBuilder(readModel),
    }
  }

  private generateByIdAndSequenceKeyQuery(
    readModel: AnyClass,
    sequenceKeyName: string
  ): GraphQLFieldConfig<unknown, GraphQLResolverContext> {
    const graphQLType = this.typeInformer.generateGraphQLTypeForClass(readModel)
    return {
      type: new GraphQLList(graphQLType),
      args: {
        id: { type: new GraphQLNonNull(GraphQLID) },
        [sequenceKeyName]: { type: GraphQLID },
      },
      resolve: this.byIDResolverBuilder(readModel),
    }
  }

  private generateFilterQueries(): GraphQLFieldConfigMap<unknown, GraphQLResolverContext> {
    const queries: GraphQLFieldConfigMap<unknown, GraphQLResolverContext> = {}
    for (const readModel of this.readModels) {
      const graphQLType = this.typeInformer.generateGraphQLTypeForClass(readModel)
      queries[inflected.pluralize(readModel.name)] = {
        type: new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(graphQLType))),
        args: this.generateFilterQueriesFields(readModel.name, readModel),
        resolve: this.filterResolverBuilder(readModel),
      }
    }
    return queries
  }

  private generateListedQueries(): GraphQLFieldConfigMap<unknown, GraphQLResolverContext> {
    const queries: GraphQLFieldConfigMap<unknown, GraphQLResolverContext> = {}
    for (const readModel of this.readModels) {
      const graphQLType = this.typeInformer.generateGraphQLTypeForClass(readModel)
      queries[`List${inflected.pluralize(readModel.name)}`] = {
        type: new GraphQLNonNull(
          new GraphQLObjectType({
            name: `${readModel.name}Connection`,
            fields: {
              items: { type: new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(graphQLType))) },
              count: { type: GraphQLInt },
              cursor: { type: GraphQLJSONObject },
            },
          })
        ),
        args: this.generateListedQueriesFields(readModel),
        resolve: this.filterResolverBuilder(readModel),
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

  public generateFilterQueriesFields(name: string, type: AnyClass): GraphQLFieldConfigArgumentMap {
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

  public generateListedQueriesFields(type: AnyClass): GraphQLFieldConfigArgumentMap {
    const filterArguments = this.generateFilterArguments(type)
    const filter: GraphQLInputObjectType = new GraphQLInputObjectType({
      name: `List${type.name}Filter`,
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

  public generateFilterArguments(type: AnyClass): GraphQLFieldConfigArgumentMap {
    const metadata = getClassMetadata(type)
    const args: GraphQLFieldConfigArgumentMap = {}
    metadata.fields.forEach((prop: PropertyMetadata) => {
      args[prop.name] = {
        type: this.generateFilterFor(prop),
      }
    })
    return args
  }

  private generateFilterFor(prop: PropertyMetadata): GraphQLInputObjectType | GraphQLScalarType {
    if (!prop.typeInfo.type || prop.typeInfo.type.name === 'Object') return GraphQLJSONObject

    let filterName = `${prop.typeInfo.name}PropertyFilter`
    filterName = filterName.charAt(0).toUpperCase() + filterName.substr(1)

    if (this.generatedFiltersByTypeName[filterName]) return this.generatedFiltersByTypeName[filterName]
    if (prop.typeInfo.typeGroup === TypeGroup.Array) return this.generateArrayFilterFor(prop)
    let fields: Thunk<GraphQLInputFieldConfigMap> = {}

    if (prop.typeInfo.typeGroup === TypeGroup.Class && prop.typeInfo.name !== 'UUID') {
      if (isExternalType(prop.typeInfo)) return GraphQLJSONObject
      let nestedProperties: GraphQLInputFieldConfigMap = {}
      const metadata = getClassMetadata(prop.typeInfo.type)
      if (metadata.fields.length === 0) return GraphQLJSONObject

      this.typeInformer.generateGraphQLTypeForClass(prop.typeInfo.type, true)

      for (const prop of metadata.fields) {
        const property = { [prop.name]: { type: this.generateFilterFor(prop) } }
        nestedProperties = { ...nestedProperties, ...property }
      }
      fields = () => ({
        ...nestedProperties,
        and: { type: new GraphQLList(this.generatedFiltersByTypeName[filterName]) },
        or: { type: new GraphQLList(this.generatedFiltersByTypeName[filterName]) },
        not: { type: this.generatedFiltersByTypeName[filterName] },
      })
    } else {
      fields = this.generateFilterInputTypes(prop.typeInfo)
    }
    this.generatedFiltersByTypeName[filterName] = new GraphQLInputObjectType({ name: filterName, fields })
    return this.generatedFiltersByTypeName[filterName]
  }

  private generateArrayFilterFor(property: PropertyMetadata): GraphQLInputObjectType {
    let filterName = `${property.typeInfo.parameters[0].name}ArrayPropertyFilter`
    filterName = filterName.charAt(0).toUpperCase() + filterName.substr(1)

    if (!this.generatedFiltersByTypeName[filterName]) {
      const propFilters: GraphQLInputFieldConfigMap = {}
      property.typeInfo.parameters.forEach((param) => {
        let graphqlType: GraphQLScalarType
        switch (param.typeGroup) {
          case TypeGroup.Boolean:
            graphqlType = GraphQLBoolean
            break
          case TypeGroup.String:
            graphqlType = GraphQLString
            break
          case TypeGroup.Number:
            graphqlType = GraphQLFloat
            break
          default:
            graphqlType = param.type === UUID ? GraphQLID : GraphQLJSONObject
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

  private generateFilterInputTypes(typeMetadata: TypeMetadata): GraphQLInputFieldConfigMap {
    const { type, typeGroup } = typeMetadata
    if (typeGroup === TypeGroup.Boolean)
      return {
        eq: { type: GraphQLBoolean },
        ne: { type: GraphQLBoolean },
      }
    if (typeGroup === TypeGroup.Number)
      return {
        eq: { type: GraphQLFloat },
        ne: { type: GraphQLFloat },
        lte: { type: GraphQLFloat },
        lt: { type: GraphQLFloat },
        gte: { type: GraphQLFloat },
        gt: { type: GraphQLFloat },
        in: { type: GraphQLList(GraphQLFloat) },
      }
    if (typeGroup === TypeGroup.String)
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
    if (type === UUID)
      return {
        eq: { type: GraphQLID },
        ne: { type: GraphQLID },
        in: { type: GraphQLList(GraphQLID) },
      }
    if (type === Date)
      return {
        eq: { type: DateScalar },
        ne: { type: DateScalar },
        lte: { type: DateScalar },
        lt: { type: DateScalar },
        gte: { type: DateScalar },
        gt: { type: DateScalar },
        in: { type: GraphQLList(DateScalar) },
      }
    if (typeGroup === TypeGroup.Enum) {
      const EnumType = this.typeInformer.getOrCreateGraphQLType(typeMetadata, true)
      return {
        eq: { type: EnumType },
        ne: { type: EnumType },
      }
    }

    throw new Error(`Type ${type?.name} is not supported in search filters`)
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
