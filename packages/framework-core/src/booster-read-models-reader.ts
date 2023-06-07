/* eslint-disable @typescript-eslint/no-explicit-any */
import {
  TraceActionTypes,
  AnyClass,
  BoosterConfig,
  FilterFor,
  GraphQLOperation,
  InvalidParameterError,
  NotFoundError,
  ReadModelInterface,
  ReadModelListResult,
  ReadModelRequestEnvelope,
  ReadOnlyNonEmptyArray,
  SortFor,
  SubscriptionEnvelope,
} from '@boostercloud/framework-types'
import { createInstance, createInstances, getLogger } from '@boostercloud/framework-common-helpers'
import { Booster } from './booster'
import { applyReadModelRequestBeforeFunctions } from './services/filter-helpers'
import { ReadModelSchemaMigrator } from './read-model-schema-migrator'
import { Trace } from './instrumentation'

export class BoosterReadModelsReader {
  public constructor(readonly config: BoosterConfig) {}

  @Trace(TraceActionTypes.READ_MODEL_FIND_BY_ID)
  public async findById(
    readModelRequest: ReadModelRequestEnvelope<ReadModelInterface>
  ): Promise<ReadModelInterface | ReadOnlyNonEmptyArray<ReadModelInterface>> {
    await this.validateByIdRequest(readModelRequest)

    const readModelMetadata = this.config.readModels[readModelRequest.class.name]
    const readModelTransformedRequest = await applyReadModelRequestBeforeFunctions(
      readModelRequest,
      readModelMetadata.before,
      readModelRequest.currentUser
    )

    const key = readModelTransformedRequest.key
    if (!key) {
      throw 'Tried to run a findById operation without providing a key. An ID is required to perform this operation.'
    }
    const currentReadModel = await Booster.readModel(readModelMetadata.class).findById(key.id, key.sequenceKey)
    if (currentReadModel) {
      const readModelInstance = createInstance(readModelMetadata.class, currentReadModel)
      const readModelName = readModelMetadata.class.name
      const readModelSchemaMigrator = new ReadModelSchemaMigrator(this.config)
      if (Array.isArray(readModelInstance)) {
        return [await readModelSchemaMigrator.migrate(<ReadModelInterface>readModelInstance[0], readModelName)]
      }
      return readModelSchemaMigrator.migrate(<ReadModelInterface>readModelInstance, readModelName)
    }
    return currentReadModel
  }

  @Trace(TraceActionTypes.GRAPHQL_READ_MODEL_SEARCH)
  public async search(
    readModelRequest: ReadModelRequestEnvelope<ReadModelInterface>
  ): Promise<Array<ReadModelInterface> | ReadModelListResult<ReadModelInterface>> {
    await this.validateRequest(readModelRequest)

    const readModelMetadata = this.config.readModels[readModelRequest.class.name]
    const readModelTransformedRequest = await applyReadModelRequestBeforeFunctions(
      readModelRequest,
      readModelMetadata.before,
      readModelRequest.currentUser
    )
    return await this.readModelSearch(
      readModelMetadata.class,
      readModelTransformedRequest.filters,
      readModelTransformedRequest.sortBy,
      readModelTransformedRequest.limit,
      readModelTransformedRequest.afterCursor,
      readModelTransformedRequest.paginatedVersion
    )
  }

  @Trace(TraceActionTypes.READ_MODEL_SEARCH)
  public async readModelSearch<TReadModel extends ReadModelInterface>(
    readModelClass: AnyClass,
    filters: FilterFor<unknown>,
    sort?: SortFor<unknown>,
    limit?: number,
    afterCursor?: any,
    paginatedVersion?: boolean
  ): Promise<Array<TReadModel> | ReadModelListResult<TReadModel>> {
    const readModelName = readModelClass.name
    const searchResult = await this.config.provider.readModels.search<TReadModel>(
      this.config,
      readModelName,
      filters ?? {},
      sort ?? {},
      limit,
      afterCursor,
      paginatedVersion ?? false
    )

    const readModels = this.createReadModelInstances(searchResult, readModelClass)
    return this.migrateReadModels(readModels, readModelName)
  }

