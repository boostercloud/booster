import {
  BoosterConfig,
  Logger,
  Instance,
  ReadModelEnvelope,
  ReadModelInterface,
  ReadModelPropertyFilter,
  SubscriptionEnvelope,
} from '@boostercloud/framework-types'
import { GraphQLSchema } from 'graphql'
import { GraphQLGenerator } from './services/graphql/graphql-generator'
import { BoosterCommandDispatcher } from './booster-command-dispatcher'
import { BoosterReadModelDispatcher } from './booster-read-model-dispatcher'

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
    for (const readModel of readModels) {
      const subscriptionsToNotify = this.filterSubscriptionsByReadModel(subscriptions, readModel)
      this.logger.debug('For read model: ', readModel, 'the following subscriptions matched: ', subscriptionsToNotify)
      await this.notifySubscriptionsWithReadModel(subscriptionsToNotify, readModel)
    }
  }

  private filterSubscriptionsByReadModel(
    subscriptions: Array<SubscriptionEnvelope>,
    readModel: ReadModelInterface
  ): Array<SubscriptionEnvelope> {
    return subscriptions.filter((subscriptionEnvelope): boolean => {
      if (!subscriptionEnvelope.filters) {
        return true
      }
      return this.readModelMatchesFilters(readModel, subscriptionEnvelope.filters)
    })
  }

  private readModelMatchesFilters(
    readModel: Record<string, any> & ReadModelInterface,
    filters: Record<string, ReadModelPropertyFilter>
  ): boolean {
    for (const filteredProp in filters) {
      const readModelPropValue = readModel[filteredProp]
      const { operation, values } = filters[filteredProp]

      switch (operation) {
        case '=':
          if (readModelPropValue !== values[0]) return false
          break
        case '!=':
          if (readModelPropValue === values[0]) return false
          break
        case '<':
          if (readModelPropValue >= values[0]) return false
          break
        case '>':
          if (readModelPropValue <= values[0]) return false
          break
        case '>=':
          if (readModelPropValue < values[0]) return false
          break
        case '<=':
          if (readModelPropValue > values[0]) return false
          break
        case 'in':
          if (!values.includes(readModelPropValue)) return false
          break
        case 'between':
          if (readModelPropValue < values[0] || readModelPropValue > values[1]) return false
          break
        case 'contains':
          if (!contains(readModelPropValue, values[0])) return false
          break
        case 'not-contains':
          if (contains(readModelPropValue, values[0])) return false
          break
        case 'begins-with':
          if (!beginWith(readModelPropValue, values[0] as string)) return false
          break
      }
      return values != undefined
    }
    return true
  }

  private async notifySubscriptionsWithReadModel(
    subscriptions: Array<SubscriptionEnvelope>,
    readModel: ReadModelInterface
  ): Promise<void> {
    this.logger.debug('Notifying matched subscriptions with read model name ' + readModel.constructor.name)
    await Promise.all(
      subscriptions.map((sub) => this.config.provider.notifySubscription(this.config, sub.connectionID, readModel))
    )
    this.logger.debug('Notifications sent')
  }
}

function contains(readModelPropValue: any, element: any): boolean {
  if (Array.isArray(readModelPropValue) || typeof readModelPropValue === 'string') {
    return readModelPropValue.includes(element)
  }
  return false
}

function beginWith(readModelPropValue: any, element: string): boolean {
  if (typeof readModelPropValue === 'string') {
    return readModelPropValue.startsWith(element)
  }
  return false
}
