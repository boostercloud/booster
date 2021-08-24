/* eslint-disable @typescript-eslint/no-explicit-any */
import {
  BoosterConfig,
  GraphQLOperation,
  InvalidParameterError,
  Logger,
  NotAuthorizedError,
  NotFoundError,
  ReadModelByIdRequestEnvelope,
  ReadModelInterface,
  ReadModelListResult,
  Searcher,
  Class,
  FilterFor,
  UserEnvelope,
  ReadModelPropertyFilter,
  ReadModelRequestEnvelope,
  ReadOnlyNonEmptyArray,
  SubscriptionEnvelope,
} from '@boostercloud/framework-types'
import { Booster } from './booster'
import { BoosterAuth } from './booster-auth'
import { getReadModelFilters } from './services/filter-helpers'

export class BoosterReadModelsReader {
  public constructor(readonly config: BoosterConfig, readonly logger: Logger) {}

  public async findById(
    readModelByIdRequestEnvelope: ReadModelByIdRequestEnvelope
  ): Promise<ReadOnlyNonEmptyArray<ReadModelInterface>> {
    this.validateByIdRequest(readModelByIdRequestEnvelope)

    return this.initializeSearcherWithFilters(
      readModelByIdRequestEnvelope.typeName,
      readModelByIdRequestEnvelope.currentUser
    ).findById(readModelByIdRequestEnvelope.id, readModelByIdRequestEnvelope.sequenceKey)
  }

  public async search(
    readModelRequest: ReadModelRequestEnvelope
  ): Promise<ReadModelInterface[] | ReadModelListResult<ReadModelInterface>> {
    this.validateRequest(readModelRequest)

    return this.initializeSearcherWithFilters(
      readModelRequest.typeName,
      readModelRequest.currentUser,
      readModelRequest.filters
    )
      .limit(readModelRequest.limit)
      .afterCursor(readModelRequest.afterCursor)
      .paginatedVersion(readModelRequest.paginatedVersion)
      .search()
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

  private validateByIdRequest(readModelByIdRequest: ReadModelByIdRequestEnvelope): void {
    this.logger.debug('Validating the following read model by id request: ', readModelByIdRequest)
    if (!readModelByIdRequest.version) {
      throw new InvalidParameterError('The required request "version" was not present')
    }

    const readModelMetadata = this.config.readModels[readModelByIdRequest.typeName]
    if (!readModelMetadata) {
      throw new NotFoundError(`Could not find read model ${readModelByIdRequest.typeName}`)
    }

    if (!BoosterAuth.isUserAuthorized(readModelMetadata.authorizedRoles, readModelByIdRequest.currentUser)) {
      throw new NotAuthorizedError(`Access denied for read model ${readModelByIdRequest.typeName}`)
    }

    if (
      readModelByIdRequest.sequenceKey &&
      readModelByIdRequest.sequenceKey.name !== this.config.readModelSequenceKeys[readModelByIdRequest.typeName]
    ) {
      throw new InvalidParameterError(
        `Could not find a sort key defined for ${readModelByIdRequest.typeName} named '${readModelByIdRequest.sequenceKey?.name}'.`
      )
    }
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

  private initializeSearcherWithFilters(
    typeName: string,
    currentUser?: UserEnvelope,
    filters?: FilterFor<Class<ReadModelInterface>>
  ): Searcher<ReadModelInterface> {
    const readModelMetadata = this.config.readModels[typeName]
    const searcher = Booster.readModel(readModelMetadata.class)

    const readModelFilters = getReadModelFilters(filters ?? {}, readModelMetadata.before, currentUser)

    return searcher.filter(readModelFilters)
  }
}
