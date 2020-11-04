/* eslint-disable @typescript-eslint/no-explicit-any */
import {
  BoosterConfig,
  Logger,
  InvalidParameterError,
  NotAuthorizedError,
  NotFoundError,
  ReadModelRequestEnvelope,
  ReadModelInterface,
  SubscriptionEnvelope,
  GraphQLOperation,
} from '@boostercloud/framework-types'
import { BoosterAuth } from './booster-auth'
import { Booster } from './booster'

export class BoosterReadModelDispatcher {
  public constructor(readonly config: BoosterConfig, readonly logger: Logger) {}

  public async fetch(readModelRequest: ReadModelRequestEnvelope): Promise<Array<ReadModelInterface>> {
    this.validateRequest(readModelRequest)
    return this.processFetch(readModelRequest)
  }

  public async subscribe(
    connectionID: string,
    readModelRequest: ReadModelRequestEnvelope,
    operation: GraphQLOperation
  ): Promise<unknown> {
    this.validateRequest(readModelRequest)
    return this.processSubscription(connectionID, readModelRequest, operation)
  }

  public async unsubscribe(connectionID: string, subscriptionID: string): Promise<void> {
    return this.config.provider.readModels.deleteSubscription(this.config, this.logger, connectionID, subscriptionID)
  }

  public async unsubscribeAll(connectionID: string): Promise<void> {
    return this.config.provider.readModels.deleteAllSubscriptions(this.config, this.logger, connectionID)
  }

  private validateRequest(readModelRequest: ReadModelRequestEnvelope): void {
    this.logger.debug('Validating the following read model request: ', readModelRequest)
    if (!readModelRequest.version) {
      throw new InvalidParameterError('The required request "version" was not present')
    }

    const readModelMetadata = this.config.readModels[readModelRequest.typeName]
    if (!readModelMetadata) {
      throw new NotFoundError(`Could not find read model ${readModelRequest.typeName}`)
    }

    if (!BoosterAuth.isUserAuthorized(readModelMetadata.authorizedRoles, readModelRequest.currentUser)) {
      throw new NotAuthorizedError(`Access denied for read model ${readModelRequest.typeName}`)
    }
  }

  private async processFetch(readModelRequest: ReadModelRequestEnvelope): Promise<Array<ReadModelInterface>> {
    const readModelMetadata = this.config.readModels[readModelRequest.typeName]
    const searcher = Booster.readModel(readModelMetadata.class)
    if (readModelRequest.filters) {
      for (const propName in readModelRequest.filters) {
        const filter = readModelRequest.filters[propName]
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        searcher.filterOld(propName as any, filter.operation as any, ...filter.values)
      }
    }
    return searcher.search()
  }

  private async processSubscription(
    connectionID: string,
    readModelRequest: ReadModelRequestEnvelope,
    operation: GraphQLOperation
  ): Promise<void> {
    this.logger.info(
      `Processing subscription of connection '${connectionID}' to read model '${readModelRequest.typeName}' with the following data: `,
      readModelRequest
    )

    const nowEpoch = Math.floor(new Date().getTime() / 1000)
    const subscription: SubscriptionEnvelope = {
      ...readModelRequest,
      expirationTime: nowEpoch + this.config.subscriptions.maxDurationInSeconds,
      connectionID,
      operation,
    }
    return this.config.provider.readModels.subscribe(this.config, this.logger, subscription)
  }
}
