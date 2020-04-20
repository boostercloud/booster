import {
  BoosterConfig,
  Logger,
  Instance,
  ReadModelEnvelope,
  ReadModelInterface,
  SubscriptionEnvelope,
} from '@boostercloud/framework-types'
import { GraphQLSchema } from 'graphql'
import { GraphQLGenerator } from './services/graphql/graphql-generator'
import { BoosterCommandDispatcher } from './booster-command-dispatcher'
import { BoosterReadModelDispatcher } from './booster-read-model-dispatcher'

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
      const readModelsByName = this.organizeReadModelsByName(readModelEnvelopes)
      for (const readModelName in readModelsByName) {
        const subscriptions = await this.getSubscriptions(readModelName)
        await this.handleReadModelsToSubscriptions(readModelsByName[readModelName], subscriptions)
      }
      console.log(this.graphQLSchema)
    } catch (e) {
      this.logger.error(e)
      // return this.config.provider.handleSubscriptionError(e)
    }
  }

  private organizeReadModelsByName(
    readModelEnvelopes: Array<ReadModelEnvelope>
  ): Record<string, Array<ReadModelInterface & Instance>> {
    return readModelEnvelopes.reduce<Record<string, Array<ReadModelInterface & Instance>>>(
      (readModelsByName, envelope) => {
        const readModelInstance = this.getReadModelInstance(envelope)
        const currentReadModels = readModelsByName[envelope.typeName] ?? []
        readModelsByName[envelope.typeName] = [...currentReadModels, readModelInstance]
        return readModelsByName
      },
      {}
    )
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

  private async getSubscriptions(readModelName: string): Promise<Array<SubscriptionEnvelope>> {
    return await this.config.provider.fetchSubscriptions(this.config, this.logger, readModelName)
  }

  private async handleReadModelsToSubscriptions(
    readModels: Array<ReadModelInterface>,
    subscriptions: Array<SubscriptionEnvelope>
  ): Promise<void> {
    this.logger.debug(
      'Handling the following read models: ',
      readModels,
      ' To the following subscriptions: ',
      subscriptions
    )
    // for (const readModel of readModels) {
      // - Create FilteredPubSub with this readmodel and filters
      // - Execute subscription to get the async interator
      // - call next() and that value is the GOOD one.

      // this.logger.debug('For read model: ', readModel, 'the following subscriptions matched: ', subscriptionsToNotify)
      // // const resolvedReadModel = this.resolveReadModel(readModel)
      // await this.notifySubscriptionsWithReadModel(subscriptionsToNotify, readModel)
    // }
  }

  // private async notifySubscriptionsWithReadModel(
  //   subscriptions: Array<SubscriptionEnvelope>,
  //   readModel: ReadModelInterface
  // ): Promise<void> {
  //   this.logger.debug('Notifying matched subscriptions with read model name ' + readModel.constructor.name)
  //   await Promise.all(
  //     subscriptions.map((sub) => this.config.provider.notifySubscription(this.config, sub.connectionID, readModel))
  //   )
  //   this.logger.debug('Notifications sent')
  // }
}