  private async migrateReadModels<TReadModel extends ReadModelInterface>(
    readModels: Array<TReadModel> | ReadModelListResult<TReadModel>,
    readModelName: string
  ): Promise<Array<TReadModel> | ReadModelListResult<TReadModel>> {
    const readModelSchemaMigrator = new ReadModelSchemaMigrator(this.config)
    if (Array.isArray(readModels)) {
      return Promise.all(readModels.map((readModel) => readModelSchemaMigrator.migrate(readModel, readModelName)))
    }
    readModels.items = await Promise.all(
      readModels.items.map((readModel) => readModelSchemaMigrator.migrate(readModel, readModelName))
    )
    return readModels
  }

  private createReadModelInstances<TReadModel extends ReadModelInterface>(
    searchResult: Array<TReadModel> | ReadModelListResult<TReadModel>,
    readModelClass: AnyClass
  ): Array<TReadModel> | ReadModelListResult<TReadModel> {
    if (Array.isArray(searchResult)) {
      return createInstances(readModelClass, searchResult)
    }
    return {
      ...searchResult,
      items: createInstances(readModelClass, searchResult.items),
    }
  }

  public async subscribe(
    connectionID: string,
    readModelRequest: ReadModelRequestEnvelope<ReadModelInterface>,
    operation: GraphQLOperation
  ): Promise<unknown> {
    await this.validateRequest(readModelRequest)
    return this.processSubscription(connectionID, readModelRequest, operation)
  }

  public async unsubscribe(connectionID: string, subscriptionID: string): Promise<void> {
    return this.config.provider.readModels.deleteSubscription(this.config, connectionID, subscriptionID)
  }

  public async unsubscribeAll(connectionID: string): Promise<void> {
    return this.config.provider.readModels.deleteAllSubscriptions(this.config, connectionID)
  }

  private async validateByIdRequest(readModelByIdRequest: ReadModelRequestEnvelope<ReadModelInterface>): Promise<void> {
    const logger = getLogger(this.config, 'BoosterReadModelsReader#validateByIdRequest')
    logger.debug('Validating the following read model by id request: ', readModelByIdRequest)
    if (!readModelByIdRequest.version) {
      throw new InvalidParameterError('The required request "version" was not present')
    }

    const readModelMetadata = this.config.readModels[readModelByIdRequest.class.name]
    if (!readModelMetadata) {
      throw new NotFoundError(`Could not find read model ${readModelByIdRequest.class.name}`)
    }

    await readModelMetadata.authorizer(readModelByIdRequest.currentUser, readModelByIdRequest)

    if (
      readModelByIdRequest?.key?.sequenceKey &&
      readModelByIdRequest.key.sequenceKey.name !== this.config.readModelSequenceKeys[readModelByIdRequest.class.name]
    ) {
      throw new InvalidParameterError(
        `Could not find a sort key defined for ${readModelByIdRequest.class.name} named '${readModelByIdRequest.key.sequenceKey.name}'.`
      )
    }
  }

  private async validateRequest(readModelRequest: ReadModelRequestEnvelope<ReadModelInterface>): Promise<void> {
    const logger = getLogger(this.config, 'BoosterReadModelsReader#validateRequest')
    logger.debug('Validating the following read model request: ', readModelRequest)
    if (!readModelRequest.version) {
      throw new InvalidParameterError('The required request "version" was not present')
    }

    const readModelMetadata = this.config.readModels[readModelRequest.class.name]
    if (!readModelMetadata) {
      throw new NotFoundError(`Could not find read model ${readModelRequest.class.name}`)
    }

    await readModelMetadata.authorizer(readModelRequest.currentUser, readModelRequest)
  }

  private async processSubscription(
    connectionID: string,
    readModelRequest: ReadModelRequestEnvelope<ReadModelInterface>,
    operation: GraphQLOperation
  ): Promise<void> {
    const logger = getLogger(this.config, 'BoosterReadModelsReader#processSubscription')
    logger.info(
      `Processing subscription of connection '${connectionID}' to read model '${readModelRequest.class.name}' with the following data: `,
      readModelRequest
    )
    const readModelMetadata = this.config.readModels[readModelRequest.class.name]

    const newReadModelRequest = await applyReadModelRequestBeforeFunctions(
      readModelRequest,
      readModelMetadata.before,
      readModelRequest.currentUser
    )

    const nowEpoch = Math.floor(new Date().getTime() / 1000)
    const subscription: SubscriptionEnvelope = {
      ...newReadModelRequest,
      expirationTime: nowEpoch + this.config.subscriptions.maxDurationInSeconds,
      connectionID,
      operation,
    }
    return this.config.provider.readModels.subscribe(this.config, subscription)
  }
}
