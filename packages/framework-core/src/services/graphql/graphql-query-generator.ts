import {
  GraphQLEnumType,
  GraphQLEnumValueConfigMap,
  GraphQLFieldConfigArgumentMap,
  GraphQLFieldConfigMap,
  GraphQLFieldResolver,
  GraphQLID,
  GraphQLInputObjectType,
  GraphQLList,
  GraphQLNonNull,
  GraphQLObjectType,
  GraphQLOutputType,
  GraphQLScalarType,
  GraphQLString,
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
import {
  AnyClass,
  BooleanOperations,
  NumberOperations,
  PropertyMetadata,
  StringOperations,
  UUID,
} from '@boostercloud/framework-types'

export class GraphQLQueryGenerator {
  private generatedFiltersByTypeName: Record<string, GraphQLInputObjectType> = {}
  private generatedOperationEnumsByTypeName: Record<string, GraphQLEnumType> = {}

  public constructor(
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
          entity: { type: new GraphQLNonNull(GraphQLString) },
          entityID: { type: GraphQLID },
          from: { type: GraphQLString },
          to: { type: GraphQLString },
        },
        resolve: this.eventsResolver,
      },
      eventsByType: {
        type: eventQueryResponse,
        args: {
          type: { type: new GraphQLNonNull(GraphQLString) },
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
      const graphQLPropType = this.typeInformer.getGraphQLTypeFor(prop.type)
      if (!this.canFilter(graphQLPropType)) {
        // TODO: We still don't handle filtering by complex properties
        return
      }
      args[prop.name] = {
        type: this.generateFilterFor(prop.type),
      }
    })
    return args
  }

  private canFilter(graphQLType: GraphQLNonInputType): boolean {
    return graphQLType instanceof GraphQLScalarType && graphQLType != GraphQLJSONObject
  }

  private generateFilterFor(type: AnyClass): GraphQLInputObjectType {
    const filterName = `${type.name}PropertyFilter`
    if (!this.generatedFiltersByTypeName[filterName]) {
      const graphQLValueType = this.typeInformer.getGraphQLTypeFor(type)
      this.generatedFiltersByTypeName[filterName] = new GraphQLInputObjectType({
        name: filterName,
        fields: {
          operation: { type: new GraphQLNonNull(this.operationEnumFor(type)) },
          values: { type: new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(graphQLValueType))) },
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
