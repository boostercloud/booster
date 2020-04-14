import { BoosterConfig, Logger, Instance } from '@boostercloud/framework-types'
import { GraphQLSchema } from 'graphql'
import { GraphQLGenerator } from './services/graphql/graphql-generator'
import { BoosterCommandDispatcher } from './booster-command-dispatcher'
import { BoosterReadModelDispatcher } from './booster-read-model-dispatcher'
import { ReadModelEnvelope, ReadModelInterface, SubscriptionEnvelope } from '@boostercloud/framework-types/dist'

export class BoosterSubscriptionDispatcher {
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
        this.handleReadModelsToSubscriptions(readModelsByName[readModelName], subscriptions)
      }
      /**
       * 1.- Organize envelopes in a hash by readmodel name. Then for each key and value (which is an array)
       * 2.- Query the subscription using the readModel name as subscriptionName. Then for each readModel instance and subscription result
       * 3.- Filter those whose subscription.filter does not match the readModel.value (could this be done in Dynamo?)
       * 4.- Use GraphQL to get the final data to return.
       * 5.- Send that data using the websocket API
       */
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

  private handleReadModelsToSubscriptions(
    readModels: Array<ReadModelInterface>,
    subscriptions: Array<SubscriptionEnvelope>
  ): void {
    this.logger.debug('Handling the following read models:')
    this.logger.debug(readModels)
    this.logger.debug('To the following subscriptions:')
    this.logger.debug(subscriptions)
  }
}
