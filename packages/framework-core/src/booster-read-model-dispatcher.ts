import {
  BoosterConfig,
  Logger,
  InvalidParameterError,
  NotAuthorizedError,
  NotFoundError,
  ReadModelRequestEnvelope,
  ReadModelInterface,
} from '@boostercloud/framework-types'
import { BoosterAuth } from './booster-auth'

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
    this.validateFetchRequest(readModelRequest)
    return this.processFetch(readModelRequest)
  }

  private validateFetchRequest(readModelRequest: ReadModelRequestEnvelope): void {
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
      return this.config.provider.fetchReadModel(
        this.config,
        this.logger,
        readModelRequest.typeName,
        readModelRequest.readModelID
      )
    }
    return this.config.provider.fetchAllReadModels(this.config, this.logger, readModelRequest.typeName)
  }
}
