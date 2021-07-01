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
  ReadModelPropertyFilter,
} from '@boostercloud/framework-types'
import { BoosterAuth } from './booster-auth'
import { Booster } from './booster'
import { getReadModelFilters } from './services/filter-helpers'

export class BoosterReadModelsReader {
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

    const filters = getReadModelFilters(
      readModelRequest.filters,
      readModelMetadata.before,
      readModelRequest.currentUser
    )

    searcher.filter(filters)

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
    const readModelMetadata = this.config.readModels[readModelRequest.typeName]

    // This type is specified because there is a mismatch between types in the filters attribute (ReadModelRequestEnvelope).
    // FilterFor<unknown> is already an object itself, and contains keys and the filters as values, but right now
    // the ReadModelRequestEnvelope property is typed as Record<string, ReadModelPropertyFilter>.
    // Apparently these two types are compatible by accident, which made us think that this could be a bug.
    readModelRequest.filters = getReadModelFilters(
      readModelRequest.filters,
      readModelMetadata.before,
      readModelRequest.currentUser
    ) as Record<string, ReadModelPropertyFilter>

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
