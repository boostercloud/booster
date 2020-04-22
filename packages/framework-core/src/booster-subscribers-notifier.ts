import {
  BoosterConfig,
  Logger,
  Instance,
  ReadModelEnvelope,
  ReadModelInterface,
  SubscriptionEnvelope,
} from '@boostercloud/framework-types'
import { GraphQLSchema, subscribe, parse, validate, DocumentNode } from 'graphql'
import { GraphQLGenerator } from './services/graphql/graphql-generator'
import { BoosterCommandDispatcher } from './booster-command-dispatcher'
import { BoosterReadModelDispatcher } from './booster-read-model-dispatcher'
import { FilteredReadModelPubSub, ReadModelPubSub } from './services/pub-sub/read-model-pub-sub'
import { GraphQLResolverContext, throwIfGraphQLErrors } from './services/graphql/common'
import { ExecutionResult } from 'graphql/execution/execute'

export class BoosterSubscribersNotifier {
  private readonly graphQLSchema: GraphQLSchema

  public constructor(private config: BoosterConfig, private logger: Logger) {
    this.graphQLSchema = new GraphQLGenerator(
      config,
      new BoosterCommandDispatcher(config, logger),
      new BoosterReadModelDispatcher(config, logger)
    ).generateSchema()
  }

  public async dispatch(request: any): Promise<any> {
    try {
      this.logger.debug('Received the following event for subscription dispatching: ', request)
      const readModelEnvelopes = await this.config.provider.rawReadModelEventsToEnvelopes(
        this.config,
        this.logger,
        request
      )
      this.logger.debug('[SubsciptionDispatcher] The following ReadModels were updated: ', readModelEnvelopes)
      const subscriptions = await this.getSubscriptions(readModelEnvelopes)
      this.logger.debug(
        '[SubsciptionDispatcher] Found the following subscriptions for those read models: ',
        subscriptions
      )

      const pubSub = this.getPubSub(readModelEnvelopes)
      await Promise.all(subscriptions.map(this.runSubscriptionAndNotify.bind(this, pubSub)))
    } catch (e) {
      this.logger.error(e)
      // return this.config.provider.handleSubscriptionError(e)
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
      readModelUniqueNames.map((name) => this.config.provider.fetchSubscriptions(this.config, this.logger, name))
    )
    return subscriptionSets.flat()
  }

  private getPubSub(readModelEnvelopes: Array<ReadModelEnvelope>): ReadModelPubSub {
    const readModelInstances = readModelEnvelopes.map(this.getReadModelInstance, this)
    return new FilteredReadModelPubSub(readModelInstances)
  }

  private async runSubscriptionAndNotify(pubSub: ReadModelPubSub, subscription: SubscriptionEnvelope): Promise<void> {
    const context: GraphQLResolverContext = {
      connectionID: subscription.connectionID,
      requestID: subscription.requestID,
      user: subscription.currentUser,
      operation: subscription.operation,
      pubSub,
      storeSubscriptions: false, // We don't store the subscription again, just get the result now
    }
    const document = this.parseSubscriptionQuery(subscription.operation.query)
    this.logger.debug('Running subscription with context: ', context)
    const iterator = await subscribe<ReadModelInterface>({
      contextValue: context,
      document: document,
      schema: this.graphQLSchema,
      variableValues: subscription.operation.variables,
    })
    if ('next' in iterator) {
      // It is an AsyncIterator
      return this.processSubscriptionsIterator(iterator, subscription)
    }
    throwIfGraphQLErrors(iterator.errors)
  }

  private parseSubscriptionQuery(query: string): DocumentNode {
    const document = parse(query)
    // We probably don't need to validate this again, as it was validated before storing it. BUT! It's always better to fail early
    const errors = validate(this.graphQLSchema, document)
    if (errors.length > 0) {
      throw new Error(errors.join('. '))
    }
    return document
  }

  private async processSubscriptionsIterator(
    iterator: AsyncIterableIterator<ExecutionResult<ReadModelInterface>>,
    subscription: SubscriptionEnvelope
  ): Promise<any> {
    const notificationPromises: Array<Promise<void>> = []
    for await (const result of iterator) {
      notificationPromises.push(this.notifyWithGraphQLResult(subscription, result))
    }
    return Promise.all(notificationPromises)
  }

  private async notifyWithGraphQLResult(
    subscription: SubscriptionEnvelope,
    result: ExecutionResult<ReadModelInterface>
  ): Promise<void> {
    if ('errors' in result) {
      throwIfGraphQLErrors(result.errors)
      return
    }
    const readModel = result.data as ReadModelInterface
    this.logger.debug(`Notifying connectionID '${subscription.connectionID}' with read model: `, readModel)
    await this.config.provider.notifySubscription(this.config, subscription.connectionID, readModel)
    this.logger.debug('Notifications sent')
  }
}
