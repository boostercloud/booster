import {
  BoosterConfig,
  Logger,
  InvalidParameterError,
  NotAuthorizedError,
  NotFoundError,
  ReadModelRequestEnvelope,
  ReadModelInterface,
  SubscriptionEnvelope,
} from '@boostercloud/framework-types'
import { BoosterAuth } from './booster-auth'
import { Booster } from './booster'

// TODO: Think if this should be configurable
const subscriptionDurationSeconds = 24 * 60 * 60 // 24 hours

export class BoosterReadModelDispatcher {
  public constructor(readonly config: BoosterConfig, readonly logger: Logger) {}
  /**
   * @deprecated This the entry point used when requests come directly trough HTTP API, use GraphQl instead
   * @param rawMessage
   * @param config
   * @param logger
   */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  public async dispatch(rawMessage: any): Promise<any> {
    try {
      const readModelRequest = await this.config.provider.rawReadModelRequestToEnvelope(rawMessage)
      const result = await this.fetch(readModelRequest)
      return this.config.provider.handleReadModelResult(result)
    } catch (e) {
      return this.config.provider.handleReadModelError(e)
    }
  }

  public async fetch(readModelRequest: ReadModelRequestEnvelope): Promise<any> {
    this.validateRequest(readModelRequest)
    return this.processFetch(readModelRequest)
  }

  public async subscribe(connectionID: string, readModelRequest: ReadModelRequestEnvelope): Promise<any> {
    this.validateRequest(readModelRequest)
    return this.processSubscription(connectionID, readModelRequest)
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

  private processFetch(
    readModelRequest: ReadModelRequestEnvelope
  ): Promise<ReadModelInterface | Array<ReadModelInterface>> {
    if (readModelRequest.readModelID) {
      // Deprecated
      return this.config.provider.fetchReadModel(
        this.config,
        this.logger,
        readModelRequest.typeName,
        readModelRequest.readModelID
      )
    }

    const readModelMetadata = this.config.readModels[readModelRequest.typeName]
    const searcher = Booster.readModel(readModelMetadata.class)
    if (readModelRequest.filters) {
      for (const propName in readModelRequest.filters) {
        const filter = readModelRequest.filters[propName]
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        searcher.filter(propName as any, filter.operation as any, ...filter.values)
      }
    }
    return searcher.search()
  }

  private async processSubscription(connectionID: string, readModelRequest: ReadModelRequestEnvelope): Promise<void> {
    this.logger.info(
      `Processing subscription of connection '${connectionID}' to read model '${readModelRequest.typeName}' with the following data: `,
      readModelRequest
    )

    const nowEpoch = Math.floor(new Date().getTime() / 1000)
    const subscription: SubscriptionEnvelope = {
      ...readModelRequest,
      expirationTime: nowEpoch + subscriptionDurationSeconds,
      connectionID,
    }
    return this.config.provider.subscribeToReadModel(this.config, this.logger, subscription)
  }
}
