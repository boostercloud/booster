/* eslint-disable @typescript-eslint/no-explicit-any */
import {
  BoosterConfig,
  Instance,
  ReadModelEnvelope,
  ReadModelInterface,
  SubscriptionEnvelope,
  GraphQLData,
  TraceActionTypes,
} from '@boostercloud/framework-types'
import { GraphQLSchema, DocumentNode } from 'graphql'
import * as graphql from 'graphql'
import { GraphQLGenerator } from './services/graphql/graphql-generator'
import { FilteredReadModelPubSub, ReadModelPubSub } from './services/pub-sub/read-model-pub-sub'
import { GraphQLResolverContext } from './services/graphql/common'
import { ExecutionResult } from 'graphql/execution/execute'
import { Promises, getLogger } from '@boostercloud/framework-common-helpers'
import { Trace } from './instrumentation'

export class BoosterSubscribersNotifier {
  private readonly graphQLSchema: GraphQLSchema

  public constructor(private config: BoosterConfig) {
    this.graphQLSchema = GraphQLGenerator.generateSchema(config)
  }

  @Trace(TraceActionTypes.DISPATCH_SUBSCRIBER_NOTIFIER)
  public async dispatch(request: unknown): Promise<void> {
    const logger = getLogger(this.config, 'BoosterSubscribersNotifier#dispatch')
    try {
      logger.debug('Received the following event for subscription dispatching: ', request)
      const readModelEnvelopes = await this.config.provider.readModels.rawToEnvelopes(this.config, request)
      logger.debug('[SubsciptionDispatcher] The following ReadModels were updated: ', readModelEnvelopes)
      const subscriptions = await this.getSubscriptions(readModelEnvelopes)
      logger.debug('Found the following subscriptions for those read models: ', subscriptions)

      const pubSub = this.getPubSub(readModelEnvelopes)
      await Promises.allSettledAndFulfilled(subscriptions.map(this.runSubscriptionAndNotify.bind(this, pubSub)))
    } catch (e) {
      logger.error(e)
    }
  }

  private getReadModelInstance(envelope: ReadModelEnvelope): ReadModelInterface & Instance {
    const readModelMetadata = this.config.readModels[envelope.typeName]
    if (!readModelMetadata) {
      throw new Error('Could not get information about read model with name: ' + envelope.typeName)
    }
    const readModelInstance = new readModelMetadata.class()
    Object.assign(readModelInstance, envelope.value)
    return readModelInstance
  }

  private async getSubscriptions(readModelEnvelopes: Array<ReadModelEnvelope>): Promise<Array<SubscriptionEnvelope>> {
    const readModelNames = readModelEnvelopes.map((readModelEnvelope) => readModelEnvelope.typeName)
    const readModelUniqueNames = [...new Set(readModelNames)]
    const subscriptionSets = await Promise.all(
      readModelUniqueNames.map((name) => this.config.provider.readModels.fetchSubscriptions(this.config, name))
    )
    return subscriptionSets.flat()
  }

  private getPubSub(readModelEnvelopes: Array<ReadModelEnvelope>): ReadModelPubSub<ReadModelInterface> {
    const readModelInstances = readModelEnvelopes.map(this.getReadModelInstance, this)
    return new FilteredReadModelPubSub(readModelInstances)
  }

  private async runSubscriptionAndNotify(
    pubSub: ReadModelPubSub<ReadModelInterface>,
    subscription: SubscriptionEnvelope
  ): Promise<unknown> {
    const logger = getLogger(this.config, 'BoosterSubscribersNotifier#runSubscriptionAndNotify')
    const context: GraphQLResolverContext = {
      connectionID: subscription.connectionID,
      responseHeaders: {},
      requestID: subscription.requestID,
      user: subscription.currentUser,
      operation: subscription.operation,
      pubSub,
      storeSubscriptions: false, // We don't store the subscription again, just get the result now
    }
    const document = this.parseSubscriptionQuery(subscription.operation.query)
    logger.debug('Running subscription with context: ', context)
    const iterator = await graphql.subscribe({
      contextValue: context,
      document: document,
      schema: this.graphQLSchema,
      variableValues: subscription.operation.variables,
    })
    if ('next' in iterator) {
      // It is an AsyncIterator
      return this.processSubscriptionsIterator(iterator, subscription)
    }
    // If "subscribe" returns an ExecutionResult (instead of an async iterator) the subscription failed.
    throw iterator.errors
  }

  private parseSubscriptionQuery(query: string): DocumentNode {
    const document = graphql.parse(query)
    // We probably don't need to validate this again, as it was validated before storing it. BUT! It's always better to fail early
    const errors = graphql.validate(this.graphQLSchema, document)
    if (errors.length > 0) {
      throw errors
    }
    return document
  }

  private async processSubscriptionsIterator(
    iterator: AsyncIterableIterator<ExecutionResult>,
    subscription: SubscriptionEnvelope
  ): Promise<unknown> {
    const notificationPromises: Array<Promise<void>> = []
    for await (const result of iterator) {
      notificationPromises.push(this.notifyWithGraphQLResult(subscription, result))
    }
    return Promise.all(notificationPromises)
  }

  private async notifyWithGraphQLResult(subscription: SubscriptionEnvelope, result: ExecutionResult): Promise<void> {
    const logger = getLogger(this.config, 'BoosterSubscribersNotifier#notifyWithGraphQLResult')
    if (result.errors) {
      throw result.errors
    }
    const readModel = result.data as ReadModelInterface
    const message = new GraphQLData(subscription.operation.id!, { data: readModel })
    logger.debug(
      `Notifying connectionID '${subscription.connectionID}' with the following wrappeed read model: `,
      readModel
    )
    await this.config.provider.connections.sendMessage(this.config, subscription.connectionID, message)
    logger.debug('Notifications sent')
  }
}
