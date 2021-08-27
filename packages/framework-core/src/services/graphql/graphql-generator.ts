import {
  AnyClass,
  BoosterConfig,
  Class,
  CommandEnvelope,
  EventFilter,
  EventSearchRequest,
  EventSearchResponse,
  Logger,
  ReadModelByIdRequestArgs,
  ReadModelInterface,
  ReadModelRequestArgs,
  ReadModelRequestEnvelope,
  ReadModelRequestProperties,
  TimeKey,
} from '@boostercloud/framework-types'
import { GraphQLFieldResolver, GraphQLResolveInfo, GraphQLSchema } from 'graphql'
import { pluralize } from 'inflected'
import { BoosterCommandDispatcher } from '../../booster-command-dispatcher'
import { BoosterEventsReader } from '../../booster-events-reader'
import { BoosterReadModelsReader } from '../../booster-read-models-reader'
import { GraphQLResolverContext } from './common'
import { GraphQLMutationGenerator } from './graphql-mutation-generator'
import { GraphQLQueryGenerator } from './graphql-query-generator'
import { GraphQLSubscriptionGenerator } from './graphql-subcriptions-generator'
import { GraphQLTypeInformer } from './graphql-type-informer'

export class GraphQLGenerator {
  private static commandsDispatcher: BoosterCommandDispatcher
  private static readModelsReader: BoosterReadModelsReader
  private static eventsReader: BoosterEventsReader
  private static schema: GraphQLSchema

  public static generateSchema(config: BoosterConfig, logger: Logger): GraphQLSchema {
    if (!this.schema) {
      this.commandsDispatcher = new BoosterCommandDispatcher(config, logger)
      this.readModelsReader = new BoosterReadModelsReader(config, logger)
      this.eventsReader = new BoosterEventsReader(config, logger)

      const typeInformer = new GraphQLTypeInformer({
        ...config.readModels,
        ...config.commandHandlers,
      })

      const queryGenerator = new GraphQLQueryGenerator(
        config,
        config.readModels,
        typeInformer,
        this.readModelByIDResolverBuilder.bind(this, config),
        this.readModelResolverBuilder.bind(this),
        this.eventResolver.bind(this)
      )

      const mutationGenerator = new GraphQLMutationGenerator(
        config.commandHandlers,
        typeInformer,
        this.commandResolverBuilder.bind(this)
      )

      const subscriptionGenerator = new GraphQLSubscriptionGenerator(
        config.readModels,
        typeInformer,
        queryGenerator,
        this.subscriptionByIDResolverBuilder.bind(this, config),
        this.subscriptionResolverBuilder.bind(this, config)
      )

      this.schema = new GraphQLSchema({
        query: queryGenerator.generate(),
        mutation: mutationGenerator.generate(),
        subscription: subscriptionGenerator.generate(),
      })
    }
    return this.schema
  }

  public static readModelResolverBuilder(
    readModelClass: AnyClass
  ): GraphQLFieldResolver<any, GraphQLResolverContext, ReadModelRequestArgs<ReadModelInterface>> {
    return (parent, args, context, info) => {
      let isPaginated = false
      if (info?.fieldName === `List${pluralize(readModelClass.name)}`) {
        isPaginated = true
      }
      const readModelEnvelope = toReadModelRequestEnvelope(readModelClass, args, context, isPaginated)
      return this.readModelsReader.search(readModelEnvelope)
    }
  }

  public static readModelByIDResolverBuilder(
    config: BoosterConfig,
    readModelClass: AnyClass
  ): GraphQLFieldResolver<unknown, GraphQLResolverContext, ReadModelByIdRequestArgs> {
    const sequenceKeyName = config.readModelSequenceKeys[readModelClass.name]
    return async (parent, args, context, info) => {
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
    args: EventFilter,
    context: GraphQLResolverContext,
    info: GraphQLResolveInfo
  ): Promise<Array<EventSearchResponse>> {
    const eventsRequestEnvelope = toEventSearchRequest(args, context)
    return this.eventsReader.fetch(eventsRequestEnvelope)
  }

  public static commandResolverBuilder(
    commandClass: AnyClass
  ): GraphQLFieldResolver<any, GraphQLResolverContext, { input: any }> {
    return async (parent, args, context, info) => {
      const commandEnvelope = toCommandEnvelope(commandClass.name, args.input, context)
      const result = await this.commandsDispatcher.dispatchCommand(commandEnvelope)
      // It could be that the command didn't return anything
      // so in that case we return `true`, as GraphQL doesn't have a `null` type
      return result ?? true
    }
  }

  public static subscriptionByIDResolverBuilder(
    config: BoosterConfig,
    readModelClass: AnyClass
  ): GraphQLFieldResolver<any, GraphQLResolverContext, ReadModelRequestProperties<ReadModelInterface>> {
    return async (parent, args, context, info) => {
      const filterArgs = { filter: { id: { eq: args.id } } }
      return this.subscriptionResolverBuilder(config, readModelClass)(parent, filterArgs, context, info)
    }
  }

  public static subscriptionResolverBuilder(
    config: BoosterConfig,
    readModelClass: AnyClass
  ): GraphQLFieldResolver<any, GraphQLResolverContext, ReadModelRequestArgs<ReadModelInterface>> {
    return async (parent, args, context, info) => {
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
    }
  }
}

function toReadModelRequestEnvelope(
  readModelClass: Class<ReadModelInterface>,
  args: ReadModelRequestArgs<ReadModelInterface>,
  context: GraphQLResolverContext,
  paginatedVersion = false
): ReadModelRequestEnvelope<ReadModelInterface> {
  return {
    requestID: context.requestID,
    currentUser: context.user,
    class: readModelClass,
    className: readModelClass.name,
    filters: args.filter ?? {},
    limit: args.limit,
    afterCursor: args.afterCursor,
    paginatedVersion,
    version: 1, // TODO: How to pass the version through GraphQL?
  }
}

function toEventSearchRequest(args: EventFilter, context: GraphQLResolverContext): EventSearchRequest {
  return {
    requestID: context.requestID,
    currentUser: context.user,
    filters: args,
  }
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function toCommandEnvelope(commandName: string, value: any, context: GraphQLResolverContext): CommandEnvelope {
  return {
    requestID: context.requestID,
    currentUser: context.user,
    typeName: commandName,
    value,
    version: 1, // TODO: How to pass the version through GraphQL?
  }
}
