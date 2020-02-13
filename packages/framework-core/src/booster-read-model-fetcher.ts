import {
  BoosterConfig,
  Logger,
  InvalidParameterError,
  NotAuthorizedError,
  NotFoundError,
  ProviderReadModelsLibrary,
  ReadModelRequestEnvelope,
} from '@boostercloud/framework-types'
import { BoosterAuth } from './booster-auth'

export class BoosterReadModelFetcher {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  public static async fetch(rawMessage: any, config: BoosterConfig, logger: Logger): Promise<any> {
    const provider: ProviderReadModelsLibrary = config.provider
    try {
      const readModelRequest = await provider.rawReadModelRequestToEnvelope(rawMessage)
      this.validateFetchRequest(readModelRequest, config, logger)
      if (readModelRequest.readModelID) {
        return provider.fetchReadModel(config, logger, readModelRequest.typeName, readModelRequest.readModelID)
      }
      return provider.fetchAllReadModels(config, logger, readModelRequest.typeName)
    } catch (e) {
      return provider.handleReadModelError(e)
    }
  }

  private static validateFetchRequest(
    readModelRequest: ReadModelRequestEnvelope,
    config: BoosterConfig,
    logger: Logger
  ): void {
    logger.debug('Validating the following read model request: ', readModelRequest)
    if (!readModelRequest.version) {
      throw new InvalidParameterError('The required request "version" was not present')
    }

    const readModelMetadata = config.readModels[readModelRequest.typeName]
    if (!readModelMetadata) {
      throw new NotFoundError(`Could not find read model ${readModelRequest.typeName}`)
    }

    if (!BoosterAuth.isUserAuthorized(readModelMetadata.authorizedRoles, readModelRequest.currentUser)) {
      throw new NotAuthorizedError(`Access denied for read model ${readModelRequest.typeName}`)
    }
  }
}
