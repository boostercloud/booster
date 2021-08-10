import {
  AnyClass,
  Logger,
  BoosterConfig,
  ReadModelPropertyFilter,
  ReadModelRequestEnvelope,
  EventSearchRequest,
  EventFilter,
  EventSearchResponse,
  ReadModelRequestArgs,
} from '@boostercloud/framework-types'
import { GraphQLFieldResolver, GraphQLObjectType, GraphQLResolveInfo, GraphQLSchema } from 'graphql'
import { pluralize } from 'inflected'
import { GraphQLTypeInformer } from './graphql-type-informer'
import { GraphQLQueryGenerator } from './graphql-query-generator'
import { GraphQLCommandMutationGenerator } from './graphql-mutation-generator'
import { GraphQLSubscriptionGenerator } from './graphql-subcriptions-generator'
import { BoosterReadModelsReader } from '../../booster-read-models-reader'
import { BoosterEventsReader } from '../../booster-events-reader'
import { GraphQLResolverContext } from './common'

export class GraphQLGenerator {
  private static readModelsReader: BoosterReadModelsReader
  private static eventsReader: BoosterEventsReader
  private static schema: GraphQLSchema

  public static generateSchema(config: BoosterConfig, logger: Logger): GraphQLSchema {
    if (!this.schema) {
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
        this.readModelByIDResolverBuilder.bind(this),
        this.readModelResolverBuilder.bind(this),
        this.eventResolver.bind(this)
      )

      const subscriptionGenerator = new GraphQLSubscriptionGenerator(
        config.readModels,
        typeInformer,
        queryGenerator,
        this.subscriptionByIDResolverBuilder.bind(this, config),
        this.subscriptionResolverBuilder.bind(this, config)
      )

      // Build the mutation types
      let mutation: GraphQLObjectType<unknown, unknown> | undefined = undefined
      /* TODO: In the future, this structure can be loaded dynamically
       * from the Booster configuration. This will alow rockets to add mutations
       * for new use cases (They can already add them declaring regular commands) */
      const mutations = { ...GraphQLCommandMutationGenerator.generate(config, logger) }
      if (Object.keys(mutations).length > 0) {
        mutation = new GraphQLObjectType({
          name: 'Mutation',
          fields: mutations,
        })
      }

      this.schema = new GraphQLSchema({
        mutation,
        query: queryGenerator.generate(),
        subscription: subscriptionGenerator.generate(),
      })
    }
    return this.schema
  }

  public static readModelResolverBuilder(
    readModelClass: AnyClass
  ): GraphQLFieldResolver<any, GraphQLResolverContext, ReadModelRequestArgs> {
    return (parent, args, context, info) => {
      let isPaginated = false
      if (info?.fieldName === `List${pluralize(readModelClass.name)}`) {
        isPaginated = true
      }
      const readModelEnvelope = toReadModelRequestEnvelope(readModelClass.name, args, context, isPaginated)
      return this.readModelsReader.fetch(readModelEnvelope)
    }
  }

  public static readModelByIDResolverBuilder(
    readModelClass: AnyClass
  ): GraphQLFieldResolver<unknown, GraphQLResolverContext, { id: string }> {
    return async (parent, args, context, info) => {
      const filterArgs = { filter: { id: { eq: args.id } } }
      const result = await this.readModelResolverBuilder(readModelClass)(parent, filterArgs, context, info)
      return result[0]
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

  public static subscriptionByIDResolverBuilder(
    config: BoosterConfig,
    readModelClass: AnyClass
  ): GraphQLFieldResolver<any, GraphQLResolverContext, Record<string, ReadModelPropertyFilter>> {
    return async (parent, args, context, info) => {
      const filterArgs = { filter: { id: { eq: args.id } } }
      return this.subscriptionResolverBuilder(config, readModelClass)(parent, filterArgs, context, info)
    }
  }

  public static subscriptionResolverBuilder(
    config: BoosterConfig,
    readModelClass: AnyClass
  ): GraphQLFieldResolver<any, GraphQLResolverContext, ReadModelRequestArgs> {
    return async (parent, args, context, info) => {
      if (!context.connectionID) {
        throw new Error('Missing "connectionID". It is required for subscriptions')
      }

      const readModelRequestEnvelope = toReadModelRequestEnvelope(readModelClass.name, args, context)
      if (context.storeSubscriptions) {
        await this.readModelsReader.subscribe(context.connectionID, readModelRequestEnvelope, context.operation)
      }

      return context.pubSub.asyncIterator(readModelRequestEnvelope, config)
    }
  }
}

function toReadModelRequestEnvelope(
  readModelName: string,
  args: ReadModelRequestArgs,
  context: GraphQLResolverContext,
  paginatedVersion = false
): ReadModelRequestEnvelope {
  return {
    requestID: context.requestID,
    currentUser: context.user,
    typeName: readModelName,
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
