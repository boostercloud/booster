import {
  AnyClass,
  BoosterConfig,
  Class,
  CommandEnvelope,
  EventSearchParameters,
  EventSearchRequest,
  EventSearchResponse,
  ProjectionFor,
  QueryEnvelope,
  ReadModelByIdRequestArgs,
  ReadModelInterface,
  ReadModelRequestArgs,
  ReadModelRequestEnvelope,
  ReadModelRequestProperties,
  TimeKey,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import {
  FieldNode,
  getNamedType,
  GraphQLFieldResolver,
  GraphQLInputObjectType,
  GraphQLList,
  GraphQLNonNull,
  GraphQLObjectType,
  GraphQLResolveInfo,
  GraphQLSchema,
  isObjectType,
} from 'graphql'
import { pluralize } from 'inflected'
import { BoosterCommandDispatcher } from '../../booster-command-dispatcher'
import { BoosterEventsReader } from '../../booster-events-reader'
import { BoosterReadModelsReader } from '../../booster-read-models-reader'
import { GraphQLResolverContext } from './common'
import { GraphQLMutationGenerator } from './graphql-mutation-generator'
import { GraphQLQueryGenerator } from './graphql-query-generator'
import { GraphQLSubscriptionGenerator } from './graphql-subcriptions-generator'
import { GraphQLTypeInformer } from './graphql-type-informer'
import { BoosterQueryDispatcher } from '../../booster-query-dispatcher'
import { SelectionSetNode } from 'graphql/language/ast'
import { GraphQLInputType, GraphQLNamedInputType } from 'graphql/type/definition'

export class GraphQLGenerator {
  private static commandsDispatcher: BoosterCommandDispatcher
  private static queriesDispatcher: BoosterQueryDispatcher
  private static readModelsReader: BoosterReadModelsReader
  private static eventsReader: BoosterEventsReader
  private static schema: GraphQLSchema

  public static generateSchema(config: BoosterConfig): GraphQLSchema {
    const logger = getLogger(config, 'GraphQLGenerator#generateSchema')
    if (!this.schema) {
      logger.debug('Generating GraphQL schema...')
      this.commandsDispatcher = new BoosterCommandDispatcher(config)
      this.queriesDispatcher = new BoosterQueryDispatcher(config)
      this.readModelsReader = new BoosterReadModelsReader(config)
      this.eventsReader = new BoosterEventsReader(config)

      const typeInformer = new GraphQLTypeInformer(logger)

      const generatedFiltersByTypeName: Record<string, GraphQLInputObjectType> = {}

      const queryGenerator = new GraphQLQueryGenerator(
        config,
        Object.values(config.readModels).map((m) => m.class),
        config.queryHandlers,
        typeInformer,
        this.readModelByIDResolverBuilder.bind(this, config),
        this.queriesResolverBuilder.bind(this),
        this.readModelResolverBuilder.bind(this),
        this.eventResolver.bind(this),
        generatedFiltersByTypeName
      )

      const mutationGenerator = new GraphQLMutationGenerator(
        config.commandHandlers,
        typeInformer,
        this.commandResolverBuilder.bind(this),
        config
      )

      const subscriptionGenerator = new GraphQLSubscriptionGenerator(
        Object.values(config.readModels).map((m) => m.class),
        typeInformer,
        this.subscriptionByIDResolverBuilder.bind(this, config),
        this.subscriptionResolverBuilder.bind(this, config),
        generatedFiltersByTypeName,
        config
      )

      this.schema = new GraphQLSchema({
        query: queryGenerator.generate(),
        mutation: mutationGenerator.generate(),
        subscription: subscriptionGenerator.generate(),
      })
      logger.debug('GraphQL schema generated')
    }
    return this.schema
  }

  public static readModelResolverBuilder(
    readModelClass: AnyClass
  ): GraphQLFieldResolver<unknown, GraphQLResolverContext, ReadModelRequestArgs<ReadModelInterface>> {
    return (parent, args, context, info) => {
      let isPaginated = false
      const fields: ProjectionFor<unknown> = this.getFields(info) as ProjectionFor<unknown>
      let select: ProjectionFor<unknown> | undefined = fields.length > 0 ? fields : undefined
      if (info?.fieldName === `List${pluralize(readModelClass.name)}`) {
        isPaginated = true
        if (select) {
          select = select
            .map((field: string) => {
              return field.split('.').slice(1).join('.')
            })
            .filter((str: string) => str.trim().length > 0) as ProjectionFor<unknown>
        }
      }
      const readModelEnvelope = toReadModelRequestEnvelope(readModelClass, args, context, isPaginated, select)
      return this.readModelsReader.search(readModelEnvelope)
    }
  }

  public static readModelByIDResolverBuilder(
    config: BoosterConfig,
    readModelClass: AnyClass
  ): GraphQLFieldResolver<unknown, GraphQLResolverContext, ReadModelByIdRequestArgs> {
    const sequenceKeyName = config.readModelSequenceKeys[readModelClass.name]
    return async (parent, args, context) => {
      const readModelRequestEnvelope = this.toReadModelByIdRequestEnvelope(
        readModelClass,
        args,
        context,
        sequenceKeyName
      )
      return await this.readModelsReader.findById(readModelRequestEnvelope)
    }
  }

  public static eventResolver(
    parent: unknown,
    args: EventSearchParameters,
    context: GraphQLResolverContext
  ): Promise<Array<EventSearchResponse>> {
    const eventsRequestEnvelope = toEventSearchRequest(args, context)
    return this.eventsReader.fetch(eventsRequestEnvelope)
  }

  public static commandResolverBuilder(
    commandClass: AnyClass
  ): GraphQLFieldResolver<unknown, GraphQLResolverContext, { input: unknown }> {
    return async (parent, args, context) => {
      const commandEnvelope = toEnvelope(commandClass.name, args.input, context) as CommandEnvelope
      const result = await this.commandsDispatcher.dispatchCommand(commandEnvelope, context)
      // It could be that the command didn't return anything
      // so in that case we return `true`, as GraphQL doesn't have a `null` type
      return result ?? true
    }
  }

  public static queriesResolverBuilder(
    queryClass: AnyClass
  ): GraphQLFieldResolver<unknown, GraphQLResolverContext, { input: unknown }> {
    return async (parent, args, context) => {
      const queryEnvelope = toEnvelope(queryClass.name, args.input, context) as QueryEnvelope
      return await this.queriesDispatcher.dispatchQuery(queryEnvelope, context)
    }
  }

  public static subscriptionByIDResolverBuilder(
    config: BoosterConfig,
    readModelClass: AnyClass
  ): GraphQLFieldResolver<unknown, GraphQLResolverContext, ReadModelRequestProperties<ReadModelInterface>> {
    return async (parent, args, context, info) => {
      const filterArgs = { filter: { id: { eq: args.id } } }
      return this.subscriptionResolverBuilder(config, readModelClass)(parent, filterArgs, context, info)
    }
  }

  public static subscriptionResolverBuilder(
    config: BoosterConfig,
    readModelClass: AnyClass
  ): GraphQLFieldResolver<unknown, GraphQLResolverContext, ReadModelRequestArgs<ReadModelInterface>> {
    return async (parent, args, context) => {
      if (!context.connectionID) {
        throw new Error('Missing "connectionID". It is required for subscriptions')
      }

      const readModelRequestEnvelope = toReadModelRequestEnvelope(readModelClass, args, context)
      if (context.storeSubscriptions) {
        await this.readModelsReader.subscribe(context.connectionID, readModelRequestEnvelope, context.operation)
      }

      return context.pubSub.asyncIterator(readModelRequestEnvelope, config)
    }
  }

  private static toReadModelByIdRequestEnvelope(
    readModelClass: Class<ReadModelInterface>,
    args: ReadModelByIdRequestArgs,
    context: GraphQLResolverContext,
    sequenceKeyName?: string
  ): ReadModelRequestEnvelope<ReadModelInterface> {
    const key = sequenceKeyName
      ? {
          id: args.id,
          sequenceKey: {
            name: sequenceKeyName,
            value: args[sequenceKeyName] as TimeKey,
          },
        }
      : { id: args.id }
    return {
      currentUser: context.user,
      requestID: context.requestID,
      class: readModelClass,
      className: readModelClass.name,
      key,
      version: 1, // TODO: How to pass the version through GraphQL?
      filters: {},
      sortBy: {},
    }
  }

  /**
   * Extracts the fields from a GraphQLResolveInfo object. This object is part of a GraphQL request and the goal is to
   * identify the fields in the request. For example, for the following GraphQL query request:
   *
   * ```graphql
   * query {
   *   CartReadModel {
   *     id
   *     cartItems {
   *       id
   *       quantity
   *     }
   *   }
   * }
   * ```
   *
   * it will return the following list of fields: `['id', 'cartItems[].id', 'cartItems[].quantity']`
   *
   * @param {GraphQLResolveInfo} info - The GraphQLResolveInfo object.
   * @returns {string[]} - The extracted fields.
   * @private
   */
  private static getFields(info: GraphQLResolveInfo): string[] {
    let fields: string[] = []

    /**
     * Checks if a type is a list.
     * @param {any} type - The type to check.
     * @returns {boolean} - True if the type is a list, false otherwise.
     */
    const isList = (type: any): boolean => {
      if (type instanceof GraphQLNonNull) {
        return type.ofType instanceof GraphQLList
      }
      return type instanceof GraphQLList
    }

    /**
     * Extracts fields from a selection set.
     * @param {SelectionSetNode} selectionSet - The selection set.
     * @param {string[]} path - The current path.
     * @param {GraphQLObjectType | any} parentType - The parent type.
     * @returns {string[]} - The extracted fields.
     */
    const extractFields = (
      selectionSet: SelectionSetNode,
      path: string[] = [],
      parentType: GraphQLObjectType | any
    ): string[] => {
      let subFields: string[] = []
      if (selectionSet && selectionSet.selections) {
        selectionSet.selections.forEach((selection: any) => {
          const fieldName = selection.name.value
          const field = parentType.getFields()[fieldName]

          if (!field) {
            return
          }

          const fieldType: GraphQLNamedInputType = getNamedType(field.type as GraphQLNamedInputType)
          const currentPath = [...path, fieldName]

          if (isList(field.type)) {
            const elementType = getNamedType(field.type.ofType)
            if (isObjectType(elementType)) {
              currentPath[currentPath.length - 1] += '[]'
            }
          }

          if (!selection.selectionSet) {
            subFields.push(currentPath.join('.'))
          } else {
            const nextParentType = isObjectType(fieldType) ? fieldType : parentType
            subFields = subFields.concat(extractFields(selection.selectionSet, currentPath, nextParentType))
          }
        })
      }
      return subFields
    }

    info.fieldNodes.forEach((fieldNode: FieldNode) => {
      const firstField: string = fieldNode.name.value
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-ignore
      const firstFieldType = info.schema.getQueryType().getFields()[firstField].type
      const rootType: GraphQLObjectType = getNamedType(
        firstFieldType as GraphQLInputType
      ) as unknown as GraphQLObjectType

      if (fieldNode.selectionSet) {
        fields = fields.concat(extractFields(fieldNode.selectionSet, [], rootType))
      }
    })

    return fields
  }
}

function toReadModelRequestEnvelope(
  readModelClass: Class<ReadModelInterface>,
  args: ReadModelRequestArgs<ReadModelInterface>,
  context: GraphQLResolverContext,
  paginatedVersion = false,
  select?: ProjectionFor<unknown> | undefined
): ReadModelRequestEnvelope<ReadModelInterface> {
  return {
    requestID: context.requestID,
    currentUser: context.user,
    class: readModelClass,
    className: readModelClass.name,
    filters: args.filter ?? {},
    sortBy: args.sortBy ?? {},
    limit: args.limit,
    afterCursor: args.afterCursor,
    paginatedVersion,
    version: 1, // TODO: How to pass the version through GraphQL?
    select,
  }
}

function toEventSearchRequest(args: EventSearchParameters, context: GraphQLResolverContext): EventSearchRequest {
  return {
    requestID: context.requestID,
    currentUser: context.user,
    parameters: args,
  }
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function toEnvelope(typeName: string, value: any, context: GraphQLResolverContext): CommandEnvelope | QueryEnvelope {
  return {
    requestID: context.requestID,
    currentUser: context.user,
    typeName: typeName,
    value,
    version: 1, // TODO: How to pass the version through GraphQL?
    context: {
      request: {
        body: context.context?.request.body,
        headers: context.context?.request.headers,
      },
      rawContext: context,
    },
  }
}
